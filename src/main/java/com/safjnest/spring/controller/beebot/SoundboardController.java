package com.safjnest.spring.controller.beebot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.bot.audio.SoundboardThumbnails;
import com.safjnest.bot.audio.SoundboardThumbnails.Image;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.bot.cache.managers.SoundCache.Context;
import com.safjnest.database.R2;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.BotDB.SoundboardAccess;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.sound.model.Sound;
import com.safjnest.spring.config.AuthFilter;
import com.safjnest.utils.ValidationUtils;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/beebot/guilds/{guildId}/soundboards")
public class SoundboardController {

    private final SoundCache sounds;
    private final BotDB database;

    @Autowired
    public SoundboardController() {
        this(SoundCache.getInstance(), BotDB.get());
    }

    public SoundboardController(SoundCache sounds, BotDB database) {
        this.sounds = sounds;
        this.database = database;
    }

    public record Soundboard(String id, String guildId, String userId, String name, String thumbnailUrl,
            int soundCount, boolean canEdit) {}

    public record Detail(Soundboard board, List<Sound> sounds) {}

    public record Thumbnail(String thumbnailUrl) {}

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping
    public List<Soundboard> list(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable("guildId") String guildId) {
        SoundboardAccess access = access(context(auth, guildId), guildId);
        List<Soundboard> boards = new ArrayList<>();

        for (QueryRecord row : database.readSoundboards(guildId))
            boards.add(board(access, row));

        return boards;
    }

    @GetMapping("/{id}")
    public Detail get(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable("guildId") String guildId,
            @PathVariable("id") String id) {
        return detail(context(auth, guildId), guildId, id(id));
    }

    @PostMapping
    public ResponseEntity<Detail> create(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("guildId") String guildId, @RequestBody JsonNode body) {
        Context context = context(auth, guildId);
        if (!body.isObject() || body.size() != 2 || !body.path("soundIds").isArray()
                || body.path("soundIds").size() > BotDB.MAX_SOUNDBOARD_SOUNDS)
            throw Exception.invalidRequest("Expected name and soundIds with up to " + BotDB.MAX_SOUNDBOARD_SOUNDS + " sounds");

        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode value : body.path("soundIds")) {
            if (!value.isTextual())
                throw Exception.invalidRequest("Sound IDs must be strings");

            ids.add(id(value.asText()));
        }

        String boardId = database.createSoundboard(name(body), access(context, guildId), List.copyOf(ids));
        URI location = URI.create("/api/beebot/guilds/" + guildId + "/soundboards/" + boardId);

        return ResponseEntity.created(location).body(detail(context, guildId, boardId));
    }

    @PatchMapping("/{id}")
    public Detail rename(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable("guildId") String guildId,
            @PathVariable("id") String id, @RequestBody JsonNode body) {
        Context context = context(auth, guildId);
        if (!body.isObject() || body.size() != 1)
            throw Exception.invalidRequest("Expected name");

        database.renameSoundboard(id(id), access(context, guildId), name(body));

        return detail(context, guildId, id);
    }

    @PutMapping("/{id}/sounds/{soundId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable("guildId") String guildId,
            @PathVariable("id") String id, @PathVariable("soundId") String soundId) {
        database.addSoundToBoard(id(id), access(context(auth, guildId), guildId), id(soundId));
    }

    @DeleteMapping("/{id}/sounds/{soundId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable("guildId") String guildId,
            @PathVariable("id") String id, @PathVariable("soundId") String soundId) {
        database.removeSoundFromBoard(id(id), access(context(auth, guildId), guildId), id(soundId));
    }

    @PutMapping(path = "/{id}/thumbnail", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Thumbnail thumbnail(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable("guildId") String guildId,
            @PathVariable("id") String id, @RequestPart("file") MultipartFile file) throws IOException {
        SoundboardAccess access = access(context(auth, guildId), guildId);
        Image image;
        try (InputStream input = file.getInputStream()) {
            image = SoundboardThumbnails.read(input);
        } catch (IllegalArgumentException failure) {
            throw Exception.invalidRequest(failure.getMessage());
        }

        String key = database.replaceSoundboardThumbnail(id(id), access, boardId -> uploadThumbnail(boardId, image));

        return new Thumbnail(R2.getInstance().url(key));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable("guildId") String guildId,
            @PathVariable("id") String id) {
        database.removeSoundboard(id(id), access(context(auth, guildId), guildId));
    }

    /* -------------------------------------------------------------------------- */

    private Context context(AuthContext auth, String guildId) {
        if (auth == null || auth.user() == null)
            throw new Exception(Type.UNAUTHENTICATED, "invalid_session", "User session required");

        Context context = sounds.context(auth.user().id());
        sounds.requireGuild(context, guildId);

        return context;
    }

    private SoundboardAccess access(Context context, String guildId) {
        return new SoundboardAccess(guildId, context.userId(), context.guilds().get(guildId).administrator());
    }

    private Detail detail(Context context, String guildId, String id) {
        return database.withSoundReadSession(() -> {
            Soundboard board = board(access(context, guildId), database.readSoundboard(guildId, id));

            return new Detail(board, sounds.soundboardSounds(context, id));
        });
    }

    private Soundboard board(SoundboardAccess access, QueryRecord row) {
        String userId = row.get("user_id");
        String key = row.get("thumbnail_key");
        String thumbnail = key == null || key.isBlank() ? null : R2.getInstance().url(key);
        boolean canEdit = access.administrator() || access.userId().equals(userId);

        return new Soundboard(row.get("id"), access.guildId(), userId, row.get("name"), thumbnail,
            row.getAsInt("sound_count"), canEdit);
    }

    private String name(JsonNode body) {
        JsonNode value = body.path("name");
        if (!value.isTextual())
            throw Exception.invalidRequest("Soundboard name required");

        String name = value.asText().strip();
        if (name.isEmpty() || name.length() > 255 || name.matches("[0-9]+"))
            throw Exception.invalidRequest("Invalid soundboard name");

        return name;
    }

    private String id(String id) {
        if (!ValidationUtils.isDecimalId(id) || id.startsWith("0"))
            throw Exception.invalidRequest("Invalid ID");

        return id;
    }

    private String uploadThumbnail(String id, Image image) {
        try {
            return SoundboardThumbnails.configured().upload(id, image);
        } catch (RuntimeException failure) {
            throw new Exception(Type.UNAVAILABLE, "sound_storage_unavailable", "Soundboard storage is unavailable", failure, null);
        }
    }
}
