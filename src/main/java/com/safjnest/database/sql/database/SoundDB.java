package com.safjnest.database.sql.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.safjnest.bot.audio.types.AudioType;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.SoundCatalog;
import com.safjnest.sound.model.SoundComment;
import com.safjnest.sound.model.SoundVote;

public final class SoundDB {

    private final BotDB database;

    public SoundDB(BotDB database) {
        this.database = database;
    }

    public record Position(long rank, long score, long id) {}

    public record CommentChange(QueryRecord row, long commentCount, String previousPinnedId) {}

    public <T> T read(Supplier<T> work) {
        return database.withSoundReadSession(work);
    }

    public QueryRecord visibleSound(String id, String viewerId) {
        QueryRecord sound = soundRow(id);
        requireVisibility(sound, viewerId);

        return sound;
    }

    public List<QueryRecord> search(SoundCatalog.Search filter, String ownerId, Position after, int limit) {
        List<Object> values = new ArrayList<>();
        String rank = "0";
        if (!filter.q().isEmpty() && filter.sort() == SoundCatalog.Sort.RELEVANCE) {
            rank = "CASE WHEN LOWER(s.name) = ? THEN 2 WHEN LOWER(s.name) LIKE ? ESCAPE '!' THEN 1 ELSE 0 END";
            values.add(filter.q());
            values.add(BotDB.escapeSoundQuery(filter.q()) + "%");
        }

        String score = switch (filter.sort()) {
            case NEWEST -> "COALESCE(TIMESTAMPDIFF(MICROSECOND, TIMESTAMP '1970-01-01 00:00:00', s.time), 0)";
            case MOST_LIKED -> "s.likes";
            case MOST_DISLIKED -> "s.dislikes";
            case POPULAR, RELEVANCE -> "s.plays";
        };

        StringBuilder sql = new StringBuilder("SELECT * FROM (SELECT s.*, ")
            .append(rank).append(" AS sort_rank, ").append(score).append(" AS sort_score FROM sound s WHERE ");
        if (ownerId == null) {
            sql.append("s.public=1");
        } else {
            sql.append("s.user_id=?");
            values.add(ownerId);
        }

        if (!filter.q().isEmpty()) {
            String pattern = "%" + BotDB.escapeSoundQuery(filter.q()) + "%";
            sql.append("""
                 AND (LOWER(s.name) LIKE ? ESCAPE '!' OR EXISTS (
                    SELECT 1 FROM tag_sounds ts JOIN tag t ON t.id=ts.tag_id
                    WHERE ts.sound_id=s.id AND LOWER(t.name) LIKE ? ESCAPE '!'
                ))
                """);
            values.add(pattern);
            values.add(pattern);
        }

        if (!filter.tag().isEmpty()) {
            sql.append(" AND EXISTS (SELECT 1 FROM tag_sounds ts JOIN tag t ON t.id=ts.tag_id")
                .append(" WHERE ts.sound_id=s.id AND LOWER(t.name)=?)");
            values.add(filter.tag());
        }

        if (filter.uploaderId() != null) {
            sql.append(" AND s.user_id=?");
            values.add(filter.uploaderId());
        }

        if (filter.minDurationMs() != null) {
            sql.append(" AND s.duration_ms>=?");
            values.add(filter.minDurationMs());
        }

        if (filter.maxDurationMs() != null) {
            sql.append(" AND s.duration_ms<=?");
            values.add(filter.maxDurationMs());
        }

        sql.append(") ranked");
        if (after != null) {
            sql.append(" WHERE (sort_rank < ? OR (sort_rank = ? AND (sort_score < ? OR (sort_score = ? AND id < ?))))");
            values.add(after.rank());
            values.add(after.rank());
            values.add(after.score());
            values.add(after.score());
            values.add(after.id());
        }

        sql.append(" ORDER BY sort_rank DESC, sort_score DESC, id DESC LIMIT ?");
        values.add(limit);

        return database.readSoundRows(sql.toString(), values.toArray());
    }

    public List<QueryRecord> suggestions(String q, String viewer, int limit) {
        String escaped = BotDB.escapeSoundQuery(q);

        return database.readSoundRows("""
            SELECT s.* FROM sound s
            WHERE (s.public=1 OR s.user_id=?) AND LOWER(s.name) LIKE ? ESCAPE '!'
            ORDER BY CASE WHEN LOWER(s.name) LIKE ? ESCAPE '!' THEN 0 ELSE 1 END,
                s.plays DESC, s.id DESC LIMIT ?
            """, viewer, "%" + escaped + "%", escaped + "%", limit);
    }

    public List<QueryRecord> tags(String prefix, String viewerId, int limit) {
        List<Object> args = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
            SELECT t.id, t.name, COUNT(DISTINCT s.id) AS sound_count
            FROM tag t JOIN tag_sounds ts ON ts.tag_id=t.id JOIN sound s ON s.id=ts.sound_id
            WHERE (s.public=1
            """);

        if (viewerId != null) {
            sql.append(" OR s.user_id=?");
            args.add(viewerId);
        }

        sql.append(")");
        if (prefix != null) {
            sql.append(" AND LOWER(t.name) LIKE ? ESCAPE '!'");
            args.add(BotDB.escapeSoundQuery(prefix) + "%");
        }

        sql.append(" GROUP BY t.id, t.name ORDER BY sound_count DESC, t.name, t.id LIMIT ?");
        args.add(limit);

        return database.readSoundRows(sql.toString(), args.toArray());
    }

    public List<QueryRecord> trending(Instant since, int limit) {
        return database.readSoundRows("""
            SELECT s.* FROM sound s JOIN (
                SELECT sound_id, COUNT(*) AS recent_plays FROM sound_play
                WHERE time >= ? GROUP BY sound_id
            ) recent ON recent.sound_id=s.id
            WHERE s.public=1 ORDER BY recent.recent_plays DESC, s.plays DESC, s.id DESC LIMIT ?
            """, Timestamp.from(since), limit);
    }

    public QueryRecord creator(String userId) {
        return database.readSoundRows("""
            SELECT COUNT(*) AS sound_count, MIN(time) AS first_upload,
                SUM(plays) AS plays, SUM(likes) AS likes, SUM(dislikes) AS dislikes, SUM(comments) AS comments
            FROM sound WHERE public=1 AND user_id=?
            """, userId).getFirst();
    }

    public QueryRecord pinned(String soundId, String viewerId) {
        List<QueryRecord> rows = database.readSoundRows(commentProjection() + """
            WHERE c.sound_id=? AND c.parent_id IS NULL AND c.pinned=1 AND c.deleted_at IS NULL
            """, viewerId, soundId);

        return rows.isEmpty() ? null : rows.getFirst();
    }

    public List<QueryRecord> comments(String soundId, String parentId, String viewerId, String excludeId,
            SoundComment.Sort sort, Position after, int limit) {
        List<Object> args = new ArrayList<>();
        args.add(viewerId);
        args.add(soundId);

        StringBuilder sql = new StringBuilder(commentProjection()).append("WHERE c.sound_id=? AND ");
        if (parentId == null) {
            sql.append("c.parent_id IS NULL AND c.pinned=0");
        } else {
            sql.append("c.parent_id=?");
            args.add(parentId);
        }

        if (excludeId != null) {
            sql.append(" AND c.id<>?");
            args.add(excludeId);
        }

        String rank = switch (sort) {
            case TOP -> "c.likes";
            case MOST_DISLIKED -> "c.dislikes";
            case NEWEST, OLDEST -> null;
        };
        boolean oldest = sort == SoundComment.Sort.OLDEST;
        String comparison = oldest ? " > ?" : " < ?";

        if (after != null) {
            sql.append(" AND (");
            if (rank != null) {
                sql.append(rank).append(" < ? OR (").append(rank).append(" = ? AND (");
                args.add(after.rank());
                args.add(after.rank());
            }

            sql.append("c.created_at").append(comparison)
                .append(" OR (c.created_at = ? AND c.id").append(comparison).append("))");
            Timestamp time = timeFromMicros(after.score());
            args.add(time);
            args.add(time);
            args.add(after.id());

            if (rank != null)
                sql.append("))");
        }

        sql.append(" ORDER BY ");
        if (rank != null)
            sql.append(rank).append(" DESC, ");

        sql.append(oldest ? "c.created_at ASC, c.id ASC" : "c.created_at DESC, c.id DESC").append(" LIMIT ?");
        args.add(limit);

        return database.readSoundRows(sql.toString(), args.toArray());
    }

    public void requireParent(String soundId, String parentId) {
        if (database.readSoundRows("SELECT id FROM sound_comment WHERE sound_id=? AND id=?", soundId, parentId).isEmpty())
            throw commentMissing();
    }

    public CommentChange addComment(String soundId, String authorId, String parentId, String text) {
        return database.writeSound(connection -> {
            QueryRecord sound = lockVisible(connection, soundId, authorId);
            if (parentId != null && comment(connection, soundId, parentId).get("deleted_at") != null)
                throw commentMissing();

            String id = insertComment(connection, soundId, authorId, parentId, text);
            database.soundExecute(connection, "UPDATE sound SET comments=comments+1 WHERE id=?", soundId);

            if (parentId != null)
                database.soundExecute(connection, "UPDATE sound_comment SET replies=replies+1 WHERE id=?", parentId);

            return new CommentChange(commentForViewer(connection, soundId, id, authorId), sound.getAsLong("comments") + 1, null);
        });
    }

    public CommentChange editComment(String soundId, String id, String viewer, SoundComment.Edit edit) {
        return database.writeSound(connection -> {
            QueryRecord sound = lockVisible(connection, soundId, viewer);
            QueryRecord current = comment(connection, soundId, id);
            if (current.get("deleted_at") != null)
                throw commentMissing();

            if (edit.text() != null && !viewer.equals(current.get("user_id")))
                throw commentForbidden();
            if ((edit.pinned() != null || edit.hearted() != null) && !viewer.equals(sound.get("user_id")))
                throw commentForbidden();
            if (Boolean.TRUE.equals(edit.pinned()) && current.get("parent_id") != null)
                throw Exception.invalidRequest("Only a root comment can be pinned");

            if (edit.text() != null && !edit.text().equals(current.get("body")))
                database.soundExecute(connection, "UPDATE sound_comment SET body=?, edited_at=CURRENT_TIMESTAMP(6) WHERE id=?", edit.text(), id);
            if (edit.hearted() != null)
                database.soundExecute(connection, "UPDATE sound_comment SET hearted=? WHERE id=?", edit.hearted(), id);

            String previous = null;
            if (edit.pinned() != null)
                previous = setPin(connection, soundId, id, edit.pinned());

            return new CommentChange(commentForViewer(connection, soundId, id, viewer), sound.getAsLong("comments"), previous);
        });
    }

    public CommentChange deleteComment(String soundId, String id, String viewer) {
        return database.writeSound(connection -> {
            QueryRecord sound = lockVisible(connection, soundId, viewer);
            QueryRecord current = comment(connection, soundId, id);
            if (current.get("deleted_at") != null)
                throw commentMissing();
            if (!viewer.equals(current.get("user_id")))
                throw commentForbidden();

            database.soundExecute(connection, "UPDATE sound SET comments=comments-1 WHERE id=?", soundId);
            QueryRecord placeholder = null;

            if (current.getAsLong("replies") == 0) {
                deleteEmptyBranch(connection, soundId, current);
            } else {
                database.soundExecute(connection, """
                    UPDATE sound_comment SET body=NULL, deleted_at=CURRENT_TIMESTAMP(6), pinned=0, hearted=0, likes=0, dislikes=0
                    WHERE id=?
                    """, id);
                database.soundExecute(connection, "DELETE FROM sound_vote WHERE comment_id=?", id);
                placeholder = commentForViewer(connection, soundId, id, viewer);
            }

            return new CommentChange(placeholder, sound.getAsLong("comments") - 1, null);
        });
    }

    public SoundVote.Result vote(String soundId, String commentId, String viewer, SoundVote vote) {
        return database.writeSound(connection -> {
            QueryRecord sound = lockVisible(connection, soundId, viewer);
            QueryRecord target = commentId == null ? sound : comment(connection, soundId, commentId);

            if (target.get("deleted_at") != null)
                throw commentMissing();

            return writeVote(database, connection, soundId, commentId, target, viewer,
                vote == null ? 0 : vote.getValue(), null);
        });
    }

    public int recordWebPlay(String soundId, String viewer, UUID playbackId) {
        return database.writeSound(connection -> {
            QueryRecord sound = lockVisible(connection, soundId, viewer);

            return database.insertSoundPlay(connection, sound, viewer, null, null, AudioType.WEB.ordinal(), playbackId.toString());
        });
    }

    public String saveImage(String soundId, String viewer, String key) {
        return database.writeSound(connection -> {
            QueryRecord sound = lockVisible(connection, soundId, viewer);
            if (!viewer.equals(sound.get("user_id")))
                throw new Exception(Type.FORBIDDEN, "sound_forbidden", "Only the uploader can edit this sound");

            database.soundExecute(connection, "UPDATE sound SET thumbnail=? WHERE id=?", key, soundId);

            return sound.get("thumbnail");
        });
    }

    static Sound.Vote setSoundVote(BotDB database, Connection connection, QueryRecord sound,
            String viewer, int value, boolean enabled) throws SQLException {
        if (SoundVote.fromValue(value) == null)
            throw Exception.invalidRequest("Invalid vote");

        String id = sound.get("id");
        SoundVote.Result saved = writeVote(database, connection, id, null, sound, viewer, enabled ? value : 0, enabled ? null : value);

        return new Sound.Vote(id, saved.likes(), saved.dislikes(), saved.vote() == SoundVote.LIKE, saved.vote() == SoundVote.DISLIKE);
    }

    /* -------------------------------------------------------------------------- */

    private QueryRecord soundRow(String id) {
        List<QueryRecord> rows = database.readSounds(List.of(id));
        if (rows.isEmpty())
            throw soundMissing();

        return rows.getFirst();
    }

    private static SoundVote.Result writeVote(BotDB database, Connection connection, String soundId,
            String commentId, QueryRecord target, String viewer, int value, Integer removeOnly) throws SQLException {
        String column = commentId == null ? "sound_id" : "comment_id";
        String id = commentId == null ? soundId : commentId;
        List<QueryRecord> rows = database.soundQuery(connection,
            "SELECT value FROM sound_vote WHERE " + column + "=? AND user_id=?", id, viewer);
        int old = rows.isEmpty() ? 0 : rows.getFirst().getAsInt("value");
        int next = removeOnly != null && old != removeOnly ? old : value;
        long likes = target.getAsLong("likes");
        long dislikes = target.getAsLong("dislikes");

        if (old == next)
            return new SoundVote.Result(soundId, commentId, likes, commentId == null ? dislikes : null, SoundVote.fromValue(next));

        if (next == 0) {
            database.soundExecute(connection, "DELETE FROM sound_vote WHERE " + column + "=? AND user_id=?", id, viewer);
        } else if (old == 0) {
            database.soundExecute(connection, "INSERT INTO sound_vote(" + column + ",user_id,value) VALUES (?,?,?)", id, viewer, next);
        } else {
            database.soundExecute(connection, "UPDATE sound_vote SET value=? WHERE " + column + "=? AND user_id=?", next, id, viewer);
        }

        likes += delta(old, next, 1);
        dislikes += delta(old, next, -1);
        String table = commentId == null ? "sound" : "sound_comment";
        database.soundExecute(connection, "UPDATE " + table + " SET likes=?, dislikes=? WHERE id=?", likes, dislikes, id);

        return new SoundVote.Result(soundId, commentId, likes, commentId == null ? dislikes : null, SoundVote.fromValue(next));
    }

    private static int delta(int old, int next, int expected) {
        return (next == expected ? 1 : 0) - (old == expected ? 1 : 0);
    }

    private QueryRecord lockVisible(Connection connection, String id, String viewer) throws SQLException {
        QueryRecord sound = database.lockSound(connection, id);
        requireVisibility(sound, viewer);

        return sound;
    }

    private static void requireVisibility(QueryRecord sound, String viewer) {
        if (!BotDB.isSoundVisible(sound, viewer, Set.of()))
            throw soundMissing();
    }

    private static String commentProjection() {
        return """
            SELECT c.*, s.user_id AS uploader_id, v.value AS vote
            FROM sound_comment c JOIN sound s ON s.id=c.sound_id
            LEFT JOIN sound_vote v ON v.comment_id=c.id AND v.user_id=?
            """;
    }

    private QueryRecord commentForViewer(Connection connection, String soundId, String id, String viewer) throws SQLException {
        return database.soundQuery(connection, commentProjection() + "WHERE c.sound_id=? AND c.id=?", viewer, soundId, id).getFirst();
    }

    private QueryRecord comment(Connection connection, String soundId, String id) throws SQLException {
        List<QueryRecord> rows = database.soundQuery(connection, "SELECT * FROM sound_comment WHERE sound_id=? AND id=?", soundId, id);
        if (rows.isEmpty())
            throw commentMissing();

        return rows.getFirst();
    }

    private String insertComment(Connection connection, String soundId, String author, String parent, String text) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO sound_comment(sound_id,user_id,parent_id,body) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
            database.bindSound(statement, new Object[]{soundId, author, parent, text});
            statement.executeUpdate();

            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next())
                    throw new SQLException("Missing generated comment ID");

                return keys.getString(1);
            }
        }
    }

    private String setPin(Connection connection, String soundId, String id, boolean pinned) throws SQLException {
        String previous = null;
        if (pinned) {
            List<QueryRecord> rows = database.soundQuery(connection,
                "SELECT id FROM sound_comment WHERE sound_id=? AND pinned=1 AND id<>?", soundId, id);
            if (!rows.isEmpty()) {
                previous = rows.getFirst().get("id");
                database.soundExecute(connection, "UPDATE sound_comment SET pinned=0 WHERE sound_id=? AND pinned=1 AND id<>?", soundId, id);
            }
        }

        database.soundExecute(connection, "UPDATE sound_comment SET pinned=? WHERE id=?", pinned, id);

        return previous;
    }

    private void deleteEmptyBranch(Connection connection, String soundId, QueryRecord current) throws SQLException {
        while (true) {
            database.soundExecute(connection, "DELETE FROM sound_comment WHERE id=?", current.get("id"));
            String parent = current.get("parent_id");
            if (parent == null)
                return;

            database.soundExecute(connection, "UPDATE sound_comment SET replies=replies-1 WHERE id=?", parent);
            current = comment(connection, soundId, parent);
            if (current.get("deleted_at") == null || current.getAsLong("replies") > 0)
                return;
        }
    }

    private static Timestamp timeFromMicros(long micros) {
        return Timestamp.from(Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000), Math.floorMod(micros, 1_000_000) * 1_000));
    }

    private static Exception soundMissing() {
        return new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found");
    }

    private static Exception commentMissing() {
        return new Exception(Type.NOT_FOUND, "comment_not_found", "Comment not found");
    }

    private static Exception commentForbidden() {
        return new Exception(Type.FORBIDDEN, "comment_forbidden", "This user cannot edit the comment");
    }
}
