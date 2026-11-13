package com.safjnest.sound;
import java.time.*;
import java.util.*;
import com.safjnest.sql.QueryRecord;
import com.safjnest.sql.database.SoundDB;
import static com.safjnest.sound.SoundValues.*;

public final class SoundAnalytics {

    private final SoundDB database;

    private final SoundAccess access;

    public SoundAnalytics(SoundDB database, SoundAccess access) {
        this.database = database;
        this.access = access;
    }

    public Map<String, Object> summary(List<QueryRecord> sounds, String userId, String guildId, SoundPeriod period) {
        List<String> ids = ids(sounds);
        return summary(sounds, userId, period, load(ids, guildId, userId, period, false));
    }

    public Map<String, Object> guild(List<QueryRecord> sounds, String userId, String guildId, SoundPeriod period, Set<String> memberships) {
        List<String> ids = ids(sounds);
        History history = load(ids, guildId, userId, period, true);
        var users = history.userRows;
        var times = history.timeRows;
        var sources = history.sourceRows;
        Instant first = history.first(null);
        List<Map<String, Object>> details = new ArrayList<>();
        for (QueryRecord sound : sounds) {
            String id = sound.get("id");
            var soundUsers = history.usersBySound.getOrDefault(id, List.of());
            var detail = group(soundUsers, history.timesBySound.getOrDefault(id, List.of()), history.sourcesBySound.getOrDefault(id, List.of()), userId, period, first, true);
            detail.put("soundId",id);
            detail.put("likes",sound.getAsLong("likes"));
            detail.put("dislikes",sound.getAsLong("dislikes"));
            long old = history.previous(id);
            detail.put("previousPlays",period.from()==null?null:old);
            detail.put("playsChangePercent",period.from()==null?null:change(plays(soundUsers),old));
            detail.put("my",personal(id,userId,guildId,period,memberships,soundUsers,history.personal(id)));
            details.add(detail);
        }
        List<Map<String,Object>> top = new ArrayList<>(details);
        top.sort(Comparator.comparingLong(d -> -((Number)d.get("plays")).longValue()));
        List<QueryRecord> liked = new ArrayList<>(sounds);
        liked.sort(Comparator.comparingLong(s -> -s.getAsLong("likes")));
        List<Map<String,Object>> mostLiked = new ArrayList<>();
        for(var row:liked.subList(0,Math.min(5,liked.size()))) mostLiked.add(map("soundId",row.get("id"),"likes",row.getAsLong("likes")));
        List<Map<String,Object>> topSounds = new ArrayList<>();
        for(var row:top.subList(0,Math.min(5,top.size()))) topSounds.add(map("soundId",row.get("soundId"),"plays",row.get("plays")));
        var summary=totals(users,userId);
        summary.put("likes",sum(sounds,"likes"));
        summary.put("dislikes",sum(sounds,"dislikes"));
        summary.put("soundCount",sounds.size());
        return map("guildId",guildId,"period",period.period(),"timezone",period.zone().getId(),"from",text(period.from()),"to",text(period.to()),
        "summary",summary,"playsPerDay",series(times,period,first),"topSounds",topSounds,"mostLiked",mostLiked,"users",leaderboard(users),"sounds",details);
    }

    public Map<String,Object> sound(QueryRecord sound, String userId, String guildId, SoundPeriod period, Set<String> memberships) {
        return sound(sound, userId, guildId, period, memberships, load(List.of(sound.get("id")), guildId, userId, period, true));
    }

    public Map<String, Object> mine(List<QueryRecord> sounds, String userId, SoundPeriod period, Set<String> memberships) {
        History history = load(ids(sounds), null, userId, period, true);
        List<Object> details = new ArrayList<>();
        for (QueryRecord sound : sounds) details.add(sound(sound, userId, null, period, memberships, history));
        return map("period", period.period(), "timezone", period.zone().getId(), "from", text(period.from()), "to", text(period.to()),
            "summary", summary(sounds, userId, period, history), "sounds", details);
    }

    // ================================================================================================================

    private Map<String, Object> sound(QueryRecord sound, String userId, String guildId, SoundPeriod period, Set<String> memberships, History history) {
        String id = sound.get("id");
        var users = history.usersBySound.getOrDefault(id, List.of());
        var times = history.timesBySound.getOrDefault(id, List.of());
        var sources = history.sourcesBySound.getOrDefault(id, List.of());
        Instant first = history.first(id);
        Map<String,Object> guilds=new LinkedHashMap<>();
        for(String guild:memberships) {
            if(guildId!=null&&!guildId.equals(guild)) continue;
            var rows=guildRows(users,guild);
            if(rows.isEmpty()) continue;
            guilds.put(guild,group(rows,guildRows(times,guild),guildRows(sources,guild),userId,period,first,true));
        }
        var otherUsers=other(users,memberships);
        var otherTimes=other(times,memberships);
        var otherSources=other(sources,memberships);
        var totals=group(users,times,sources,userId,period,first,false);
        totals.put("likes",sound.getAsLong("likes"));
        totals.put("dislikes",sound.getAsLong("dislikes"));
        long previous = history.previous(id);
        totals.put("previousPlays",period.from()==null?null:previous);
        totals.put("playsChangePercent",period.from()==null?null:change(plays(users),previous));
        return map("soundId",id,"period",period.period(),"timezone",period.zone().getId(),"from",text(period.from()),"to",text(period.to()),
        "totals",totals,"guilds",guilds,"otherGuilds",group(otherUsers,otherTimes,otherSources,userId,period,first,false),"my",personal(id,userId,guildId,period,memberships,users,history.personal(id)));
    }

    private Map<String, Object> summary(List<QueryRecord> sounds, String userId, SoundPeriod period, History history) {
        var users = history.userRows;
        Map<String, Object> totals = totals(users, userId);
        long previous = history.previous(null);
        totals.put("period", period.period());
        totals.put("likes", sum(sounds, "likes"));
        totals.put("dislikes", sum(sounds, "dislikes"));
        totals.put("soundCount", sounds.size());
        totals.put("previousPlays", period.from() == null ? null : previous);
        totals.put("playsChangePercent", period.from() == null ? null : change(plays(users), previous));
        return totals;
    }

    private record UserKey(String sound, String guild, String user) {}
    private record TimeKey(String sound, String guild, long bucket) {}
    private record SourceKey(String sound, String guild, int source) {}

    private static final class History {
        private static final int PERSONAL_LIMIT = 101;
        private static final Comparator<SoundDB.Play> CHRONOLOGICAL = Comparator.comparing(SoundDB.Play::time)
            .thenComparingLong(play -> Long.parseLong(play.id()));
        private final SoundPeriod period;
        private final String viewer;
        private final boolean detailed;
        private final Map<UserKey, Long> users = new HashMap<>();
        private final Map<TimeKey, Long> times = new HashMap<>();
        private final Map<SourceKey, Long> sources = new HashMap<>();
        private final Map<String, Long> previous = new HashMap<>();
        private final Map<String, Instant> first = new HashMap<>();
        private final Map<String, PriorityQueue<SoundDB.Play>> personal = new HashMap<>();
        private List<QueryRecord> userRows;
        private List<QueryRecord> timeRows;
        private List<QueryRecord> sourceRows;
        private Map<String, List<QueryRecord>> usersBySound;
        private Map<String, List<QueryRecord>> timesBySound;
        private Map<String, List<QueryRecord>> sourcesBySound;

        private History(SoundPeriod period, String viewer, boolean detailed) {
            this.period = period;
            this.viewer = viewer;
            this.detailed = detailed;
        }

        private void accept(SoundDB.Play play) {
            if (period.from() != null && play.time().isBefore(period.from())) {
                previous.merge(play.soundId(), 1L, Long::sum);
                return;
            }
            users.merge(new UserKey(play.soundId(), play.guildId(), play.userId()), 1L, Long::sum);
            if (!detailed) return;
            first.merge(play.soundId(), play.time(), (a, b) -> a.isBefore(b) ? a : b);
            times.merge(new TimeKey(play.soundId(), play.guildId(), Math.floorDiv(play.time().getEpochSecond(), 900)), 1L, Long::sum);
            sources.merge(new SourceKey(play.soundId(), play.guildId(), play.source()), 1L, Long::sum);
            if (!viewer.equals(play.userId())) return;
            if (period.before() != null && (play.time().isAfter(period.before())
                    || play.time().equals(period.before()) && Long.parseLong(play.id()) >= Long.parseLong(period.beforeId()))) return;
            PriorityQueue<SoundDB.Play> queue = personal.computeIfAbsent(play.soundId(), ignored -> new PriorityQueue<>(PERSONAL_LIMIT, CHRONOLOGICAL));
            if (queue.size() < PERSONAL_LIMIT) queue.add(play);
            else if (CHRONOLOGICAL.compare(play, queue.peek()) > 0) { queue.poll(); queue.add(play); }
        }

        private void finish() {
            userRows = new ArrayList<>(users.size());
            users.forEach((key, count) -> userRows.add(row("sound_id", key.sound(), "guild_id", key.guild(), "user_id", key.user(), "plays", count)));
            timeRows = new ArrayList<>(times.size());
            times.forEach((key, count) -> timeRows.add(row("sound_id", key.sound(), "guild_id", key.guild(), "bucket", key.bucket(), "plays", count)));
            sourceRows = new ArrayList<>(sources.size());
            sources.forEach((key, count) -> sourceRows.add(row("sound_id", key.sound(), "guild_id", key.guild(), "source", key.source(), "plays", count)));
            usersBySound = index(userRows); timesBySound = index(timeRows); sourcesBySound = index(sourceRows);
            users.clear(); times.clear(); sources.clear();
        }

        private Instant first(String id) {
            if (period.from() != null) return period.from();
            if (id != null) return first.getOrDefault(id, period.to());
            Instant earliest = period.to();
            for (Instant time : first.values()) if (time.isBefore(earliest)) earliest = time;
            return earliest;
        }

        private List<QueryRecord> personal(String id) {
            List<SoundDB.Play> ordered = new ArrayList<>(personal.getOrDefault(id, new PriorityQueue<>(CHRONOLOGICAL)));
            ordered.sort(CHRONOLOGICAL.reversed());
            List<QueryRecord> rows = new ArrayList<>(ordered.size());
            for (SoundDB.Play play : ordered) rows.add(row("id", play.id(), "guild_id", play.guildId(),
                "time", java.sql.Timestamp.from(play.time()), "source", play.source()));
            return rows;
        }

        private long previous(String id) {
            if (id != null) return previous.getOrDefault(id, 0L);
            long result = 0; for (long count : previous.values()) result += count; return result;
        }

        private static Map<String, List<QueryRecord>> index(List<QueryRecord> rows) {
            Map<String, List<QueryRecord>> result = new HashMap<>();
            for (QueryRecord row : rows) result.computeIfAbsent(row.get("sound_id"), ignored -> new ArrayList<>()).add(row);
            return result;
        }

        private static QueryRecord row(Object... values) {
            QueryRecord row = new QueryRecord();
            for (int i = 0; i < values.length; i += 2) row.put((String) values[i], values[i+1]);
            return row;
        }
    }

    private History load(List<String> ids, String guildId, String userId, SoundPeriod period, boolean detailed) {
        History history = new History(period, userId, detailed);
        database.forEachPlay(ids, guildId, period.from() == null ? null : period.previous().from(), period.to(), history::accept);
        history.finish();
        return history;
    }

    private Map<String,Object> personal(String id,String user,String guild,SoundPeriod period,Set<String> memberships,List<QueryRecord> users,List<QueryRecord> rows) {
        long count=0;
        for(var row:users) if(user.equals(row.get("user_id"))) count+=row.getAsLong("plays");
        String cursor=rows.size()>100?period.cursor(rows.get(99),id,user,guild):null;
        List<Map<String,Object>> history=new ArrayList<>();
        for(var row:rows.subList(0,Math.min(100,rows.size()))) history.add(map("id",row.get("id"),"playedAt",row.getAsTimestamp("time").toInstant().toString(),
        "guildId",memberships.contains(row.get("guild_id"))?row.get("guild_id"):null,"source",source(row.getAsInt("source"))));
        return map("plays",count,"history",history,"nextCursor",cursor);
    }

    private Map<String,Object> group(List<QueryRecord> users,List<QueryRecord> times,List<QueryRecord> sources,String user,SoundPeriod period,Instant first,boolean identities) {
        var result=totals(users,user);
        result.remove("myPlays");
        result.put("playsPerDay",series(times,period,first));
        long[][] cells=new long[7][24];
        for(var row:times) {
            var time=Instant.ofEpochSecond(row.getAsLong("bucket")*900).atZone(period.zone());
            cells[time.getDayOfWeek().getValue()-1][time.getHour()]+=row.getAsLong("plays");
        }
        List<Map<String,Object>> heat=new ArrayList<>();
        for(int d=0;d<7;d++)for(int h=0;h<24;h++)heat.add(map("weekday",d+1,"hour",h,"plays",cells[d][h]));
        result.put("heatmap",heat);
        Map<String,Long> modes=new TreeMap<>();
        for(var row:sources)modes.merge(source(row.getAsInt("source")),row.getAsLong("plays"),Long::sum);
        List<Map<String,Object>> modesList=new ArrayList<>();
        modes.forEach((key,value)->modesList.add(map("source",key,"plays",value)));
        result.put("sources",modesList);
        Map<String,Long> counts=counts(users);
        long[] buckets=new long[5];
        for(long n:counts.values())buckets[n==1?0:n<=5?1:n<=20?2:n<=50?3:4]++;
        List<Map<String,Object>> replay=new ArrayList<>();
        int[] lower= {
            1,2,6,21,51
        };
        Integer[] upper= {
            1,5,20,50,null
        };
        for(int i=0;i<5;i++)replay.add(map("min",lower[i],"max",upper[i],"users",buckets[i]));
        result.put("replayDistribution",replay);
        if(identities)result.put("users",leaderboard(users));
        return result;
    }

    private Map<String,Object> totals(List<QueryRecord> rows,String user) {
        long plays=plays(rows),mine=0;
        for(var row:rows)if(user.equals(row.get("user_id")))mine+=row.getAsLong("plays");
        int listeners=counts(rows).size();
        return map("plays",plays,"listeners",listeners,"averagePlaysPerListener",listeners==0?0.0:(double)plays/listeners,"myPlays",mine);
    }

    private List<Map<String,Object>> leaderboard(List<QueryRecord> rows) {
        Map<String,Long> counts=counts(rows);
        Map<String,Set<String>> distinct=new HashMap<>();
        for(var row:rows)distinct.computeIfAbsent(row.get("user_id"),k->new HashSet<>()).add(row.get("sound_id"));
        List<String> ids=new ArrayList<>(counts.keySet());
        ids.sort(Comparator.<String>comparingLong(counts::get).reversed().thenComparing(Comparator.naturalOrder()));
        List<Map<String,Object>> result=new ArrayList<>();
        for(String id:ids)result.add(map("user",access.user(id),"plays",counts.get(id),"soundCount",distinct.get(id).size()));
        return result;
    }

    private List<Map<String,Object>> series(List<QueryRecord> rows,SoundPeriod period,Instant first) {
        Map<LocalDate,Long> counts=new HashMap<>();
        for(var row:rows) {
            var date=Instant.ofEpochSecond(row.getAsLong("bucket")*900).atZone(period.zone()).toLocalDate();
            counts.merge(date,row.getAsLong("plays"),Long::sum);
        }
        List<Map<String,Object>> result=new ArrayList<>();
        LocalDate start=first.atZone(period.zone()).toLocalDate(),end=period.to().minusNanos(1).atZone(period.zone()).toLocalDate();
        for(LocalDate date=start;!date.isAfter(end);date=date.plusDays(1))result.add(map("date",date.toString(),"plays",counts.getOrDefault(date,0L)));
        return result;
    }

    private Map<String,Long> counts(List<QueryRecord> rows) {
        Map<String,Long> result=new HashMap<>();
        for(var row:rows)result.merge(row.get("user_id"),row.getAsLong("plays"),Long::sum);
        return result;
    }

    private List<String> ids(List<QueryRecord> rows) {
        List<String> result=new ArrayList<>();
        for(var row:rows)result.add(row.get("id"));
        return result;
    }

    private List<QueryRecord> guildRows(List<QueryRecord> rows,String guild) {
        return rows.stream().filter(row->guild.equals(row.get("guild_id"))).toList();
    }

    private List<QueryRecord> other(List<QueryRecord> rows,Set<String> guilds) {
        return rows.stream().filter(row->!guilds.contains(row.get("guild_id"))).toList();
    }

    private long plays(List<QueryRecord> rows) {
        return sum(rows,"plays");
    }

    private long sum(List<QueryRecord> rows,String key) {
        long result=0;
        for(var row:rows)result+=row.getAsLong(key);
        return result;
    }

    private Double change(long current,long previous) {
        return previous==0?null:100.0*(current-previous)/previous;
    }

    private String text(Instant instant) {
        return instant==null?null:instant.toString();
    }

    private String source(int ordinal) {
        return switch(ordinal) {
            case 0->"AUDIO";
            case 1->"SOUND";
            case 2->"GREET";
            case 3->"SOUNDBOARD";
            default->"UNKNOWN";
        };
    }
}
