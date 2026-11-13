package com.safjnest.core.cache.managers;
import com.fasterxml.jackson.databind.JsonNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;
import com.mpatric.mp3agic.Mp3File;
import com.safjnest.auth.AuthException;
import com.safjnest.core.cache.CacheAdapter;
import com.safjnest.model.sound.Sound;
import com.safjnest.model.sound.Tag;
import com.safjnest.sound.SoundAccess;
import com.safjnest.sound.SoundAnalytics;
import com.safjnest.sound.SoundPeriod;
import com.safjnest.sound.SoundQueryCache;
import com.safjnest.sound.SoundValues;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.BotDB;
import com.safjnest.sql.database.SoundDB;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import org.gagravarr.ogg.OggFile;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusStatistics;
import org.springframework.http.HttpStatus;

public class SoundCache extends CacheAdapter<String, Sound> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(SoundCache.class);
    private static final SoundCache instance = new SoundCache();

    private final Cache<String, Sound> objects;
    private final SoundDB database;
    private final SoundAccess access;
    private final SoundQueryCache queries;
    private final SoundAnalytics analytics;
    private volatile String cdnUrl = "";

    public SoundCache() {
        this(new SoundDB(), new SoundAccess(), new SoundQueryCache());
    }

    public SoundCache(SoundDB database, SoundAccess access, SoundQueryCache queries) {
        this(database, access, queries, Ticker.systemTicker());
    }

    public SoundCache(SoundDB database, SoundAccess access, SoundQueryCache queries,
    Ticker ticker) {
        this.database=database;
        this.access=access;
        this.queries=queries;
        this.objects=Caffeine.newBuilder().maximumSize(100).expireAfterWrite(1,TimeUnit.MINUTES).ticker(ticker).build();
        this.analytics=new SoundAnalytics(database, access);
    }

    public void setCdnUrl(String value) {
        cdnUrl=value == null ? "" : value;
    }

    @Override public void put(String key, Sound value) {
        objects.put(key,value);
    }

    @Override public void invalidate(String key) {
        objects.invalidate(key);
    }

    @Override public Sound remove(String key) {
        Sound old=objects.getIfPresent(key);
        objects.invalidate(key);
        return old;
    }

    @Override public boolean contains(String key) {
        return objects.getIfPresent(key)!=null;
    }

    @Override public Collection<String> keySet() {
        return Set.copyOf(objects.asMap().keySet());
    }

    @Override public ConcurrentMap<String,Sound> asTypedMap() {
        return objects.asMap();
    }

    @Override public Collection<Sound> values() {
        return java.util.List.copyOf(objects.asMap().values());
    }

    @Override public int getSize() {
        return objects.asMap().size();
    }

    @Override public int getMaxSize() {
        return 100;
    }

    @Override public void clear() {
        objects.invalidateAll();
    }

    @Override public long expiresAfter(String key) {
        return objects.policy().expireAfterWrite().flatMap(policy->policy.ageOf(key)).map(age->Math.max(0,60_000-age.toMillis())).orElse(0L);
    }

    @Override public Collection<Sound> get(Collection<String> keys) {
        return load(new java.util.ArrayList<>(keys));
    }

    public Sound get(String key) {
        return objects.getIfPresent(key);
    }

    public List<Sound> get(String... keys) {
        return load(List.of(keys));
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
        var greets=database.greets(userId);
        String selected=null;
        boolean global=true;
        for(var row:greets)if("0".equals(row.get("guild_id")))selected=row.get("sound_id");
        for(var row:greets)if(guildId.equals(row.get("guild_id"))) {
            selected=row.get("sound_id");
            global=false;
            break;
        }
        if(selected==null)return null;
        var rows=database.sounds(List.of(selected));
        if(rows.isEmpty())return null;
        var row=rows.get(0);
        boolean allowed=row.getAsBoolean("public")||(global?userId.equals(row.get("user_id")):guildId.equals(row.get("guild_id")));
        if (!allowed) return null;
        List<Sound> sounds = load(List.of(selected));
        return sounds.isEmpty() ? null : sounds.get(0);
    }

    public static void catalogChanged(String id) {
        if(id!=null)instance.invalidate(id);
        instance.queries.invalidate();
    }

    public List<Sound> load(List<String> ids) {
        Map<String,Sound> resolved=new HashMap<>();
        List<String> missing=new ArrayList<>();
        for(String id:ids) {
            Sound sound=get(id);
            if(sound==null)missing.add(id);
            else resolved.put(id,sound);
        }
        if(!missing.isEmpty()) {
            var rows=database.sounds(missing);
            var tagRows=database.tags(missing);
            for(var row:rows) {
                Sound sound=new Sound(row,tags(row.get("id"),tagRows,true));
                put(sound.getId(),sound);
                resolved.put(sound.getId(),sound);
            }
        }
        List<Sound> result=new ArrayList<>();
        for(String id:ids) {
            Sound sound=resolved.get(id);
            if(sound!=null)result.add(sound);
        }
        return result;
    }

    public static boolean deleteSound(String id) {
        boolean result = BotDB.deleteSound(id);
        if (result) instance.remove(id);
        return result;
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
                throw new Exception("MP3 file does not have an ID3v2 tag.");
            }
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public record Context(String userId, Map<String,SoundAccess.GuildAccess> guilds) {
    }

    public Context context(String userId) {
        return new Context(userId,access.guilds(userId));
    }

    public void requireGuild(Context context,String guild) {
        SoundValues.id(guild);
        if(!context.guilds().containsKey(guild))throw new AuthException(HttpStatus.FORBIDDEN,"guild_forbidden","Guild membership and bot presence required");
    }

    public Map<String,Object> guildSounds(Context context,String guild,SoundPeriod period) {
        return readRequest("guildSounds", () -> {
            requireGuild(context,guild);
            var rows=selection(context,guild,false,"","","plays","desc",0);
            var tagRows=database.tags(rowIds(rows));
            List<Sound> sounds=responses(context,rows,tagRows);
            long mine=0;
            for(var row:rows)if(context.userId().equals(row.get("user_id")))mine++;
            return SoundValues.map("guildId",guild,"counts",SoundValues.map("all",rows.size(),"mine",mine,"others",rows.size()-mine),
            "tags",tagCounts(tagRows),"summary",analytics.summary(rows,context.userId(),guild,period),"sounds",sounds);
        });
    }

    public Map<String,Object> mySounds(Context context,SoundPeriod period) {
        return readRequest("mySounds", () -> {
            var rows=selection(context,null,true,"","","plays","desc",0);
            var tagRows=database.tags(rowIds(rows));
            var sounds=responses(context,rows,tagRows);
            Map<String,Object> guilds=new LinkedHashMap<>();
            for(Sound sound:sounds) {
                var membership=context.guilds().get(sound.getGuildId());
                var group=(Map<String,Object>)guilds.computeIfAbsent(sound.getGuildId(),key->SoundValues.map("name",membership==null?null:membership.name(),"sounds",new ArrayList<Sound>()));
                ((List<Sound>)group.get("sounds")).add(sound);
            }
            return SoundValues.map("counts",SoundValues.map("all",sounds.size()),"tags",tagCounts(tagRows),"summary",analytics.summary(rows,context.userId(),null,period),"guilds",guilds);
        });
    }

    public Sound sound(Context context,String id) {
        return readRequest("sound", () -> {
            return responses(context,List.of(visible(context,id))).get(0);
        });
    }

    public Map<String,Object> search(Context context,String q,String tag,String sort,String order,int limit) {
        return readRequest("search", () -> {
            String query=normalize(q);
            String tagName=normalize(tag);
            if(query.isEmpty()&&tagName.isEmpty())throw SoundValues.bad("Specify q or tag");
            if(!Set.of("plays","likes","dislikes").contains(sort)||!Set.of("asc","desc").contains(order)||limit<1||limit>100)
            throw SoundValues.bad("Invalid sort, order or limit");
            return SoundValues.map("sounds",responses(context,selection(context,null,false,query,tagName,sort,order,limit)),"limit",limit);
        });
    }

    public Map<String,Object> searchTags(Context context,String q) {
        return readRequest("searchTags", () -> {
            String query=normalize(q);
            if(query.isEmpty())throw SoundValues.bad("Specify a tag prefix");
            List<Map<String,Object>> result=new ArrayList<>();
            for(var row:database.searchTags(query,context.userId(),context.guilds().keySet()))result.add(SoundValues.map("id",row.get("id"),"name",row.get("name")));
            return SoundValues.map("tags",result);
        });
    }

    public Map<String,Object> guildAnalytics(Context context,String guild,SoundPeriod period) {
        return readRequest("guildAnalytics", () -> {
            requireGuild(context,guild);
            return analytics.guild(selection(context,guild,false,"","","plays","desc",0),context.userId(),guild,period,context.guilds().keySet());
        });
    }

    public Map<String,Object> soundAnalytics(Context context,String id,String guild,SoundPeriod period) {
        return readRequest("soundAnalytics", () -> {
            if(guild!=null)requireGuild(context,guild);
            return analytics.sound(visible(context,id),context.userId(),guild,period,context.guilds().keySet());
        });
    }

    public Map<String,Object> myAnalytics(Context context,SoundPeriod period) {
        return readRequest("myAnalytics", () -> {
            var rows=selection(context,null,true,"","","plays","desc",0);
            return analytics.mine(rows, context.userId(), period, context.guilds().keySet());
        });
    }

    public Sound patch(Context context,String id,JsonNode body) {
        var existing=visible(context,id);
        if(!context.userId().equals(existing.get("user_id")))throw new AuthException(HttpStatus.FORBIDDEN,"sound_forbidden","Only the uploader can edit");
        if(body==null||!body.isObject()||body.isEmpty())throw SoundValues.bad("Nonempty JSON object required");
        Map<String,Object> values=new LinkedHashMap<>();
        List<String> tags=null;
        var fields=body.fields();
        while(fields.hasNext()) {
            var entry=fields.next();
            var value=entry.getValue();
            switch(entry.getKey()) {
                case "name" -> {
                    if(!value.isTextual()||value.asText().isBlank()||value.asText().length()>100||value.asText().matches("[0-9]+"))throw SoundValues.bad("Invalid sound name");
                    values.put("name",value.asText().trim());
                }
                case "public" -> {
                    if(!value.isBoolean())throw SoundValues.bad("public must be boolean");
                    values.put("public",value.asBoolean());
                }
                case "tags" -> {
                    if(!value.isArray()||value.size()>Tag.MAX_TAG_SOUND)throw SoundValues.bad("At most five tags required");
                    Set<String> names=new LinkedHashSet<>();
                    for(var node:value) {
                        if(!node.isTextual()||node.asText().isBlank()||node.asText().length()>255)throw SoundValues.bad("Invalid tag");
                        names.add(normalize(node.asText()));
                    }
                    tags=new ArrayList<>(names);
                }
                default -> throw SoundValues.bad("Unknown sound field");
            }
        }
        database.patch(id,context.userId(),values,tags);
        changed(id);
        return sound(context,id);
    }

    public void delete(Context context,String id) {
        visible(context,id);
        Set<String> admins=new HashSet<>();
        context.guilds().forEach((key,value)-> {
            if(value.administrator())admins.add(key);
        });
        var removed=database.delete(id,context.userId(),admins);
        changed(id);
        String extension=removed.get("extension");
        if(extension!=null&&extension.matches("[a-zA-Z0-9]+")) {
            try {
                java.nio.file.Files.deleteIfExists(java.nio.file.Path.of("rsc","sounds",id+"."+extension));
            }
            catch(java.io.IOException exception) {
                LOG.warn("Unable to remove deleted sound file {}", id, exception);
            }
        }
    }

    public Map<String,Object> vote(Context context,String id,int value,JsonNode body) {
        visible(context,id);
        if(body==null||!body.isObject()||body.size()!=1||!body.path("enabled").isBoolean())throw SoundValues.bad("enabled boolean required");
        database.vote(id,context.userId(),context.guilds().keySet(),value,body.get("enabled").asBoolean());
        changed(id);
        var row=visible(context,id);
        var extra=database.enrichment(List.of(id),context.userId()).get(0);
        return SoundValues.map("soundId",id,"likes",row.getAsLong("likes"),"dislikes",row.getAsLong("dislikes"),"likedByMe",extra.getAsInt("vote")==1,"dislikedByMe",extra.getAsInt("vote")==-1);
    }

    public Map<String,Sound> greets(Context context) {
        return readRequest("greets", () -> {
            var greets=database.greets(context.userId());
            List<String> ids=new ArrayList<>();
            for(var row:greets)ids.add(row.get("sound_id"));
            var rows=database.sounds(ids);
            Map<String,Sound> byId=new HashMap<>();
            List<QueryRecord> permitted=new ArrayList<>();
            for(var row:rows)if(SoundDB.visible(row,context.userId(),context.guilds().keySet()))permitted.add(row);
            for(Sound sound:responses(context,permitted))byId.put(sound.getId(),sound);
            Map<String,Sound> result=new LinkedHashMap<>();
            for(var greet:greets) {
                Sound sound=byId.get(greet.get("sound_id"));
                if(sound!=null)result.put(greet.get("guild_id"),sound);
            }
            return result;
        });
    }

    public Map<String,Object> settings(Context context,JsonNode body) {
        if(body==null||!body.isObject()||body.size()!=1||!body.path("greets").isObject())throw SoundValues.bad("greets object required");
        Map<String,Object> results=new LinkedHashMap<>();
        Set<String> requestedIds = new LinkedHashSet<>();
        for (JsonNode value : body.get("greets")) {
            if (value.isTextual() && value.asText().matches("[0-9]{1,19}")) requestedIds.add(value.asText());
        }
        Map<String, QueryRecord> candidates = new HashMap<>();
        AuthException preloadError = null;
        try {
            for (QueryRecord row : database.sounds(requestedIds)) candidates.put(row.get("id"), row);
        } catch (AuthException exception) { preloadError = exception; }
        var fields=body.get("greets").fields();
        boolean saved=false;
        while(fields.hasNext()) {
            var entry=fields.next();
            String guild=entry.getKey();
            try {
                if(!guild.equals("0"))requireGuild(context,guild);
                String id=null;
                if(!entry.getValue().isNull()) {
                    if(!entry.getValue().isTextual())throw SoundValues.bad("Sound ID must be a string");
                    id=SoundValues.id(entry.getValue().asText());
                    if (preloadError != null) throw preloadError;
                    QueryRecord candidate = candidates.get(id);
                    if (candidate == null || !SoundDB.visible(candidate, context.userId(), context.guilds().keySet()))
                        throw new AuthException(HttpStatus.NOT_FOUND, "sound_not_found", "Sound not found");
                }
                database.greet(context.userId(),guild,id);
                saved=true;
                results.put(guild,SoundValues.map("status","ok","errors",List.of()));
            }
            catch(AuthException exception) {
                results.put(guild,SoundValues.map("status","error","errors",List.of(SoundValues.map("field","soundId","code",exception.code(),"message",exception.getReason()))));
            }
        }
        if(saved)com.safjnest.core.cache.managers.UserCache.getInstance().invalidate(context.userId());
        return SoundValues.map("greets",results);
    }
    // ================================================================================================================

    private <T> T readRequest(String operation, java.util.function.Supplier<T> work) {
        long started = System.nanoTime();
        try { return database.withReadSession(work); }
        finally { LOG.debug("Sound read {} completed in {} ms", operation, (System.nanoTime() - started) / 1_000_000); }
    }

    private List<String> rowIds(List<QueryRecord> rows) {
        List<String> ids = new ArrayList<>(rows.size());
        for (QueryRecord row : rows) ids.add(row.get("id"));
        return ids;
    }

    private void changed(String id) {
        invalidate(id);
        queries.invalidate();
    }

    private String normalize(String value) {
        if(value==null)return "";
        if(value.length()>255)throw SoundValues.bad("Query too long");
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private QueryRecord visible(Context context,String id) {
        SoundValues.id(id);
        var rows=database.sounds(List.of(id));
        if(rows.isEmpty()||!SoundDB.visible(rows.get(0),context.userId(),context.guilds().keySet()))throw new AuthException(HttpStatus.NOT_FOUND,"sound_not_found","Sound not found");
        return rows.get(0);
    }

    private List<QueryRecord> selection(Context context,String guild,boolean mine,String q,String tag,String sort,String order,int limit) {
        var select=new SoundDB.Selection(context.userId(),context.guilds().keySet(),guild,mine,q,tag,sort,order,limit);
        queries.initialize();
        List<?> key=List.of(context.userId(),new TreeSet<>(context.guilds().keySet()),guild==null?"":guild,mine,q,tag,sort,order,limit);
        List<String> ids=queries.ids(key,()->database.select(select));
        var rows=database.sounds(ids);
        Map<String,QueryRecord> byId=new HashMap<>();
        for(var row:rows)if(SoundDB.visible(row,context.userId(),context.guilds().keySet())&&(guild==null||guild.equals(row.get("guild_id")))&&(!mine||context.userId().equals(row.get("user_id"))))byId.put(row.get("id"),row);
        if(byId.size()!=ids.size()) {
            ids=database.select(select);
            rows=database.sounds(ids);
            byId.clear();
            for(var row:rows)if(SoundDB.visible(row,context.userId(),context.guilds().keySet()))byId.put(row.get("id"),row);
        }
        List<QueryRecord> ordered=new ArrayList<>();
        for(String id:ids)if(byId.containsKey(id))ordered.add(byId.get(id));
        return ordered;
    }

    private List<Sound> responses(Context context,List<QueryRecord> rows) {
        return responses(context, rows, database.tags(rowIds(rows)));
    }

    private List<Sound> responses(Context context, List<QueryRecord> rows, List<QueryRecord> tagRows) {
        List<String> ids = rowIds(rows);
        var extras=database.enrichment(ids,context.userId());
        Map<String,QueryRecord> extraById=new HashMap<>();
        for(var extra:extras)extraById.put(extra.get("id"),extra);
        List<Sound> result=new ArrayList<>();
        for(var row:rows) {
            String id=row.get("id");
            Sound cached=get(id);
            if(cached==null) {
                cached=new Sound(row,tags(id,tagRows,true));
                put(id,cached);
            }
            var membership=context.guilds().get(row.get("guild_id"));
            result.add(Sound.forResponse(cached,row,tags(id,tagRows,false),extraById.getOrDefault(id,new QueryRecord()),context.userId(),membership!=null&&membership.administrator(),cdnUrl,access.user(row.get("user_id"))));
        }
        return result;
    }

    private List<Tag> tags(String id,List<QueryRecord> rows,boolean padding) {
        List<Tag> result=new ArrayList<>();
        for(var row:rows)if(id.equals(row.get("sound_id")))result.add(new Tag(row.getAsInt("tag_id"),row.get("name")));
        if(padding)while(result.size()<Tag.MAX_TAG_SOUND)result.add(new Tag());
        return result;
    }

    private List<Map<String,Object>> tagCounts(List<QueryRecord> tagRows) {
        Map<String,Map<String,Object>> tags=new LinkedHashMap<>();
        for(var row:tagRows) {
            var tag=tags.computeIfAbsent(row.get("tag_id"),key->SoundValues.map("id",key,"name",row.get("name"),"soundCount",0));
            tag.put("soundCount",((Number)tag.get("soundCount")).intValue()+1);
        }
        return new ArrayList<>(tags.values());
    }
}
