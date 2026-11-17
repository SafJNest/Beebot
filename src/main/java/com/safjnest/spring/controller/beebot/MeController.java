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
import com.safjnest.discord.auth.AuthException;
import com.safjnest.discord.auth.DiscordApi.AuthUser;
import com.safjnest.discord.auth.DiscordApi.Server;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.discord.guild.DiscordGuildService;
import com.safjnest.sound.SoundValues;
import com.safjnest.sound.model.Sound;
import com.safjnest.spring.config.AuthFilter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/beebot")
public class MeController {

    private final DiscordGuildService guilds;
    private final SoundCache sounds;
    private final UserCache users;
    private final BotDB database;

    public record Me(AuthUser user, List<Server> servers, Map<String, Sound> greets) {}

    @Autowired
    public MeController(Environment environment) {
        this(new DiscordGuildService(environment.getProperty("AUTH_ENCRYPTION_KEY")),
            SoundCache.getInstance(), UserCache.getInstance(), BotDB.get());
    }

    public MeController(DiscordGuildService guilds, SoundCache sounds, UserCache users, BotDB database) {
        this.guilds = guilds;
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

        return new Me(auth.user(), guilds.servers(auth), sounds.greets(context, configured));
    }

    @PostMapping("/me/settings")
    public Map<String, Object> settings(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestBody(required = false) JsonNode body) {
        SoundCache.Context context = context(auth);
        if (body == null || !body.isObject() || body.size() != 1 || !body.path("greets").isObject())
            throw SoundValues.bad("greets object required");

        Set<String> requestedIds = new LinkedHashSet<>();
        for (JsonNode value : body.get("greets")) {
            if (value.isTextual() && value.asText().matches("[0-9]{1,19}"))
                requestedIds.add(value.asText());
        }

        Map<String, QueryRecord> candidates = new HashMap<>();
        AuthException preloadError = null;

        try {
            for (QueryRecord row : database.readSounds(requestedIds))
                candidates.put(row.get("id"), row);
        } catch (AuthException exception) {
            preloadError = exception;
        }

        Map<String, Object> results = new LinkedHashMap<>();
        for (Map.Entry<String, JsonNode> entry : body.get("greets").properties()) {
            String scope = entry.getKey();

            try {
                if (!scope.equals("0"))
                    sounds.requireGuild(context, scope);

                String id = null;
                if (!entry.getValue().isNull()) {
                    if (!entry.getValue().isTextual())
                        throw SoundValues.bad("Sound ID must be a string");

                    id = SoundValues.id(entry.getValue().asText());
                    if (preloadError != null)
                        throw preloadError;

                    QueryRecord candidate = candidates.get(id);
                    if (candidate == null || !BotDB.isSoundVisible(candidate, context.userId(), context.guilds().keySet()))
                        throw new AuthException(HttpStatus.NOT_FOUND, "sound_not_found", "Sound not found");
                }

                String selected = id;
                users.with(context.userId(), user -> {
                    user.saveGreet(scope, selected);

                    return true;
                });

                results.put(scope, SoundValues.map("status", "ok", "errors", List.of()));
            } catch (AuthException exception) {
                results.put(scope, SoundValues.map("status", "error", "errors", List.of(
                    SoundValues.map("field", "soundId", "code", exception.code(), "message", exception.getReason()))));
            } catch (IllegalStateException exception) {
                results.put(scope, SoundValues.map("status", "error", "errors", List.of(
                    SoundValues.map("field", "soundId", "code", "sound_storage_unavailable", "message", "Sound storage is unavailable"))));
            }
        }

        return SoundValues.map("greets", results);
    }

    /* -------------------------------------------------------------------------- */

    private SoundCache.Context context(AuthContext auth) {
        if (auth == null || auth.user() == null)
            throw new AuthException(HttpStatus.UNAUTHORIZED, "invalid_session", "User session required");

        return sounds.context(auth.user().id());
    }
}
