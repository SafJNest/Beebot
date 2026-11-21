package com.safjnest.spring.controller.beebot;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.bot.cache.managers.UserCache;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.discord.auth.DiscordApi.AuthUser;
import com.safjnest.discord.auth.DiscordApi.Server;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.discord.auth.DiscordAuthService;
import com.safjnest.sound.model.Sound;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.utils.ValidationUtils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/beebot")
public class MeController {

    private final DiscordAuthService auth;
    private final SoundCache sounds;
    private final UserCache users;
    private final BotDB database;

    public record Me(AuthUser user, List<Server> servers, Map<String, Sound> greets) {}
    public record GreetSettings(Map<String, GreetResult> greets) {}
    public record GreetResult(String status, List<GreetError> errors) {}
    public record GreetError(String field, String code, String message) {}

    @Autowired
    public MeController(DiscordAuthService auth) {
        this(auth, SoundCache.getInstance(), UserCache.getInstance(), BotDB.get());
    }

    public MeController(DiscordAuthService auth, SoundCache sounds, UserCache users, BotDB database) {
        this.auth = auth;
        this.sounds = sounds;
        this.users = users;
        this.database = database;
    }

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping("/me")
    public Me me(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth) {
        SoundCache.Context context = context(auth);
        Map<String, String> configured = users.with(context.userId(), user -> user.getAllGreets());

        return new Me(auth.user(), this.auth.servers(auth), sounds.greets(context, configured));
    }

    @PostMapping("/me/settings")
    public GreetSettings settings(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestBody(required = false) JsonNode body) {
        SoundCache.Context context = context(auth);
        if (body == null || !body.isObject() || body.size() != 1 || !body.path("greets").isObject())
            throw Exception.invalidRequest("greets object required");

        Set<String> requestedIds = new LinkedHashSet<>();
        for (JsonNode value : body.get("greets")) {
            if (value.isTextual() && ValidationUtils.isDecimalId(value.asText()))
                requestedIds.add(value.asText());
        }

        Map<String, QueryRecord> candidates = new HashMap<>();
        Exception preloadError = null;

        try {
            for (QueryRecord row : database.readSounds(requestedIds))
                candidates.put(row.get("id"), row);
        } catch (Exception exception) {
            preloadError = exception;
        }

        Map<String, GreetResult> results = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : body.get("greets").properties()) {
            String scope = entry.getKey();

            try {
                if (!scope.equals("0"))
                    sounds.requireGuild(context, scope);

                String id = null;
                if (!entry.getValue().isNull()) {
                    if (!entry.getValue().isTextual())
                        throw Exception.invalidRequest("Sound ID must be a string");

                    id = entry.getValue().asText();
                    if (!ValidationUtils.isDecimalId(id))
                        throw Exception.invalidRequest("Invalid ID");
                    if (preloadError != null)
                        throw preloadError;

                    QueryRecord candidate = candidates.get(id);
                    if (candidate == null || !BotDB.isSoundVisible(candidate, context.userId(), context.guilds().keySet()))
                        throw new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found");
                }

                String selected = id;
                users.with(context.userId(), user -> {
                    user.saveGreet(scope, selected);

                    return true;
                });

                results.put(scope, new GreetResult("ok", List.of()));
            } catch (Exception exception) {
                results.put(scope, new GreetResult("error", List.of(
                    new GreetError("soundId", exception.getCode(), exception.getMessage()))));
            } catch (IllegalStateException exception) {
                results.put(scope, new GreetResult("error", List.of(
                    new GreetError("soundId", "sound_storage_unavailable", "Sound storage is unavailable"))));
            }
        }

        return new GreetSettings(results);
    }

    /* -------------------------------------------------------------------------- */

    private SoundCache.Context context(AuthContext auth) {
        if (auth == null || auth.user() == null)
            throw new Exception(Type.UNAUTHENTICATED, "invalid_session", "User session required");

        return sounds.context(auth.user().id());
    }
}
