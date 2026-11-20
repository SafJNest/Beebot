package com.safjnest.spring.controller.beebot;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.annotation.JsonInclude;
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
import com.safjnest.sound.SoundService;
import com.safjnest.sound.model.Soundboard;
import com.safjnest.sound.model.Soundboard.Edit;
import com.safjnest.sound.model.Soundboard.SortMode;
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
@RequestMapping("/api/beebot")
public class SoundboardController {

    private static final String GUILD = "/guilds/{guildId}/soundboards";
    private static final String PERSONAL = "/me/soundboards";
    private static final String MANAGE = "/soundboards/{id}";

    private final SoundCache sounds;
    private final BotDB database;
    private final SoundService community;

    @Autowired
    public SoundboardController() {
        this(SoundCache.getInstance(), BotDB.get(), SoundService.getInstance());
    }

    public SoundboardController(SoundCache sounds, BotDB database, SoundService community) {
        this.sounds = sounds;
        this.database = database;
        this.community = community;
    }

    public record Detail(Soundboard board, List<Sound> sounds,
            @JsonInclude(JsonInclude.Include.NON_NULL) List<String> soundIds) {}

    public record Thumbnail(String thumbnailUrl) {}

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
    }

    @GetMapping({GUILD, PERSONAL})
    public List<Soundboard> list(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable(name = "guildId", required = false) String guildId) {
        String actor = user(auth);
        List<QueryRecord> rows;
        boolean administrator = false;
        if (guildId == null) {
            rows = database.readPersonalSoundboards(actor);
        } else {
            Context context = context(auth, guildId);
            administrator = context.guilds().get(guildId).administrator();
            rows = database.readSoundboards(guildId);
        }

        List<Soundboard> boards = new ArrayList<>();
        for (QueryRecord row : rows)
            boards.add(Soundboard.fromRow(row, administrator || actor.equals(row.get("user_id")), true, false));

        return boards;
    }

    @GetMapping(GUILD + "/{id}")
    public Detail get(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("guildId") String guildId, @PathVariable("id") String id) {
        return database.withSoundReadSession(() -> detail(context(auth, guildId), database.readSoundboard(guildId, id(id))));
    }

    @GetMapping("/public/soundboards/{id}")
    public Soundboard.PublicDetail publicDetail(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("id") String id) {
        String viewer = auth == null || auth.user() == null ? null : auth.user().id();

        return community.publicSoundboard(id(id), viewer);
    }

    @GetMapping(MANAGE)
    public Detail managedDetail(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable("id") String id) {
        return managedDetail(auth, id(id), null);
    }

    @PostMapping({GUILD, PERSONAL})
    public ResponseEntity<Detail> create(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable(name = "guildId", required = false) String guildId, @RequestBody JsonNode body) {
        Context context = guildId == null ? new Context(user(auth), Map.of()) : context(auth, guildId);
        Edit edit = edit(body, true, guildId != null && !body.has("sortMode") && !body.has("public"));
        String boardId = database.createSoundboard(edit, access(context, guildId));
        URI location = URI.create(guildId == null ? "/api/beebot/soundboards/" + boardId
            : "/api/beebot/guilds/" + guildId + "/soundboards/" + boardId);

        Detail result = database.withSoundReadSession(() -> detail(context, database.readSoundboard(guildId, boardId)));

        return ResponseEntity.created(location).body(result);
    }

    @PatchMapping({GUILD + "/{id}", MANAGE})
    public Detail patch(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable(name = "guildId", required = false) String guildId, @PathVariable("id") String id,
            @RequestBody JsonNode body) {
        String boardId = id(id);
        database.editSoundboard(boardId, editAccess(auth, guildId, boardId), edit(body, false, false));

        return managedDetail(auth, boardId, guildId);
    }

    @PutMapping({GUILD + "/{id}/sounds/{soundId}", MANAGE + "/sounds/{soundId}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable(name = "guildId", required = false) String guildId,
            @PathVariable("id") String id, @PathVariable("soundId") String soundId) {
        database.addSoundToBoard(id(id), editAccess(auth, guildId, id(id)), id(soundId));
    }

    @DeleteMapping({GUILD + "/{id}/sounds/{soundId}", MANAGE + "/sounds/{soundId}"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable(name = "guildId", required = false) String guildId,
            @PathVariable("id") String id, @PathVariable("soundId") String soundId) {
        database.removeSoundFromBoard(id(id), editAccess(auth, guildId, id(id)), id(soundId));
    }

    @PutMapping(path = {GUILD + "/{id}/thumbnail", MANAGE + "/thumbnail"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Thumbnail thumbnail(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable(name = "guildId", required = false) String guildId,
            @PathVariable("id") String id, @RequestPart("file") MultipartFile file) throws IOException {
        SoundboardAccess access = editAccess(auth, guildId, id(id));
        Image image;
        try (InputStream input = file.getInputStream()) {
            image = SoundboardThumbnails.read(input);
        } catch (IllegalArgumentException failure) {
            throw Exception.invalidRequest(failure.getMessage());
        }

        String key = database.replaceSoundboardThumbnail(id(id), access, boardId -> uploadThumbnail(boardId, image));

        return new Thumbnail(R2.getInstance().url(key));
    }

    @DeleteMapping({GUILD + "/{id}", MANAGE})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth, @PathVariable(name = "guildId", required = false) String guildId,
            @PathVariable("id") String id) {
        database.removeSoundboard(id(id), editAccess(auth, guildId, id(id)));
    }

    /* -------------------------------------------------------------------------- */

    private String user(AuthContext auth) {
        if (auth == null || auth.user() == null)
            throw new Exception(Type.UNAUTHENTICATED, "invalid_session", "User session required");

        return auth.user().id();
    }

    private Context context(AuthContext auth, String guildId) {
        Context context = sounds.context(user(auth));
        sounds.requireGuild(context, guildId);

        return context;
    }

    private SoundboardAccess access(Context context, String guildId) {
        boolean administrator = guildId != null && context.guilds().get(guildId).administrator();

        return new SoundboardAccess(guildId, context.userId(), administrator);
    }

    private SoundboardAccess editAccess(AuthContext auth, String scopedGuild, String id) {
        if (scopedGuild != null)
            return access(context(auth, scopedGuild), scopedGuild);

        String actor = user(auth);
        QueryRecord row = database.readSoundboard(id, null, false);
        String guildId = row.get("guild_id");
        if (actor.equals(row.get("user_id")))
            return new SoundboardAccess(guildId, actor, false);
        if (guildId == null)
            throw new Exception(Type.NOT_FOUND, "soundboard_not_found", "Soundboard not found");

        Context context = sounds.context(actor);
        if (row.getAsBoolean("public"))
            sounds.requireGuild(context, guildId);
        else
            BotDB.requireSoundboardVisibility(row, actor, context.guilds().keySet());

        return access(context, guildId);
    }

    private Detail managedDetail(AuthContext auth, String id, String guildId) {
        return database.withSoundReadSession(() -> {
            SoundboardAccess access = editAccess(auth, guildId, id);
            QueryRecord row = database.readSoundboard(access.guildId(), id);
            if (!access.administrator() && !access.userId().equals(row.get("user_id")))
                throw new Exception(Type.FORBIDDEN, "soundboard_forbidden", "Only the uploader or a guild administrator may edit");

            Context context = access.guildId() == null ? new Context(access.userId(), Map.of()) : sounds.context(access.userId());

            return detail(context, row);
        });
    }

    private Detail detail(Context context, QueryRecord row) {
        String guildId = row.get("guild_id");
        boolean administrator = guildId != null && context.guilds().containsKey(guildId)
            && context.guilds().get(guildId).administrator();
        boolean canEdit = administrator || row.get("user_id").equals(context.userId());
        Soundboard board = Soundboard.fromRow(row, canEdit, true, false);

        List<String> soundIds = canEdit ? database.readSoundboardSoundIds(board.id()) : null;

        return new Detail(board, sounds.soundboardSounds(context, row), soundIds);
    }

    private Edit edit(JsonNode body, boolean create, boolean legacyCreate) {
        if (!body.isObject() || body.isEmpty())
            throw Exception.invalidRequest("Nonempty JSON object required");
        for (Map.Entry<String, JsonNode> field : body.properties()) {
            if (!Set.of("name", "soundIds", "public", "sortMode").contains(field.getKey()))
                throw Exception.invalidRequest("Unknown soundboard field");
        }

        String name = create || body.has("name") ? name(body) : null;
        JsonNode visibility = body.get("public");
        if (visibility != null && !visibility.isBoolean())
            throw Exception.invalidRequest("public must be boolean");

        SortMode sort = null;
        if (body.has("sortMode")) {
            try {
                sort = SortMode.valueOf(body.path("sortMode").asText());
            } catch (IllegalArgumentException failure) {
                throw Exception.invalidRequest("Invalid sortMode");
            }
        }

        List<String> ids = null;
        if (create || body.has("soundIds")) {
            JsonNode list = body.path("soundIds");
            if (!list.isArray() || list.size() > BotDB.MAX_SOUNDBOARD_SOUNDS)
                throw Exception.invalidRequest("Expected soundIds with up to " + BotDB.MAX_SOUNDBOARD_SOUNDS + " sounds");

            Set<String> unique = new LinkedHashSet<>();
            for (JsonNode value : list) {
                if (!value.isTextual())
                    throw Exception.invalidRequest("Sound IDs must be strings");
                if (!unique.add(id(value.asText())) && !legacyCreate)
                    throw Exception.invalidRequest("Sound IDs must be distinct");
            }
            ids = List.copyOf(unique);
        }

        return new Edit(name, visibility == null ? null : visibility.booleanValue(), sort, ids);
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
