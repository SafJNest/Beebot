package com.safjnest.sound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import com.safjnest.bot.audio.SoundFiles;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.database.sql.database.SoundDB;
import com.safjnest.database.sql.database.SoundDB.CommentChange;
import com.safjnest.database.sql.database.SoundDB.Position;
import com.safjnest.discord.auth.DiscordApi;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.SoundCatalog;
import com.safjnest.sound.model.SoundComment;
import com.safjnest.sound.model.SoundVote;
import com.safjnest.sound.model.Tag;
import com.safjnest.utils.ValidationUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SoundService {

    private static final Logger LOG = LoggerFactory.getLogger(SoundService.class);
    private static final int PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_COMMENT_LENGTH = 2000;
    private static final int EXPLORE_SIZE = 10;
    private static final int MINE_SIZE = 5;
    private static final int SUGGESTION_TAGS = 3;
    private static final Base64.Encoder CURSOR_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder CURSOR_DECODER = Base64.getUrlDecoder();

    private static final SoundService instance = new SoundService(BotDB.get(), SoundCache.getInstance(), new DiscordApi(), Clock.systemUTC());

    private final BotDB database;
    private final SoundDB sounds;
    private final SoundCache cache;
    private final DiscordApi discord;
    private final Clock clock;

    public SoundService(BotDB database, SoundCache cache, DiscordApi discord, Clock clock) {
        this.database = database;
        this.sounds = database.communitySounds();
        this.cache = cache;
        this.discord = discord;
        this.clock = clock;
    }

    public static SoundService getInstance() {
        return instance;
    }

    public SoundCatalog.Card get(String soundId, String viewer) {
        String id = id(soundId);

        return sounds.read(() -> cards(List.of(sounds.visibleSound(id, viewer)), viewer).getFirst());
    }

    public SoundCatalog.Results search(SoundCatalog.Search request, String viewer) {
        SoundCatalog.Search filter = normalize(request);
        String scope = scope("catalog", filter.q(), filter.tag(), filter.uploaderId(), filter.minDurationMs(),
            filter.maxDurationMs(), filter.sort());
        Cursor after = decode(filter.cursor(), scope);

        return sounds.read(() -> {
            Position position = after == null ? null : after.position();
            List<QueryRecord> rows = sounds.search(filter, null, position, filter.limit() + 1);
            List<QueryRecord> items = rows.subList(0, Math.min(rows.size(), filter.limit()));
            List<QueryRecord> mine = viewer == null || after != null ? List.of() : sounds.search(filter, viewer, null, MINE_SIZE);

            Map<String, SoundCatalog.Card> cards = cardsById(items, mine, viewer);
            String next = rows.size() > filter.limit() ? encode(scope, catalogPosition(items.getLast()), null) : null;

            return new SoundCatalog.Results(orderedCards(items, cards), orderedCards(mine, cards), next);
        });
    }

    public SoundCatalog.Explore explore(String viewer) {
        SoundCatalog.Search latest = normalize(new SoundCatalog.Search(null, null, null, null, null,
            SoundCatalog.Sort.NEWEST, EXPLORE_SIZE, null));

        return sounds.read(() -> {
            List<QueryRecord> trendingRows = sounds.trending(clock.instant().minus(7, ChronoUnit.DAYS), EXPLORE_SIZE);
            List<QueryRecord> latestRows = sounds.search(latest, null, null, EXPLORE_SIZE);
            Map<String, SoundCatalog.Card> cards = cardsById(trendingRows, latestRows, viewer);

            return new SoundCatalog.Explore(tags(sounds.tags(null, null, EXPLORE_SIZE)),
                orderedCards(trendingRows, cards), orderedCards(latestRows, cards));
        });
    }

    public SoundCatalog.Suggestions suggestions(String query, String viewer) {
        String q = normalizeText(query);
        if (q.isEmpty())
            throw Exception.invalidRequest("Specify a search prefix");

        return sounds.read(() -> {
            List<QueryRecord> rows = sounds.suggestions(q, viewer, MINE_SIZE);

            return new SoundCatalog.Suggestions(cards(rows, viewer), tags(sounds.tags(q, viewer, SUGGESTION_TAGS)));
        });
    }

    public SoundCatalog.Creator creator(String userId) {
        String id = identity(userId);
        QueryRecord totals = sounds.creator(id);

        return new SoundCatalog.Creator(discord.user(id), timestamp(totals, "first_upload"), totals.getAsLong("sound_count"),
            totals.getAsLong("plays"), totals.getAsLong("likes"), totals.getAsLong("dislikes"), totals.getAsLong("comment_count"));
    }

    public SoundCatalog.Page<SoundComment> comments(String soundId, String viewer, String parentId,
            SoundComment.Sort order, Integer pageSize, String cursor) {
        String id = id(soundId);
        String parent = parentId == null ? null : id(parentId);
        SoundComment.Sort sort = order == null ? SoundComment.Sort.TOP : order;
        int limit = limit(pageSize);
        String scope = scope("comments", id, parent, sort);
        Cursor after = decode(cursor, scope);

        return sounds.read(() -> {
            sounds.visibleSound(id, viewer);

            if (parent != null)
                sounds.requireParent(id, parent);

            QueryRecord pinned = parent == null && after == null ? sounds.pinned(id, viewer) : null;
            String pinnedId = after == null ? pinned == null ? null : pinned.get("id") : after.pinnedId();
            Position position = after != null && after.position().id() > 0 ? after.position() : null;
            int remaining = pinned == null ? limit : limit - 1;

            List<QueryRecord> rows = sounds.comments(id, parent, viewer, pinnedId, sort, position, remaining + 1);
            List<SoundComment> items = new ArrayList<>();
            Map<String, Map<String, Object>> authors = new HashMap<>();

            if (pinned != null)
                items.add(comment(pinned, viewer, authors));

            for (QueryRecord row : rows.subList(0, Math.min(rows.size(), remaining)))
                items.add(comment(row, viewer, authors));

            String next = null;
            if (rows.size() > remaining) {
                Position nextPosition = remaining == 0 ? new Position(0, 0, 0) : commentPosition(rows.get(remaining - 1), sort);
                next = encode(scope, nextPosition, pinnedId);
            }

            return new SoundCatalog.Page<>(List.copyOf(items), next);
        });
    }

    public SoundComment.Change addComment(String soundId, String viewer, String parentId, String text) {
        String actor = requireUser(viewer);
        String parent = parentId == null ? null : id(parentId);
        CommentChange result = sounds.addComment(id(soundId), actor, parent, commentText(text));

        return new SoundComment.Change(comment(result.row(), actor, new HashMap<>()), result.commentCount(), null);
    }

    public SoundComment.Change editComment(String soundId, String commentId, String viewer, SoundComment.Edit edit) {
        String actor = requireUser(viewer);
        if (edit == null || edit.text() == null && edit.pinned() == null && edit.hearted() == null)
            throw Exception.invalidRequest("Specify at least one comment field");

        SoundComment.Edit normalized = new SoundComment.Edit(edit.text() == null ? null : commentText(edit.text()),
            edit.pinned(), edit.hearted());
        CommentChange result = sounds.editComment(id(soundId), id(commentId), actor, normalized);

        return new SoundComment.Change(comment(result.row(), actor, new HashMap<>()), result.commentCount(), result.previousPinnedId());
    }

    public SoundComment.Removal deleteComment(String soundId, String commentId, String viewer) {
        String actor = requireUser(viewer);
        CommentChange result = sounds.deleteComment(id(soundId), id(commentId), actor);
        SoundComment placeholder = result.row() == null ? null : comment(result.row(), actor, new HashMap<>());

        return new SoundComment.Removal(placeholder, result.commentCount());
    }

    public SoundVote.Result vote(String soundId, String commentId, String viewer, SoundVote vote) {
        String actor = requireUser(viewer);
        String id = id(soundId);
        String target = commentId == null ? null : id(commentId);
        if (target == null)
            return withSound(id, sound -> sound.vote(actor, vote));

        return sounds.vote(id, target, actor, vote);
    }

    public int recordWebPlay(String soundId, String viewer, UUID playbackId) {
        if (playbackId == null)
            throw Exception.invalidRequest("A playback ID is required");

        String user = viewer == null ? null : requireUser(viewer);

        return withSound(id(soundId), sound -> sound.recordWebPlay(user, playbackId));
    }

    public String putImage(String soundId, String viewer, InputStream input) throws IOException {
        String actor = requireUser(viewer);
        String id = id(soundId);
        QueryRecord sound = sounds.visibleSound(id, actor);
        if (!actor.equals(sound.get("user_id")))
            throw new Exception(Type.FORBIDDEN, "sound_forbidden", "Only the uploader can edit this sound");

        SoundFiles files = SoundFiles.getInstance();
        String key = files.uploadImage(id, input);
        String previous;

        try {
            previous = sounds.saveImage(id, actor, key);
        } catch (RuntimeException failure) {
            removeUnreferencedImage(files, id, key);
            throw failure;
        }

        files.deleteImage(previous);

        return files.imageUrl(key);
    }

    public void deleteImage(String soundId, String viewer) {
        String previous = sounds.saveImage(id(soundId), requireUser(viewer), null);
        SoundFiles.getInstance().deleteImage(previous);
    }

    /* -------------------------------------------------------------------------- */

    private <T> T withSound(String id, Function<Sound, T> operation) {
        try {
            return cache.with(id, operation);
        } catch (NoSuchElementException failure) {
            throw new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found");
        }
    }

    private List<SoundCatalog.Card> cards(List<QueryRecord> rows, String viewer) {
        return orderedCards(rows, cardsById(rows, List.of(), viewer));
    }

    private Map<String, SoundCatalog.Card> cardsById(List<QueryRecord> first, List<QueryRecord> second, String viewer) {
        Map<String, QueryRecord> rows = new LinkedHashMap<>();
        for (QueryRecord row : first)
            rows.put(row.get("id"), row);
        for (QueryRecord row : second)
            rows.put(row.get("id"), row);

        Map<String, List<Tag>> tags = new HashMap<>();
        for (QueryRecord row : database.readSoundTags(rows.keySet()))
            tags.computeIfAbsent(row.get("sound_id"), ignored -> new ArrayList<>())
                .add(new Tag(row.getAsInt("tag_id"), row.get("name")));

        Map<String, QueryRecord> extras = new HashMap<>();
        for (QueryRecord extra : database.readSoundEnrichment(rows.keySet(), viewer))
            extras.put(extra.get("id"), extra);

        Map<String, Map<String, Object>> uploaders = new HashMap<>();
        Map<String, SoundCatalog.Card> cards = new HashMap<>();
        for (QueryRecord row : rows.values()) {
            String id = row.get("id");
            String uploaderId = row.get("user_id");
            QueryRecord extra = extras.get(id);
            long listeners = extra == null ? 0 : extra.getAsLong("listeners");
            SoundVote vote = extra == null ? null : SoundVote.fromValue(extra.getAsInt("vote"));
            SoundCatalog.Viewer personal = viewer == null ? null : new SoundCatalog.Viewer(viewer.equals(uploaderId), vote);
            long duration = row.getAsLong("duration_ms");

            cards.put(id, new SoundCatalog.Card(id, row.get("name"), row.getAsBoolean("public"), timestamp(row, "time"),
                duration > 0 ? duration : null, cache.playbackUrl(row), SoundFiles.getInstance().imageUrl(row.get("image_key")),
                uploaders.computeIfAbsent(uploaderId, discord::user), List.copyOf(tags.getOrDefault(id, List.of())),
                row.getAsLong("plays"), listeners, row.getAsLong("likes"), row.getAsLong("dislikes"),
                row.getAsLong("comment_count"), personal));
        }

        return cards;
    }

    private static List<SoundCatalog.Card> orderedCards(List<QueryRecord> rows, Map<String, SoundCatalog.Card> cards) {
        List<SoundCatalog.Card> result = new ArrayList<>(rows.size());

        for (QueryRecord row : rows)
            result.add(cards.get(row.get("id")));

        return List.copyOf(result);
    }

    private static List<SoundCatalog.TagCount> tags(List<QueryRecord> rows) {
        List<SoundCatalog.TagCount> result = new ArrayList<>();

        for (QueryRecord row : rows)
            result.add(new SoundCatalog.TagCount(row.get("id"), row.get("name"), row.getAsLong("sound_count")));

        return List.copyOf(result);
    }

    private SoundComment comment(QueryRecord row, String viewer, Map<String, Map<String, Object>> users) {
        boolean deleted = row.get("deleted_at") != null;
        String author = row.get("author_id");
        SoundComment.Viewer personal = viewer == null || deleted ? null
            : new SoundComment.Viewer(viewer.equals(author), SoundVote.fromValue(row.getAsInt("vote")));

        return new SoundComment(row.get("id"), row.get("sound_id"), row.get("parent_id"),
            deleted ? null : users.computeIfAbsent(author, discord::user), row.get("body"), timestamp(row, "created_at"),
            deleted ? null : timestamp(row, "edited_at"), deleted, row.getAsBoolean("pinned"), row.getAsBoolean("hearted"),
            row.getAsLong("likes"), row.getAsLong("reply_count"), !deleted && author.equals(row.get("uploader_id")), personal);
    }

    private static SoundCatalog.Search normalize(SoundCatalog.Search request) {
        if (request == null)
            return new SoundCatalog.Search("", "", null, null, null, SoundCatalog.Sort.POPULAR, PAGE_SIZE, null);

        String q = normalizeText(request.q());
        String tag = normalizeText(request.tag());
        if (request.tag() != null && tag.isEmpty())
            throw Exception.invalidRequest("Specify a tag name");

        String uploader = request.uploaderId() == null ? null : identity(request.uploaderId());
        Long min = request.minDurationMs();
        Long max = request.maxDurationMs();

        if (min != null && min < 0 || max != null && max < 0 || min != null && max != null && min > max)
            throw Exception.invalidRequest("Invalid duration range");

        SoundCatalog.Sort sort = request.sort();
        if (sort == null)
            sort = q.isEmpty() ? SoundCatalog.Sort.POPULAR : SoundCatalog.Sort.RELEVANCE;

        return new SoundCatalog.Search(q, tag, uploader, min, max, sort, limit(request.limit()), request.cursor());
    }

    private static Position catalogPosition(QueryRecord row) {
        return new Position(row.getAsLong("sort_rank"), row.getAsLong("sort_score"), row.getAsLong("id"));
    }

    private static Position commentPosition(QueryRecord row, SoundComment.Sort sort) {
        long rank = switch (sort) {
            case TOP -> row.getAsLong("likes");
            case MOST_DISLIKED -> row.getAsLong("dislikes");
            case NEWEST, OLDEST -> 0;
        };
        Instant created = row.getAsInstant("created_at");
        long micros = created.getEpochSecond() * 1_000_000 + created.getNano() / 1_000;

        return new Position(rank, micros, row.getAsLong("id"));
    }

    private static String scope(Object... filters) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object filter : filters) {
                byte[] value = Objects.toString(filter, "").getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(value.length).array());
                digest.update(value);
            }

            return CURSOR_ENCODER.encodeToString(digest.digest());
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String encode(String scope, Position position, String pinnedId) {
        String fields = String.join(":", scope, Long.toString(position.rank()), Long.toString(position.score()),
            Long.toString(position.id()), Objects.toString(pinnedId, ""));

        return CURSOR_ENCODER.encodeToString(fields.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String cursor, String scope) {
        if (cursor == null)
            return null;

        try {
            if (cursor.length() <= 200) {
                String[] fields = new String(CURSOR_DECODER.decode(cursor), StandardCharsets.UTF_8).split(":", -1);
                if (fields.length == 5 && scope.equals(fields[0])) {
                    Position position = new Position(Long.parseLong(fields[1]), Long.parseLong(fields[2]), Long.parseLong(fields[3]));
                    String pinnedId = fields[4].isEmpty() ? null : id(fields[4]);

                    if (position.id() >= 0 && position.rank() >= 0)
                        return new Cursor(position, pinnedId);
                }
            }
        } catch (IllegalArgumentException failure) {
            throw Exception.invalidRequest("Invalid cursor");
        }

        throw Exception.invalidRequest("Invalid cursor");
    }

    private static int limit(Integer size) {
        if (size == null)
            return PAGE_SIZE;
        if (size < 1 || size > MAX_PAGE_SIZE)
            throw Exception.invalidRequest("Invalid page size");

        return size;
    }

    private static String id(String value) {
        if (ValidationUtils.isDecimalId(value)) {
            try {
                return Long.toString(Long.parseLong(value));
            } catch (NumberFormatException failure) {
                throw Exception.invalidRequest("Invalid ID");
            }
        }

        throw Exception.invalidRequest("Invalid ID");
    }

    private static String identity(String value) {
        if (!ValidationUtils.isDecimalId(value) || value.startsWith("0"))
            throw Exception.invalidRequest("Invalid Discord user ID");

        return value;
    }

    private static String requireUser(String user) {
        if (user == null)
            throw new Exception(Type.UNAUTHENTICATED, "authentication_required", "Authentication required");

        return identity(user);
    }

    private static String normalizeText(String value) {
        if (value == null)
            return "";
        if (value.length() > 255)
            throw Exception.invalidRequest("Search value is too long");

        return value.strip().toLowerCase(Locale.ROOT);
    }

    private static String commentText(String value) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > MAX_COMMENT_LENGTH)
            throw Exception.invalidRequest("Invalid comment length");

        return text;
    }

    private static String timestamp(QueryRecord row, String column) {
        Instant instant = row.getAsInstant(column);

        return instant == null ? null : instant.toString();
    }

    private void removeUnreferencedImage(SoundFiles files, String soundId, String key) {
        try {
            for (QueryRecord row : database.readSounds(List.of(soundId))) {
                if (key.equals(row.get("image_key")))
                    return;
            }

            files.deleteImage(key);
        } catch (RuntimeException failure) {
            LOG.warn("Could not verify whether sound image {} is referenced", key, failure);
        }
    }

    private record Cursor(Position position, String pinnedId) {}
}
