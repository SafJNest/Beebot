package com.safjnest.bot.cache.managers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Ticker;
import com.mpatric.mp3agic.Mp3File;
import com.safjnest.App;
import com.safjnest.bot.audio.SoundFiles;
import com.safjnest.bot.cache.CacheAdapter;
import com.safjnest.database.redis.RedisClient;
import com.safjnest.database.sql.QueryRecord;
import com.safjnest.database.sql.database.BotDB;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.discord.auth.DiscordApi;
import com.safjnest.sound.SoundAnalytics;
import com.safjnest.sound.SoundPeriod;
import com.safjnest.sound.model.Sound;
import com.safjnest.sound.model.Tag;
import com.safjnest.utils.ValidationUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusStatistics;

public class SoundCache extends CacheAdapter<String, Sound> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SoundCache.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> SELECTION_IDS = new TypeReference<>() {};
    private static final int SELECTION_TTL_SECONDS = 60;
    private static final SelectionStore REDIS = new SelectionStore() {
        @Override
        public String get(String key) {
            return RedisClient.get(key);
        }

        @Override
        public void put(String key, String value, int ttl) {
            RedisClient.setValue(key, value, ttl);
        }
    };

    private static final SoundCache instance = new SoundCache();

    private final BotDB database;
    private final UserCache users;
    private final DiscordApi access;
    private final SelectionStore selections;
    private final SoundAnalytics analytics;

    private volatile String cdnUrl = "";

    public SoundCache() {
        this(BotDB.get(), new DiscordApi(), Ticker.systemTicker());
    }

    SoundCache(BotDB database, DiscordApi access, Ticker ticker) {
        this(database, access, REDIS, ticker, UserCache.getInstance());
    }

    public SoundCache(BotDB database, DiscordApi access, SelectionStore selections, Ticker ticker, UserCache users) {
        super(100, 1, TimeUnit.MINUTES, ticker, false);

        this.database = database;
        this.users = users;

        this.access = access;
        this.selections = selections;
        this.analytics = new SoundAnalytics(database, access);
    }

    public void setCdnUrl(String value) {
        cdnUrl=value == null ? "" : value;
    }

    @Override
    public Collection<Sound> get(Collection<String> keys) {
        return getOrLoadAll(keys);
    }

    public List<Sound> get(String... keys) {
        return getOrLoadAll(List.of(keys));
    }

    public static SoundCache getInstance() {
        return instance;
    }

    public static Sound getSoundByString(String regex, Guild guild, User user) {
        List<QueryRecord> sounds = regex.matches("[0123456789]*")
        ? BotDB.getSoundsById(regex, guild.getId(), user.getId())
        : BotDB.getSoundsByName(regex, guild.getId(), user.getId());
        if(sounds.isEmpty()) {
            return null;
        }
        QueryRecord toPlay = null;
        for(QueryRecord sound : sounds) {
            if(sound.get("guild_id").equals(guild.getId())) {
                toPlay = sound;
                break;
            }
        }
        if(toPlay == null)
        toPlay = sounds.get((int)(Math.random() * sounds.size()));
        return getSoundById(toPlay.get("id"));
    }

    public static Sound getSoundById(String soundId) {
        List<Sound> sounds=instance.load(List.of(soundId));
        return sounds.isEmpty()?null:sounds.get(0);
    }

    public static List<Sound> getSoundsByIds(String[] soundIds) {
        return instance.load(List.of(soundIds));
    }

    public static Sound greetForPlayback(String userId, String guildId) {
        return instance.resolveGreet(userId, guildId);
    }

    public Sound resolveGreet(String userId, String guildId) {
        String[] configured = users.with(userId, user -> new String[] {
            user.getGuildGreet(guildId), user.getGlobalGreet()
        });
        boolean global = configured[0] == null;
        String selected = global ? configured[1] : configured[0];

        if (selected == null)
            return null;

        List<QueryRecord> rows = database.readSounds(List.of(selected));
        if (rows.isEmpty())
            return null;

        QueryRecord row = rows.get(0);
        boolean allowed = row.getAsBoolean("public") || (global
            ? userId.equals(row.get("user_id"))
            : guildId.equals(row.get("guild_id")));

        if (!allowed)
            return null;

        return getOrLoad(selected);
    }

    public static void catalogChanged(String id) {
        if (id != null)
            instance.invalidate(id);

        instance.invalidateSelections();
    }

    public List<Sound> load(List<String> ids) {
        return getOrLoadAll(ids);
    }

    public static List<Sound> getSounds(Collection<String> ids) {
        return instance.getOrLoadAll(ids);
    }

    public static <R> R withSound(String id, Function<? super Sound, ? extends R> operation) {
        return instance.with(id, operation);
    }

    public static <R> R withSounds(Collection<String> ids, Function<? super List<Sound>, ? extends R> operation) {
        return instance.withAll(ids, operation);
    }

    public static boolean deleteSound(String id) {
        try {
            boolean saved = instance.with(id, Sound::delete);
            if (saved)
                instance.remove(id);

            return saved;
        } catch (java.util.NoSuchElementException exception) {
            return false;
        }
    }

    public static List<Sound> searchSound(String regex) {
        return searchSound(regex, null);
    }

    public static List<Sound> searchSound(String regex, String author) {
        List<QueryRecord> result = author == null ? BotDB.extremeSoundResearch(regex) : BotDB.extremeSoundResearch(regex, author);
        List<String> soundIds = new ArrayList<>();
        for (QueryRecord row : result) soundIds.add(row.get("id"));
        List<Sound> sounds = getSoundsByIds(soundIds.toArray(new String[0]));
        final Pattern pattern = Pattern.compile(regex);
        Comparator<Sound> byRelevance = (Sound s1, Sound s2) -> {
            boolean s1Matches = pattern.matcher(s1.getName()).find();
            boolean s2Matches = pattern.matcher(s2.getName()).find();
            if (s1Matches && !s2Matches) {
                return -1;
            }
            else if (!s1Matches && s2Matches) {
                return 1;
            }
            int viewComparison = Integer.compare(s2.getGlobalPlays(), s1.getGlobalPlays());
            if (viewComparison != 0) {
                return viewComparison;
            }
            return Integer.compare(s2.getLikes(), s1.getLikes());
        };
        Collections.sort(sounds, byRelevance);
        int reduceTo = sounds.size() > 25 ? 25 : sounds.size();
        sounds = sounds.subList(0, reduceTo);
        return sounds;
    }
    //      ███        ▄████████    ▄██████▄
    //  ▀█████████▄   ███    ███   ███    ███
    //     ▀███▀▀██   ███    ███   ███    █▀
    //      ███   ▀   ███    ███  ▄███
    //      ███     ▀███████████ ▀▀███ ████▄
    //      ███       ███    ███   ███    ███
    //      ███       ███    ███   ███    ███
    //     ▄████▀     ███    █▀    ████████▀
    //

    public static Tag getTagById(String tag_id) {
        QueryRecord tagData = BotDB.getTag(tag_id);
        if (tagData == null) return null;
        return new Tag(tagData.getAsInt("id"), tagData.get("name"));
    }

    public static Tag getTagByName(String tag_name) {
        int tag_id = BotDB.insertTag(tag_name.toLowerCase());
        return getTagById(String.valueOf(tag_id));
    }
    //     ▄████████  ▄█   ▄█          ▄████████
    //    ███    ███ ███  ███         ███    ███
    //    ███    █▀  ███▌ ███         ███    █▀
    //   ▄███▄▄▄     ███▌ ███        ▄███▄▄▄
    //  ▀▀███▀▀▀     ███▌ ███       ▀▀███▀▀▀
    //    ███        ███  ███         ███    █▄
    //    ███        ███  ███▌    ▄   ███    ███
    //    ███        █▀   █████▄▄██   ██████████
    //                    ▀

    public static OpusFile getOpus(String path) throws IOException {
        File initialFile = new File(path);
        InputStream targetStream = new FileInputStream(initialFile);
        OggFile ogg = new OggFile(targetStream);
        return new OpusFile(ogg);
    }

    public static double getOpusDuration(OpusFile opus) throws IOException {
        OpusStatistics stats = null;
        stats = new OpusStatistics(opus);
        stats.calculate();
        return stats.getDurationSeconds();
    }

    public static double getOpusDuration(String path) throws IOException {
        OpusFile opus = getOpus(path);
        return getOpusDuration(opus);
    }

    public static long getMP3Duration(String filePath) {
        try {
            Mp3File mp3file = new Mp3File(filePath);
            if (mp3file.hasId3v2Tag()) {
                // Duration in seconds
                return mp3file.getLengthInSeconds();
            }
            else {
                throw new java.lang.Exception("MP3 file does not have an ID3v2 tag.");
            }
        }
        catch (java.lang.Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public record Context(String userId, Map<String, DiscordApi.GuildAccess> guilds) {
    }

    public record GuildSounds(String guildId, GuildCounts counts, List<TagCount> tags,
            Map<String, Object> summary, List<Sound> sounds) {}

    public record PersonalSounds(PersonalCounts counts, List<TagCount> tags,
            Map<String, Object> summary, Map<String, GuildGroup> guilds) {}

    public record GuildCounts(int all, long mine, long others) {}
    public record PersonalCounts(int all) {}
    public record GuildGroup(String name, List<Sound> sounds) {}
    public record TagCount(String id, String name, int soundCount) {}
    public record SoundSearch(List<Sound> sounds, int limit) {}
    public record TagSearch(List<MatchedTag> tags) {}
    public record MatchedTag(String id, String name) {}

    public interface SelectionStore {
        String get(String key);
        void put(String key, String value, int ttl);
    }

    public Context context(String userId) {
        return new Context(userId, access.guildAccess(userId));
    }

    public void requireGuild(Context context, String guild) {
        validateId(guild);

        if (!context.guilds().containsKey(guild))
            throw new Exception(Type.FORBIDDEN, "guild_forbidden", "Guild membership and bot presence required");
    }

    public GuildSounds guildSounds(Context context, String guild, SoundPeriod period) {
        return readRequest("guildSounds", () -> {
            requireGuild(context, guild);

            List<QueryRecord> rows = selection(context, guild, false, "", "", "plays", "desc", 0);
            List<QueryRecord> tagRows = database.readSoundTags(rowIds(rows));
            List<Sound> sounds = responses(context, rows, tagRows);

            long mine = 0;
            for (QueryRecord row : rows) {
                if (context.userId().equals(row.get("user_id")))
                    mine++;
            }

            GuildCounts counts = new GuildCounts(rows.size(), mine, rows.size() - mine);
            Map<String, Object> summary = analytics.getSummary(rows, context.userId(), guild, period);

            return new GuildSounds(guild, counts, tagCounts(tagRows), summary, sounds);
        });
    }

    public PersonalSounds mySounds(Context context, SoundPeriod period) {
        return readRequest("mySounds", () -> {
            List<QueryRecord> rows = selection(context, null, true, "", "", "plays", "desc", 0);
            List<QueryRecord> tagRows = database.readSoundTags(rowIds(rows));
            List<Sound> sounds = responses(context, rows, tagRows);
            Map<String, GuildGroup> guilds = new LinkedHashMap<>();

            for (Sound sound : sounds) {
                GuildGroup group = guilds.computeIfAbsent(sound.getGuildId(), id -> {
                    DiscordApi.GuildAccess membership = context.guilds().get(id);

                    return new GuildGroup(membership == null ? null : membership.name(), new ArrayList<>());
                });
                group.sounds().add(sound);
            }

            Map<String, Object> summary = analytics.getSummary(rows, context.userId(), null, period);

            return new PersonalSounds(new PersonalCounts(sounds.size()), tagCounts(tagRows), summary, guilds);
        });
    }

    public Sound sound(Context context,String id) {
        return readRequest("sound", () -> {
            return responses(context,List.of(visible(context,id))).get(0);
        });
    }

    public SoundSearch search(Context context, String q, String tag, String sort, String order, int limit) {
        return readRequest("search", () -> {
            String query = normalize(q);
            String tagName = normalize(tag);

            if (query.isEmpty() && tagName.isEmpty())
                throw Exception.invalidRequest("Specify q or tag");
            if (!Set.of("plays", "likes", "dislikes").contains(sort) || !Set.of("asc", "desc").contains(order)
                    || limit < 1 || limit > 100)
                throw Exception.invalidRequest("Invalid sort, order or limit");

            List<QueryRecord> rows = selection(context, null, false, query, tagName, sort, order, limit);

            return new SoundSearch(responses(context, rows), limit);
        });
    }

    public TagSearch searchTags(Context context, String q) {
        return readRequest("searchTags", () -> {
            String query = normalize(q);
            if (query.isEmpty())
                throw Exception.invalidRequest("Specify a tag prefix");

            List<QueryRecord> rows = database.searchSoundTags(query, context.userId(), context.guilds().keySet());
            List<MatchedTag> tags = new ArrayList<>();

            for (QueryRecord row : rows)
                tags.add(new MatchedTag(row.get("id"), row.get("name")));

            return new TagSearch(tags);
        });
    }

    public Map<String,Object> guildAnalytics(Context context,String guild,SoundPeriod period) {
        return readRequest("guildAnalytics", () -> {
            requireGuild(context,guild);
            return analytics.getGuildAnalytics(selection(context,guild,false,"","","plays","desc",0),context.userId(),guild,period,context.guilds().keySet());
        });
    }

    public Map<String,Object> soundAnalytics(Context context,String id,String guild,SoundPeriod period) {
        return readRequest("soundAnalytics", () -> {
            if(guild!=null)requireGuild(context,guild);
            return analytics.getSoundAnalytics(visible(context,id),context.userId(),guild,period,context.guilds().keySet());
        });
    }

    public Map<String,Object> myAnalytics(Context context,SoundPeriod period) {
        return readRequest("myAnalytics", () -> {
            var rows=selection(context,null,true,"","","plays","desc",0);
            return analytics.getUserAnalytics(rows, context.userId(), period, context.guilds().keySet());
        });
    }

    public <R> R withVisibleSound(Context context, String id, Function<Sound, R> operation) {
        validateId(id);

        try {
            return with(id, sound -> {
                QueryRecord current = visible(context, id);
                List<QueryRecord> tagRows = database.readSoundTags(List.of(id));
                sound.refreshMetadata(current, tags(id, tagRows, true));

                return operation.apply(sound);
            });
        } catch (java.util.NoSuchElementException exception) {
            throw new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found");
        }
    }

    public void removeDeleted(String id) {
        remove(id);
    }
    public Map<String, Sound> greets(Context context) {
        Map<String, String> configured = users.with(context.userId(), user -> user.getAllGreets());

        return greets(context, configured);
    }

    public Map<String, Sound> greets(Context context, Map<String, String> configured) {
        return readRequest("greets", () -> {
            List<QueryRecord> rows = database.readSounds(new LinkedHashSet<>(configured.values()));
            List<QueryRecord> permitted = new ArrayList<>();

            for (QueryRecord row : rows) {
                if (BotDB.isSoundVisible(row, context.userId(), context.guilds().keySet()))
                    permitted.add(row);
            }

            Map<String, Sound> byId = new HashMap<>();
            for (Sound sound : responses(context, permitted))
                byId.put(sound.getId(), sound);

            Map<String, Sound> result = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : configured.entrySet()) {
                Sound sound = byId.get(entry.getValue());
                if (sound != null)
                    result.put(entry.getKey(), sound);
            }

            return result;
        });
    }

    @Override
    protected Map<String, Sound> loadValues(Collection<String> ids) {
        List<QueryRecord> rows = database.readSounds(ids);
        List<QueryRecord> tagRows = database.readSoundTags(ids);
        Map<String, Sound> result = new LinkedHashMap<>();

        for (QueryRecord row : rows) {
            String id = row.get("id");
            result.put(id, new Sound(row, tags(id, tagRows, true), database, this::invalidateSelections));
        }

        return result;
    }

    /* -------------------------------------------------------------------------- */

    private <T> T readRequest(String operation, Supplier<T> work) {
        long started = System.nanoTime();
        try { return database.withSoundReadSession(work); }
        finally { LOG.debug("Sound read {} completed in {} ms", operation, (System.nanoTime() - started) / 1_000_000); }
    }

    private List<String> rowIds(List<QueryRecord> rows) {
        List<String> ids = new ArrayList<>(rows.size());
        for (QueryRecord row : rows) ids.add(row.get("id"));
        return ids;
    }

    private String normalize(String value) {
        if (value == null)
            return "";
        if (value.length() > 255)
            throw Exception.invalidRequest("Query too long");

        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void validateId(String id) {
        if (!ValidationUtils.isDecimalId(id))
            throw Exception.invalidRequest("Invalid ID");
    }

    private QueryRecord visible(Context context, String id) {
        validateId(id);

        List<QueryRecord> rows = database.readSounds(List.of(id));
        if (rows.isEmpty() || !BotDB.isSoundVisible(rows.get(0), context.userId(), context.guilds().keySet()))
            throw new Exception(Type.NOT_FOUND, "sound_not_found", "Sound not found");

        return rows.get(0);
    }

    private List<QueryRecord> selection(Context context, String guild, boolean mine, String q, String tag,
            String sort, String order, int limit) {
        Set<String> memberships = context.guilds().keySet();
        BotDB.SoundSelection select = new BotDB.SoundSelection(context.userId(), memberships, guild, mine, q, tag, sort, order, limit);
        List<?> parameters = List.of(context.userId(), new TreeSet<>(memberships), guild == null ? "" : guild,
            mine, q, tag, sort, order, limit);

        List<String> ids = selectionIds(select, parameters);
        List<QueryRecord> rows = database.readSounds(ids);
        Map<String, QueryRecord> byId = new HashMap<>();

        for (QueryRecord row : rows) {
            if (BotDB.isSoundVisible(row, context.userId(), memberships)
                    && (guild == null || guild.equals(row.get("guild_id")))
                    && (!mine || context.userId().equals(row.get("user_id"))))
                byId.put(row.get("id"), row);
        }

        if (byId.size() != ids.size()) {
            ids = database.selectSounds(select);
            rows = database.readSounds(ids);
            byId.clear();

            for (QueryRecord row : rows) {
                if (BotDB.isSoundVisible(row, context.userId(), memberships))
                    byId.put(row.get("id"), row);
            }
        }

        List<QueryRecord> ordered = new ArrayList<>();
        for (String id : ids) {
            QueryRecord row = byId.get(id);
            if (row != null)
                ordered.add(row);
        }

        return ordered;
    }

    private List<String> selectionIds(BotDB.SoundSelection select, List<?> parameters) {
        String prefix = selectionPrefix();
        String generation = selections.get(prefix + "generation");
        if (generation == null) {
            invalidateSelections();
            generation = selections.get(prefix + "generation");
        }
        if (generation == null)
            return database.selectSounds(select);

        String key = prefix + "v1:" + generation + ":" + selectionHash(parameters);
        String value = selections.get(key);
        if (value != null) {
            try {
                List<String> ids = JSON.readValue(value, SELECTION_IDS);
                if (ids != null && ids.stream().allMatch(id -> id != null && id.matches("[0-9]+")))
                    return ids;
            } catch (java.lang.Exception ignored) {
            }
        }

        List<String> ids = database.selectSounds(select);
        try {
            selections.put(key, JSON.writeValueAsString(ids), SELECTION_TTL_SECONDS);
        } catch (java.lang.Exception ignored) {
        }

        return ids;
    }

    private void invalidateSelections() {
        selections.put(selectionPrefix() + "generation", UUID.randomUUID().toString(), 0);
    }

    private String selectionPrefix() {
        return (App.isTesting() ? "beebot_test" : "beebot") + ":sound:query:";
    }

    private String selectionHash(List<?> parameters) {
        try {
            byte[] json = JSON.writeValueAsString(parameters).getBytes(StandardCharsets.UTF_8);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(json);

            return HexFormat.of().formatHex(digest);
        } catch (java.lang.Exception exception) {
            throw new IllegalArgumentException("Invalid query key", exception);
        }
    }

    private List<Sound> responses(Context context,List<QueryRecord> rows) {
        return responses(context, rows, database.readSoundTags(rowIds(rows)));
    }

    private List<Sound> responses(Context context, List<QueryRecord> rows, List<QueryRecord> tagRows) {
        List<String> ids = rowIds(rows);
        List<QueryRecord> extras = database.readSoundEnrichment(ids, context.userId());
        Map<String, QueryRecord> extraById = new HashMap<>();

        for (QueryRecord extra : extras)
            extraById.put(extra.get("id"), extra);

        List<Sound> sounds = getOrLoadAll(ids, missing -> {
            Set<String> needed = new HashSet<>(missing);
            Map<String, Sound> loaded = new HashMap<>();

            for (QueryRecord row : rows) {
                String id = row.get("id");
                if (needed.contains(id))
                    loaded.put(id, new Sound(row, tags(id, tagRows, true), database, this::invalidateSelections));
            }

            return loaded;
        });

        Map<String, Sound> byId = new HashMap<>();

        for (Sound sound : sounds)
            byId.put(sound.getId(), sound);

        List<Sound> result = new ArrayList<>();
        for (QueryRecord row : rows) {
            String id = row.get("id");
            String guildId = row.get("guild_id");
            boolean administrator = context.guilds().containsKey(guildId) && context.guilds().get(guildId).administrator();
            Sound source = byId.get(id);

            if (source == null)
                source = new Sound(row, tags(id, tagRows, true), database, this::invalidateSelections);

            String url = SoundFiles.getInstance().playbackUrl(id, row.get("extension"), cdnUrl);
            result.add(Sound.forResponse(source, row, tags(id, tagRows, false),
                extraById.getOrDefault(id, new QueryRecord()), context.userId(), administrator, url,
                access.user(row.get("user_id"))));
        }

        return result;
    }

    private List<Tag> tags(String id,List<QueryRecord> rows,boolean padding) {
        List<Tag> result=new ArrayList<>();
        for(var row:rows)if(id.equals(row.get("sound_id")))result.add(new Tag(row.getAsInt("tag_id"),row.get("name")));
        if(padding)while(result.size()<Tag.MAX_TAG_SOUND)result.add(new Tag());
        return result;
    }

    private List<TagCount> tagCounts(List<QueryRecord> tagRows) {
        Map<String, TagCount> tags = new LinkedHashMap<>();
        for (QueryRecord row : tagRows) {
            String id = row.get("tag_id");
            TagCount previous = tags.get(id);
            if (previous == null)
                tags.put(id, new TagCount(id, row.get("name"), 1));
            else
                tags.put(id, new TagCount(id, previous.name(), previous.soundCount() + 1));
        }

        return new ArrayList<>(tags.values());
    }
}
