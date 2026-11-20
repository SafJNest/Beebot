package com.safjnest.spring.controller.beebot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.bot.audio.SoundboardThumbnails;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.sound.SoundService;
import com.safjnest.sound.model.SoundCatalog;
import com.safjnest.sound.model.SoundComment;
import com.safjnest.sound.model.SoundVote;
import com.safjnest.sound.model.SoundTarget;
import com.safjnest.sound.model.Soundboard;
import com.safjnest.utils.ValidationUtils;
import com.safjnest.spring.config.AuthFilter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/beebot")
public final class SoundCommunityController {

    private final SoundService sounds;

    @Autowired
    public SoundCommunityController() {
        this(SoundService.getInstance());
    }

    public SoundCommunityController(SoundService sounds) {
        this.sounds = sounds;
    }

    public record Image(String imageUrl) {}

    @GetMapping("/public/sounds/explore")
    public SoundCatalog.Explore explore(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth) {
        return sounds.explore(viewer(auth));
    }

    @GetMapping("/public/sounds")
    public SoundCatalog.Results search(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "tag", required = false) String tag,
            @RequestParam(name = "uploaderId", required = false) String uploaderId,
            @RequestParam(name = "minDurationMs", required = false) Long minDurationMs,
            @RequestParam(name = "maxDurationMs", required = false) Long maxDurationMs,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        SoundCatalog.Search filter = new SoundCatalog.Search(query, tag, uploaderId, minDurationMs,
            maxDurationMs, catalogSort(sort), limit, cursor);

        return sounds.search(filter, viewer(auth));
    }

    @GetMapping("/public/soundboards")
    public SoundCatalog.Page<Soundboard> searchSoundboards(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return sounds.searchSoundboards(query, viewer(auth), limit, cursor);
    }

    @GetMapping("/public/sounds/suggestions")
    public SoundCatalog.Suggestions suggestions(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @RequestParam("q") String query) {
        return sounds.suggestions(query, viewer(auth));
    }

    @GetMapping("/public/sounds/{soundId}")
    public SoundCatalog.Card sound(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("soundId") String soundId) {
        return sounds.get(soundId, viewer(auth));
    }

    @GetMapping({"/public/sounds/{soundId}/comments", "/public/soundboards/{boardId}/comments"})
    public SoundCatalog.Page<SoundComment> comments(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable Map<String, String> path,
            @RequestParam(name = "parentId", required = false) String parentId,
            @RequestParam(name = "sort", required = false) String sort,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "cursor", required = false) String cursor) {
        return sounds.comments(resource(path), viewer(auth), parentId, commentSort(sort), limit, cursor);
    }

    @GetMapping("/public/users/{userId}")
    public SoundCatalog.Creator creator(@PathVariable("userId") String userId) {
        return sounds.creator(userId);
    }

    @PostMapping(path = "/public/sounds/{soundId}/plays", consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordPlay(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("soundId") String soundId, @RequestBody JsonNode body) {
        requireFields(body, "playbackId", "soundboardId");
        String id = text(body, "playbackId");

        String boardId = body.has("soundboardId") ? text(body, "soundboardId") : null;
        sounds.recordWebPlay(soundId, viewer(auth), playbackId(id), boardId);
    }

    @PostMapping(path = {"/sounds/{soundId}/comments", "/soundboards/{boardId}/comments"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SoundComment.Change createComment(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable Map<String, String> path, @RequestBody JsonNode body) {
        requireFields(body, "text", "parentId");
        String parent = body.has("parentId") ? text(body, "parentId") : null;

        return sounds.addComment(resource(path), user(auth), parent, text(body, "text"));
    }

    @PatchMapping(path = {"/sounds/{soundId}/comments/{commentId}", "/soundboards/{boardId}/comments/{commentId}"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public SoundComment.Change editComment(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable Map<String, String> path, @PathVariable("commentId") String commentId,
            @RequestBody JsonNode body) {
        requireFields(body, "text", "pinned", "hearted");
        SoundComment.Edit edit = new SoundComment.Edit(body.has("text") ? text(body, "text") : null,
            flag(body, "pinned"), flag(body, "hearted"));

        return sounds.editComment(resource(path), commentId, user(auth), edit);
    }

    @DeleteMapping({"/sounds/{soundId}/comments/{commentId}", "/soundboards/{boardId}/comments/{commentId}"})
    public SoundComment.Removal deleteComment(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable Map<String, String> path, @PathVariable("commentId") String commentId) {
        return sounds.deleteComment(resource(path), commentId, user(auth));
    }

    @PutMapping(path = {"/sounds/{soundId}/vote", "/soundboards/{boardId}/vote"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public SoundVote.Result voteSound(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable Map<String, String> path, @RequestBody JsonNode body) {
        return vote(path, null, user(auth), vote(body));
    }

    @PutMapping(path = {"/sounds/{soundId}/comments/{commentId}/vote", "/soundboards/{boardId}/comments/{commentId}/vote"}, consumes = MediaType.APPLICATION_JSON_VALUE)
    public SoundVote.Result voteComment(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable Map<String, String> path, @PathVariable("commentId") String commentId,
            @RequestBody JsonNode body) {
        return vote(path, commentId, user(auth), vote(body));
    }

    @PutMapping(path = "/sounds/{soundId}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Image putImage(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("soundId") String soundId, @RequestPart("file") MultipartFile file) throws IOException {
        if (file.getSize() > SoundboardThumbnails.MAX_ATTACHMENT_BYTES)
            throw new MaxUploadSizeExceededException(SoundboardThumbnails.MAX_ATTACHMENT_BYTES);

        try (InputStream input = file.getInputStream()) {
            return new Image(sounds.putImage(soundId, user(auth), input));
        } catch (IllegalArgumentException failure) {
            throw Exception.invalidRequest(failure.getMessage());
        }
    }

    @DeleteMapping("/sounds/{soundId}/image")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext auth,
            @PathVariable("soundId") String soundId) {
        sounds.deleteImage(soundId, user(auth));
    }

    /* -------------------------------------------------------------------------- */

    private SoundTarget resource(Map<String, String> path) {
        String board = path.get("boardId");
        String targetId = board == null ? path.get("soundId") : board;
        if (!ValidationUtils.isDecimalId(targetId) || board != null && targetId.startsWith("0"))
            throw Exception.invalidRequest("Invalid ID");

        try {
            String id = Long.toString(Long.parseLong(targetId));
            return board == null ? SoundTarget.sound(id) : SoundTarget.soundboard(id);
        } catch (NumberFormatException failure) {
            throw Exception.invalidRequest("Invalid ID");
        }
    }

    private SoundVote.Result vote(Map<String, String> path, String commentId, String viewer, SoundVote vote) {
        SoundTarget target = resource(path);
        if (target.soundboard())
            return sounds.voteSoundboard(target.id(), commentId, viewer, vote);

        return sounds.vote(target.id(), commentId, viewer, vote);
    }

    private String viewer(AuthContext auth) {
        return auth.user() == null ? null : auth.user().id();
    }

    private String user(AuthContext auth) {
        String id = viewer(auth);
        if (id == null)
            throw new Exception(Type.UNAUTHENTICATED, "invalid_session", "User session required");

        return id;
    }

    private SoundCatalog.Sort catalogSort(String value) {
        return switch (value) {
            case null -> null;
            case "relevance" -> SoundCatalog.Sort.RELEVANCE;
            case "popular" -> SoundCatalog.Sort.POPULAR;
            case "newest" -> SoundCatalog.Sort.NEWEST;
            case "mostLiked" -> SoundCatalog.Sort.MOST_LIKED;
            case "mostDisliked" -> SoundCatalog.Sort.MOST_DISLIKED;
            default -> throw Exception.invalidRequest("Invalid catalog sort");
        };
    }

    private SoundComment.Sort commentSort(String value) {
        return switch (value) {
            case null -> null;
            case "top" -> SoundComment.Sort.TOP;
            case "newest" -> SoundComment.Sort.NEWEST;
            case "oldest" -> SoundComment.Sort.OLDEST;
            case "mostDisliked" -> SoundComment.Sort.MOST_DISLIKED;
            default -> throw Exception.invalidRequest("Invalid comment sort");
        };
    }

    private void requireFields(JsonNode body, String... names) {
        if (body == null || !body.isObject() || body.isEmpty())
            throw Exception.invalidRequest("Nonempty JSON object required");

        Set<String> allowed = Set.of(names);
        for (Map.Entry<String, JsonNode> field : body.properties()) {
            if (!allowed.contains(field.getKey()))
                throw Exception.invalidRequest("Unknown field '" + field.getKey() + "'");
        }
    }

    private String text(JsonNode body, String field) {
        JsonNode value = body.path(field);
        if (!value.isTextual())
            throw Exception.invalidRequest("'" + field + "' must be a string");

        return value.textValue();
    }

    private Boolean flag(JsonNode body, String field) {
        JsonNode value = body.get(field);
        if (value == null)
            return null;
        if (!value.isBoolean())
            throw Exception.invalidRequest("'" + field + "' must be a boolean");

        return value.booleanValue();
    }

    private SoundVote vote(JsonNode body) {
        requireFields(body, "vote");
        if (body.path("vote").isNull())
            return null;

        return switch (text(body, "vote")) {
            case "LIKE" -> SoundVote.LIKE;
            case "DISLIKE" -> SoundVote.DISLIKE;
            default -> throw Exception.invalidRequest("'vote' must be LIKE, DISLIKE or null");
        };
    }

    private UUID playbackId(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (id.toString().equalsIgnoreCase(value))
                return id;
        } catch (IllegalArgumentException ignored) {
        }

        throw Exception.invalidRequest("'playbackId' must be a UUID");
    }
}
