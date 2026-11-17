package com.safjnest.sound.model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.safjnest.bot.audio.types.AudioType;
import com.safjnest.bot.cache.managers.SoundCache;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.NONE, getterVisibility = JsonAutoDetect.Visibility.NONE, isGetterVisibility = JsonAutoDetect.Visibility.NONE)
public class Sound {
    @JsonProperty("id")
    private final String ID;
    @JsonProperty("guildId")
    private final String GUILD_ID;
    @JsonProperty("userId")
    private final String USER_ID;

    @JsonProperty("name")
    private volatile String name;
    private String extension;
    @JsonProperty("public")
    private volatile boolean isPublic;
    private Timestamp timestamp;

    @JsonProperty("plays")
    private volatile int plays;
    @JsonProperty("likes")
    private volatile int likes;
    @JsonProperty("dislikes")
    private volatile int dislikes;

    private final BotDB database;
    private final Runnable catalogChanged;

    private Sound original;
    private long revision;
    private boolean deleted;

    private AudioTrack track;

    private volatile List<Tag> tags;

    @JsonProperty private Map<String, Object> uploader;
    @JsonProperty private String url;
    @JsonProperty private volatile Long durationMs;
    @JsonProperty private long listeners;
    @JsonProperty private long myPlays;
    @JsonProperty private boolean likedByMe;
    @JsonProperty private boolean dislikedByMe;
    @JsonProperty private boolean ownedByMe;
    @JsonProperty private boolean canEdit;
    @JsonProperty private boolean canDelete;
    @JsonProperty private long soundboardCount;

    public static Sound forResponse(Sound source, QueryRecord current, List<Tag> tags, QueryRecord extra,
            String viewerId, boolean administrator, String cdn, Map<String, Object> uploader) {
        Sound result = new Sound(current, tags);
        result.uploader = uploader;
        result.url = cdn == null || cdn.isBlank() ? null : cdn.replaceAll("/+$", "") + "/sounds/" + result.ID + "." + result.extension;
        result.durationMs = source.durationMs;

        result.listeners = extra.getAsLong("listeners");
        result.myPlays = extra.getAsLong("my_plays");
        result.likedByMe = extra.getAsInt("vote") == 1;
        result.dislikedByMe = extra.getAsInt("vote") == -1;

        result.ownedByMe = viewerId.equals(result.USER_ID);
        result.canEdit = result.ownedByMe;
        result.canDelete = result.ownedByMe || administrator;
        result.soundboardCount = extra.getAsLong("boards");

        return result;
    }

    @JsonProperty("createdAt")
    public String getCreatedAt() {
        return timestamp == null ? null : timestamp.toInstant().toString();
    }

    @JsonProperty("tags")
    public List<Tag> getJsonTags() {
        ArrayList<Tag> result = new ArrayList<>();
        if (tags != null) {
            for (Tag tag : tags) {
                if (!tag.isEmpty())
                    result.add(new Tag(tag.getId(), tag.getName()));
            }
        }

        return result;
    }

    public Sound() {
        this.database = BotDB.get();
        this.catalogChanged = () -> SoundCache.catalogChanged(null);

        this.ID = null;
        this.GUILD_ID = null;
        this.USER_ID = null;
        this.name = null;
        this.extension = null;
        this.isPublic = false;
        this.timestamp = null;
        this.tags = null;
    }

    public Sound(QueryRecord data, List<Tag> tags) {
        this(data, tags, BotDB.get(), () -> SoundCache.catalogChanged(null));
    }

    public Sound(QueryRecord data, List<Tag> tags, BotDB database, Runnable catalogChanged) {
        this.database = database;
        this.catalogChanged = catalogChanged;

        this.ID = data.get("id");
        this.GUILD_ID = data.get("guild_id");
        this.USER_ID = data.get("user_id");

        this.name = data.get("name");
        this.extension = data.get("extension");
        this.isPublic = data.getAsBoolean("public");
        this.timestamp = data.getAsTimestamp("time");

        this.plays = data.getAsInt("plays");
        this.likes = data.getAsInt("likes");
        this.dislikes = data.getAsInt("dislikes");
        
        this.tags = copyTags(tags);
    }

    private Sound(Sound original) {
        this.ID = original.ID;
        this.GUILD_ID = original.GUILD_ID;
        this.USER_ID = original.USER_ID;

        this.database = original.database;
        this.catalogChanged = original.catalogChanged;

        this.name = original.name;
        this.isPublic = original.isPublic;
        this.tags = copyTags(original.tags);

        this.extension = original.extension;
        this.timestamp = original.timestamp;
        this.durationMs = original.durationMs;

        this.plays = original.plays;
        this.likes = original.likes;
        this.dislikes = original.dislikes;

        this.original = original;
        this.revision = original.revision;
    }

    public record Vote(String soundId, long likes, long dislikes, boolean likedByMe, boolean dislikedByMe) {}

    public synchronized Sound deferEdit() {
        if (original != null)
            throw new IllegalStateException("This sound is already an edit");

        return new Sound(this);
    }

    public boolean update() {
        try {
            return update(USER_ID);
        } catch (Exception exception) {
            return false;
        }
    }

    public boolean update(String userId) {
        if (original == null)
            return deferEdit().update(userId);

        synchronized (original) {
            if (original.deleted || revision != original.revision)
                return false;

            List<Tag> savedTags = database.saveSound(this, userId);

            original.name = name;
            original.isPublic = isPublic;
            original.tags = copyTags(savedTags);
            original.revision++;
        }

        catalogChanged.run();

        return true;
    }

    public synchronized void refreshMetadata(QueryRecord current, List<Tag> currentTags) {
        if (!ID.equals(current.get("id")) || original != null || deleted)
            throw new IllegalStateException("Cannot refresh this sound");

        String refreshedName = current.get("name");
        boolean refreshedPublic = current.getAsBoolean("public");
        List<Tag> refreshedTags = copyTags(currentTags);

        boolean metadataChanged = !Objects.equals(name, refreshedName)
            || isPublic != refreshedPublic
            || !getTagSet(tags).equals(getTagSet(refreshedTags));

        name = refreshedName;
        isPublic = refreshedPublic;
        tags = refreshedTags;

        plays = current.getAsInt("plays");
        likes = current.getAsInt("likes");
        dislikes = current.getAsInt("dislikes");

        if (metadataChanged)
            revision++;
    }

    public String getId() {
        return ID;
    }

    public String getGuildId() {
        return GUILD_ID;
    }

    public String getUserId() {
        return USER_ID;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isPublic() {
        return isPublic;
    }

    public Timestamp getTimestamp() {
        return timestamp == null ? null : new Timestamp(timestamp.getTime());
    }

    public long getTimestampSecond() {
        return timestamp.getTime() / 1000;
    }

    public synchronized AudioTrack getAsTrack() {
        return track;
    }

    public List<Tag> getTags() {
        return copyTags(tags);
    }

    public String getPath() {
        return "rsc" + java.io.File.separator + "sounds" + java.io.File.separator + ID + "." + extension;
    }

    public boolean isOpus() {
        return extension.equals("opus");
    }

    public boolean setName(String name) {
        return change(edit -> edit.name = name);
    }

    public boolean setPublic(boolean isPublic) {
        return change(edit -> edit.isPublic = isPublic);
    }

    public boolean setTags(List<Tag> tags) {
        return change(edit -> edit.tags = copyTags(tags));
    }

    public boolean setTagNames(List<String> names) {
        List<Tag> tags = new ArrayList<>();

        for (String name : new LinkedHashSet<>(names))
            tags.add(new Tag(0, name));

        return setTags(tags);
    }

    public boolean increaseUserPlays(String userId, String guildId) {
        return increaseUserPlays(userId, guildId, AudioType.AUDIO);
    }

    public synchronized boolean increaseUserPlays(String userId, String guildId, AudioType source) {
        if (deleted || original != null)
            return false;

        try {
            plays = database.recordSoundPlay(ID, userId, guildId, source.ordinal());

            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    public synchronized void setTrack(AudioTrack track) {
        this.track = track;
        this.durationMs = track == null ? null : track.getDuration();
    }

    public String getFormattedTags() {
        StringBuilder result = new StringBuilder();

        for (Tag tag : getTags()) {
            if (!tag.isEmpty())
                result.append(tag.getName()).append(", ");
        }

        return result.length() > 2 ? result.substring(0, result.length() - 2) : "";
    }

    public int[] getPlays(String userId) {
        QueryRecord personal = database.readSoundUserPlays(ID, userId);

        return new int[] {plays, personal.getAsInt("times")};
    }

    public int getGlobalPlays() {
        return plays;
    }

    public synchronized int retriveGlobalPlays() {
        List<QueryRecord> rows = database.readSounds(List.of(ID));
        if (rows.isEmpty())
            return 0;

        plays = rows.get(0).getAsInt("plays");

        return plays;
    }

    public synchronized int[] getLikesDislikes(boolean retrieve) {
        if (retrieve) {
            List<QueryRecord> rows = database.readSounds(List.of(ID));
            if (rows.isEmpty())
                return new int[] {0, 0};

            likes = rows.get(0).getAsInt("likes");
            dislikes = rows.get(0).getAsInt("dislikes");
        }

        return new int[] {likes, dislikes};
    }

    public int getLikes() {
        return likes;
    }

    public int getDislikes() {
        return dislikes;
    }

    public boolean like(String userId) {
        return like(userId, true);
    }

    public boolean dislike(String userId) {
        return dislike(userId, true);
    }

    public boolean like(String userId, boolean enabled) {
        return vote(userId, 1, enabled);
    }

    public boolean dislike(String userId, boolean enabled) {
        return vote(userId, -1, enabled);
    }

    public synchronized Vote vote(String userId, Set<String> guilds, int value, boolean enabled) {
        if (deleted || original != null)
            throw new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found");

        Vote saved = database.voteSound(ID, userId, guilds, value, enabled);
        likes = (int) saved.likes();
        dislikes = (int) saved.dislikes();
        catalogChanged.run();

        return saved;
    }

    public boolean hasLiked(String userId) {
        return currentVote(userId) == 1;
    }

    public boolean hasDisliked(String userId) {
        return currentVote(userId) == -1;
    }

    public boolean delete() {
        try {
            return delete(USER_ID, Set.of());
        } catch (Exception exception) {
            return false;
        }
    }

    public synchronized boolean delete(String userId, Set<String> administrators) {
        if (deleted || original != null)
            return false;

        database.deleteSoundData(ID, userId, administrators);
        deleted = true;
        revision++;
        catalogChanged.run();

        return true;
    }

    @Override
    public String toString() {
        return "ID: " + ID;
    }

    /* -------------------------------------------------------------------------- */

    private synchronized boolean change(Consumer<Sound> mutation) {
        if (original != null) {
            mutation.accept(this);

            return true;
        }

        Sound edit = deferEdit();
        mutation.accept(edit);

        return edit.update();
    }

    private boolean vote(String userId, int value, boolean enabled) {
        try {
            vote(userId, Set.of(GUILD_ID), value, enabled);

            return true;
        } catch (Exception exception) {
            return false;
        }
    }

    private int currentVote(String userId) {
        return database.readSoundInteraction(ID, userId).getAsInt("value");
    }

    private static Set<Tag> getTagSet(List<Tag> tags) {
        Set<Tag> result = new HashSet<>();

        if (tags != null) {
            for (Tag tag : tags) {
                if (!tag.isEmpty())
                    result.add(tag);
            }
        }

        return result;
    }

    private static List<Tag> copyTags(List<Tag> tags) {
        List<Tag> result = new ArrayList<>();

        if (tags != null)
            result.addAll(tags);

        while (result.size() < Tag.MAX_TAG_SOUND)
            result.add(new Tag());

        return result;
    }
}
