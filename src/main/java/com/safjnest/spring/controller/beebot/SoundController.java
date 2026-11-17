package com.safjnest.spring.controller.beebot;

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.sound.SoundValues;
import com.safjnest.sound.model.Tag;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.sound.SoundPeriod;
import com.safjnest.sound.model.Sound;
import com.safjnest.spring.config.AuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beebot")
public class SoundController {

    private final SoundCache sounds;

    @Autowired
    public SoundController(Environment environment) {
        this(SoundCache.getInstance());
        sounds.setCdnUrl(environment.getProperty("CDN_URL", ""));
    }

    public SoundController(SoundCache sounds) {
        this.sounds = sounds;
    }

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
    }

    @GetMapping("/guilds/{guildId}/sounds")
    public Map<String,Object> guildSounds(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("guildId") String guildId,@RequestParam(name="period", defaultValue="30d") String period,@RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone) {
        SoundCache.Context context = context(auth);
        return sounds.guildSounds(context,guildId,period(context,period,timezone,null,null,null));
    }

    @GetMapping("/sounds/me")
    public Map<String,Object> mine(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam(name="period", defaultValue="30d") String period,@RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone) {
        SoundCache.Context context = context(auth);
        return sounds.mySounds(context,period(context,period,timezone,null,null,null));
    }

    @GetMapping("/sounds/{soundId}")
    public Sound sound(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId) {
        return sounds.sound(context(auth),soundId);
    }

    @GetMapping("/sounds")
    public Map<String,Object> search(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam(name="q", required=false) String q,@RequestParam(name="tag", required=false) String tag,
            @RequestParam(name="sort", defaultValue="plays") String sort,@RequestParam(name="order", defaultValue="desc") String order,@RequestParam(name="limit", defaultValue="100") int limit) {
        return sounds.search(context(auth),q,tag,sort,order,limit);
    }

    @GetMapping("/tags")
    public Map<String,Object> tags(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,@RequestParam(name="q", required=false) String q) {
        return sounds.searchTags(context(auth),q);
    }

    @GetMapping("/guilds/{guildId}/sounds/analytics")
    public Map<String,Object> guildAnalytics(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("guildId") String guildId,
            @RequestParam(name="period", defaultValue="30d") String period,@RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone) {
        SoundCache.Context context = context(auth);
        return sounds.guildAnalytics(context,guildId,period(context,period,timezone,null,null,null));
    }

    @GetMapping("/sounds/me/analytics")
    public Map<String,Object> myAnalytics(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam(name="period", defaultValue="30d") String period,@RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone) {
        SoundCache.Context context = context(auth);
        return sounds.myAnalytics(context,period(context,period,timezone,null,null,null));
    }

    @GetMapping("/sounds/{soundId}/analytics")
    public Map<String,Object> analytics(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId,
            @RequestParam(name="guildId", required=false) String guildId,@RequestParam(name="period", defaultValue="30d") String period,
            @RequestParam(name="timezone", defaultValue="Europe/Rome") String timezone,@RequestParam(name="cursor", required=false) String cursor) {
        SoundCache.Context context = context(auth);
        return sounds.soundAnalytics(context,soundId,guildId,period(context,period,timezone,cursor,soundId,guildId));
    }

    @PatchMapping("/sounds/{soundId}")
    public Sound patch(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId,@RequestBody(required=false) JsonNode body) {
        SoundCache.Context context = context(auth);

        sounds.withVisibleSound(context, soundId, sound -> {
            if (!context.userId().equals(sound.getUserId()))
                throw new Exception(Type.FORBIDDEN, "sound_forbidden", "Only the uploader can edit");

            Sound edit = sound.deferEdit();
            parseSound(edit, body);

            if (!edit.update(context.userId()))
                throw new Exception(Type.UNAVAILABLE, "sound_storage_unavailable", "Sound storage is unavailable");

            return true;
        });

        return sounds.sound(context, soundId);
    }

    @DeleteMapping("/sounds/{soundId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId) {
        SoundCache.Context context = context(auth);
        Set<String> administrators = new LinkedHashSet<>();

        context.guilds().forEach((id, guild) -> {
            if (guild.administrator())
                administrators.add(id);
        });

        String extension = sounds.withVisibleSound(context, soundId, sound -> {
            if (!sound.delete(context.userId(), administrators))
                throw new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found");

            return sound.getExtension();
        });

        sounds.removeDeleted(soundId);

        if (extension != null && extension.matches("[a-zA-Z0-9]+")) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("rsc", "sounds", soundId + "." + extension));
            } catch (java.io.IOException exception) {
                org.slf4j.LoggerFactory.getLogger(SoundController.class).warn("Unable to remove deleted sound file {}", soundId, exception);
            }
        }
    }

    @PatchMapping("/sounds/{soundId}/like")
    public Sound.Vote like(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId,@RequestBody(required=false) JsonNode body) {
        return vote(context(auth), soundId, 1, body);
    }

    @PatchMapping("/sounds/{soundId}/dislike")
    public Sound.Vote dislike(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,@PathVariable("soundId") String soundId,@RequestBody(required=false) JsonNode body) {
        return vote(context(auth), soundId, -1, body);
    }

    // ================================================================================================================

    private Sound.Vote vote(SoundCache.Context context, String id, int value, JsonNode body) {
        return sounds.withVisibleSound(context, id, sound -> {
            if (body == null || !body.isObject() || body.size() != 1 || !body.path("enabled").isBoolean())
                throw SoundValues.bad("enabled boolean required");

            return sound.vote(context.userId(), context.guilds().keySet(), value, body.get("enabled").asBoolean());
        });
    }

    private void parseSound(Sound edit, JsonNode body) {
        if (body == null || !body.isObject() || body.isEmpty())
            throw SoundValues.bad("Nonempty JSON object required");

        for (Map.Entry<String, JsonNode> entry : body.properties()) {
            JsonNode value = entry.getValue();

            switch (entry.getKey()) {
                case "name" -> {
                    if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > 100 || value.asText().matches("[0-9]+"))
                        throw SoundValues.bad("Invalid sound name");

                    edit.setName(value.asText().trim());
                }
                case "public" -> {
                    if (!value.isBoolean())
                        throw SoundValues.bad("public must be boolean");

                    edit.setPublic(value.asBoolean());
                }
                case "tags" -> {
                    if (!value.isArray() || value.size() > Tag.MAX_TAG_SOUND)
                        throw SoundValues.bad("At most five tags required");

                    Set<String> names = new LinkedHashSet<>();
                    for (JsonNode tag : value) {
                        if (!tag.isTextual() || tag.asText().isBlank() || tag.asText().length() > 255)
                            throw SoundValues.bad("Invalid tag");

                        names.add(tag.asText().trim().toLowerCase(java.util.Locale.ROOT));
                    }

                    edit.setTagNames(new ArrayList<>(names));
                }
                default -> throw SoundValues.bad("Unknown sound field");
            }
        }
    }

    private SoundCache.Context context(AuthContext auth) {
        if (auth == null || auth.user() == null)
            throw new Exception(Type.UNAUTHENTICATED, "invalid_session", "User session required");

        return sounds.context(auth.user().id());
    }

    private SoundPeriod period(SoundCache.Context context,String period,String zone,String cursor,String sound,String guild) {
        return SoundPeriod.of(period,zone,cursor,sound,context.userId(),guild);
    }
}
