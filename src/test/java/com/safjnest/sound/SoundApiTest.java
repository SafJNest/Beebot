package com.safjnest.sound;

import org.junit.*;
import static org.junit.Assert.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.fasterxml.jackson.databind.*;
import com.safjnest.auth.*;
import com.safjnest.auth.DiscordApi.AuthUser;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.model.sound.Sound;
import com.safjnest.sql.database.SoundDB;
import com.safjnest.spring.controller.*;
import com.safjnest.spring.config.BeebotAuthFilter;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class SoundApiTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private String url;
    private SoundDB db;
    private SoundCache cache;
    private final AtomicLong tick=new AtomicLong();
    private final Map<String,String> redis=new ConcurrentHashMap<>();
    private MockMvc mvc;
    private final AtomicInteger connectionOpens = new AtomicInteger();
    private final List<String> queries = Collections.synchronizedList(new ArrayList<>());
    private SoundCache.Context viewer;
    @Before public void setup() throws Exception {
        url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MariaDB;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1";
        try(var c=connection();var s=c.createStatement()){
            for(String sql:List.of(
                "CREATE TABLE sound(id INT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(255),guild_id VARCHAR(19),user_id VARCHAR(19),extension VARCHAR(4),public BOOLEAN,time TIMESTAMP,plays INT DEFAULT 0,likes INT DEFAULT 0,dislikes INT DEFAULT 0)",
                "CREATE TABLE sound_history(id INT AUTO_INCREMENT PRIMARY KEY,sound_id INT REFERENCES sound(id) ON DELETE CASCADE,user_id VARCHAR(19),guild_id VARCHAR(19),time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,source INT)",
                "CREATE TABLE sound_interactions(sound_id INT REFERENCES sound(id) ON DELETE CASCADE,user_id VARCHAR(19),value INT,PRIMARY KEY(sound_id,user_id))",
                "CREATE TABLE tag(id INT AUTO_INCREMENT PRIMARY KEY,name VARCHAR(255))",
                "CREATE TABLE tag_sounds(id INT AUTO_INCREMENT PRIMARY KEY,sound_id INT REFERENCES sound(id) ON DELETE CASCADE,tag_id INT REFERENCES tag(id))",
                "CREATE TABLE soundboard_sounds(id INT,sound_id INT REFERENCES sound(id) ON DELETE CASCADE)",
                "CREATE TABLE greeting(user_id VARCHAR(19),guild_id VARCHAR(19),sound_id INT REFERENCES sound(id) ON DELETE CASCADE,PRIMARY KEY(user_id,guild_id))"))s.execute(sql);
            s.execute("INSERT INTO sound(id,name,guild_id,user_id,extension,public,time) VALUES(1,'airhorn','10','100','mp3',TRUE,CURRENT_TIMESTAMP),(2,'secret','20','200','opus',FALSE,CURRENT_TIMESTAMP),(3,'mine','20','100','mp3',FALSE,CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO tag(id,name) VALUES(1,'meme'),(2,'secret')");
            s.execute("INSERT INTO tag_sounds(sound_id,tag_id) VALUES(1,1),(2,2)");
        }
        db=new SoundDB(this::connection);
        var access=new SoundAccess(){
            @Override public Map<String,GuildAccess> guilds(String user){return user.equals("100")?Map.of("10",new GuildAccess("Ten",false)):Map.of("20",new GuildAccess("Twenty",false));}
            @Override public Map<String,Object> user(String id){return SoundValues.map("id",id,"name","User "+id,"avatarUrl",null);}
        };
        var queries=new SoundQueryCache(new SoundQueryCache.Store(){public String get(String key){return redis.get(key);}public void put(String key,String value,int ttl){redis.put(key,value);}});
        cache=new SoundCache(db,access,queries,tick::get);cache.setCdnUrl("https://cdn.example.com/");viewer=cache.context("100");
        var auth=new AuthContext(new ApiKey("app","test",""),"session",null,Long.MAX_VALUE,new AuthUser("100","tester",null,null));
        mvc=MockMvcBuilders.standaloneSetup(new SoundController(cache)).setControllerAdvice(new AuthExceptionHandler(),new AuthRequestExceptionHandler())
            .addFilters(new BeebotAuthFilter((key,bearer,required)->auth)).build();
    }
    private Connection connection() {
        try {
            Connection delegate = DriverManager.getConnection(url);
            try (var statement = delegate.createStatement()) { statement.execute("SET TIME ZONE 'UTC'"); }
            connectionOpens.incrementAndGet();
            return (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement")) queries.add((String) args[0]);
                try { return method.invoke(delegate, args); }
                catch (java.lang.reflect.InvocationTargetException exception) { throw exception.getCause(); }
            });
        } catch (SQLException exception) { throw new IllegalStateException(exception); }
    }
    private void sql(String sql)throws Exception{try(var c=connection();var s=c.createStatement()){s.execute(sql);}}
    private SoundPeriod period(){return SoundPeriod.of("30d","Europe/Rome",null,null,"100",null);}

    @Test public void cacheReturnsLargeBatchesAndExpiresAfterWrite()throws Exception{
        try(var c=connection();var s=c.prepareStatement("INSERT INTO sound(id,name,guild_id,user_id,extension,public) VALUES(?,?,'10','100','mp3',TRUE)")){
            for(int i=4;i<=210;i++){s.setInt(1,i);s.setString(2,"s"+i);s.addBatch();}s.executeBatch();}
        List<String> ids=new ArrayList<>();for(int i=1;i<=210;i++)ids.add(""+i);
        assertEquals(210,cache.load(ids).size());assertTrue(cache.load(List.of()).isEmpty());assertTrue(cache.load(List.of("999")).isEmpty());
        cache.clear();Sound first=cache.load(List.of("1")).get(0);tick.set(TimeUnit.SECONDS.toNanos(50));assertSame(first,cache.get("1"));
        tick.set(TimeUnit.SECONDS.toNanos(61));assertNull(cache.get("1"));assertNotSame(first,cache.load(List.of("1")).get(0));
    }
    @Test public void canonicalJsonIsRequestLocalAndContainsNoOperationalFields()throws Exception{
        db.play("1","100","10",3);db.vote("1","100",Set.of("10"),1,true);
        var mine=JSON.valueToTree(cache.sound(viewer,"1"));var other=JSON.valueToTree(cache.sound(cache.context("200"),"1"));
        assertEquals(1,mine.get("myPlays").asInt());assertTrue(mine.get("likedByMe").asBoolean());assertTrue(mine.get("canEdit").asBoolean());
        assertEquals(0,other.get("myPlays").asInt());assertFalse(other.get("likedByMe").asBoolean());assertFalse(other.get("canEdit").asBoolean());
        assertEquals("https://cdn.example.com/sounds/1.mp3",mine.get("url").asText());
        assertEquals("1",mine.get("tags").get(0).get("id").asText());assertEquals(1,mine.get("tags").size());
        for(String field:List.of("path","asTrack","track","formattedTags","timestampSecond","extension"))assertFalse(mine.has(field));
        var list=JSON.valueToTree(cache.guildSounds(viewer,"10",period())).get("sounds").get(0);assertEquals(mine,list);
    }
    @Test public void queryHitsNeverAuthorizePrivateOrDeletedObjects()throws Exception{
        assertEquals(1,((List<?>)cache.search(viewer,"air",null,"plays","desc",100).get("sounds")).size());
        sql("UPDATE sound SET public=FALSE,guild_id='20',user_id='200' WHERE id=1");
        assertEquals(0,((List<?>)cache.search(viewer,"air",null,"plays","desc",100).get("sounds")).size());
        try{cache.sound(viewer,"1");fail();}catch(AuthException e){assertEquals(404,e.getStatusCode().value());}
        assertEquals(0,((List<?>)cache.searchTags(viewer,"sec").get("tags")).size());
    }
    @Test public void votesAreIdempotentAndRemovingOneDoesNotClearOpposite()throws Exception{
        cache.vote(viewer,"1",1,JSON.readTree("{\"enabled\":true}"));cache.vote(viewer,"1",1,JSON.readTree("{\"enabled\":true}"));
        assertEquals(1,db.sounds(List.of("1")).get(0).getAsInt("likes"));
        cache.vote(viewer,"1",-1,JSON.readTree("{\"enabled\":true}"));cache.vote(viewer,"1",1,JSON.readTree("{\"enabled\":false}"));
        assertEquals(-1,db.enrichment(List.of("1"),"100").get(0).getAsInt("vote"));
        ExecutorService executor=Executors.newFixedThreadPool(4);try{
            List<Future<?>> work=new ArrayList<>();for(int i=0;i<20;i++){final int vote=i%2==0?1:-1;work.add(executor.submit(()->db.vote("1","100",Set.of("10"),vote,true)));}for(var future:work)future.get();
        }finally{executor.shutdownNow();}
        var row=db.sounds(List.of("1")).get(0);assertEquals(1,row.getAsInt("likes")+row.getAsInt("dislikes"));
    }
    @Test public void patchIsAtomicAndGreetsHaveIndependentOutcomes()throws Exception{
        cache.patch(viewer,"1",JSON.readTree("{\"name\":\"updated\",\"tags\":[]}"));assertEquals("updated",cache.sound(viewer,"1").getName());
        try{cache.patch(viewer,"1",JSON.readTree("{\"name\":\"should-not-save\",\"unknown\":1}"));fail();}catch(AuthException expected){}
        assertEquals("updated",db.sounds(List.of("1")).get(0).get("name"));
        var result=JSON.valueToTree(cache.settings(viewer,JSON.readTree("{\"greets\":{\"0\":\"3\",\"10\":\"3\",\"20\":\"1\"}}")));
        assertEquals("ok",result.at("/greets/0/status").asText());assertEquals("error",result.at("/greets/10/status").asText());assertEquals("error",result.at("/greets/20/status").asText());
        assertEquals("3",cache.greets(viewer).get("0").getId());
        cache.settings(viewer,JSON.readTree("{\"greets\":{\"0\":null}}"));assertTrue(cache.greets(viewer).isEmpty());
    }
    @Test public void analyticsDeduplicateUsersAndHideOtherGuildIdentities()throws Exception{
        db.play("1","100","10",3);db.play("1","200","10",0);db.play("1","200","20",2);db.play("1","300","20",3);
        var result=JSON.valueToTree(cache.soundAnalytics(viewer,"1",null,period()));
        assertEquals(4,result.at("/totals/plays").asInt());assertEquals(3,result.at("/totals/listeners").asInt());assertEquals(2,result.at("/otherGuilds/listeners").asInt());
        assertFalse(result.get("otherGuilds").has("users"));assertFalse(result.get("guilds").has("20"));assertEquals(168,result.at("/otherGuilds/heatmap").size());
        assertEquals(1,result.at("/my/history").size());assertFalse(result.at("/guilds/10/users/0").has("playedAt"));
        var guild=JSON.valueToTree(cache.guildAnalytics(viewer,"10",period()));assertEquals(2,guild.at("/summary/plays").asInt());
    }
    @Test public void personalHistoryCursorIsBoundToUserSoundAndPeriod()throws Exception{
        for(int i=0;i<105;i++)db.play("1","100","10",0);
        var first=JSON.valueToTree(cache.soundAnalytics(viewer,"1",null,period()));assertEquals(100,first.at("/my/history").size());
        String cursor=first.at("/my/nextCursor").asText();var next=SoundPeriod.of("30d","Europe/Rome",cursor,"1","100",null);
        var second=JSON.valueToTree(cache.soundAnalytics(viewer,"1",null,next));assertEquals(5,second.at("/my/history").size());assertEquals(105,second.at("/totals/plays").asInt());
        try{SoundPeriod.of("7d","Europe/Rome",cursor,"1","100",null);fail();}catch(AuthException expected){}
    }
    @Test public void responseCopiesRemainPrivateUnderConcurrentReads()throws Exception{
        db.play("1","100","10",0);
        ExecutorService pool=Executors.newFixedThreadPool(6);
        try{
            List<Future<?>> work=new ArrayList<>();
            for(int i=0;i<30;i++){final String user=i%2==0?"100":"200";work.add(pool.submit(()->{
                var json=JSON.valueToTree(cache.sound(cache.context(user),"1"));
                assertEquals(user.equals("100"),json.get("canEdit").asBoolean());
                assertEquals(user.equals("100")?1:0,json.get("myPlays").asInt());
            }));}
            for(var task:work)task.get();
        }finally{pool.shutdownNow();}
    }
    @Test public void sqlFailureRollsBackWholePatchAndDoesNotStopOtherGreetItems()throws Exception{
        sql("ALTER TABLE tag ADD CONSTRAINT rejected_tag CHECK(name <> 'reject')");
        try{cache.patch(viewer,"1",JSON.readTree("{\"name\":\"rolled-back\",\"tags\":[\"reject\"]}"));fail();}catch(AuthException expected){assertEquals(503,expected.getStatusCode().value());}
        assertEquals("airhorn",db.sounds(List.of("1")).get(0).get("name"));assertEquals(1,db.tags(List.of("1")).size());
        sql("ALTER TABLE greeting ADD CONSTRAINT rejected_scope CHECK(guild_id <> '10')");
        var result=JSON.valueToTree(cache.settings(viewer,JSON.readTree("{\"greets\":{\"10\":\"1\",\"0\":\"3\"}}")));
        assertEquals("error",result.at("/greets/10/status").asText());assertEquals("ok",result.at("/greets/0/status").asText());
        assertEquals(1,db.greets("100").size());
    }
    @Test public void timezoneHeatmapCombinesRepeatedDstHourAndAllTimeHasNoComparison()throws Exception{
        sql("INSERT INTO sound_history(sound_id,user_id,guild_id,time,source) VALUES(1,'100','10','2026-10-25 00:30:00',0),(1,'200','10','2026-10-25 01:30:00',0)");
        var result=JSON.valueToTree(cache.soundAnalytics(viewer,"1",null,SoundPeriod.of("all","Europe/Rome",null,null,"100",null)));
        assertEquals(2,result.at("/totals/plays").asInt());assertTrue(result.at("/totals/previousPlays").isNull());
        assertEquals(2,result.at("/totals/heatmap").get(6*24+2).get("plays").asInt());
    }
    @Test public void greetPlaybackRechecksVisibilityAndGlobalOwnerException()throws Exception{
        db.greet("100","0","3");assertEquals("3",cache.resolveGreet("100","10").getId());
        db.greet("100","10","1");assertEquals("1",cache.resolveGreet("100","10").getId());
        sql("UPDATE sound SET public=FALSE,guild_id='20',user_id='200' WHERE id=1");
        assertNull(cache.resolveGreet("100","10"));
        db.greet("100","10",null);assertEquals("3",cache.resolveGreet("100","10").getId());
        sql("UPDATE sound SET user_id='200' WHERE id=3");assertNull(cache.resolveGreet("100","10"));
    }
    @Test public void deletionChecksAdministratorAndCascadesDependentData()throws Exception{
        db.play("1","100","10",0);db.greet("100","0","1");db.vote("1","100",Set.of("10"),1,true);
        try{db.delete("1","200",Set.of());fail();}catch(AuthException expected){assertEquals(403,expected.getStatusCode().value());}
        db.delete("1","200",Set.of("10"));assertTrue(db.sounds(List.of("1")).isEmpty());assertTrue(db.greets("100").isEmpty());
        assertTrue(db.read("SELECT * FROM sound_history WHERE sound_id=1").isEmpty());assertTrue(db.read("SELECT * FROM sound_interactions WHERE sound_id=1").isEmpty());
    }
    @Test public void collectionAnalyticsReadTenThousandEventsOnceOnOneConnection() throws Exception {
        for (int id = 4; id <= 62; id++) sql("INSERT INTO sound(id,name,guild_id,user_id,extension,public) VALUES("+id+",'s"+id+"','10','100','mp3',TRUE)");
        sql("INSERT INTO sound_history(sound_id,user_id,guild_id,time,source) SELECT CASE WHEN MOD(X,60)=0 THEN 1 ELSE MOD(X,60)+3 END,'100','10',CURRENT_TIMESTAMP,0 FROM SYSTEM_RANGE(1,10000)");
        connectionOpens.set(0); queries.clear();
        var guild = JSON.valueToTree(cache.guildAnalytics(viewer,"10",period()));
        assertEquals(10000,guild.at("/summary/plays").asInt()); assertEquals(60,guild.get("sounds").size());
        assertEquals(1,connectionOpens.get());
        assertEquals(1,queries.stream().filter(query -> query.contains("FROM sound_history")).count());
        assertEquals(3,queries.size());
        connectionOpens.set(0); queries.clear();
        var mine = JSON.valueToTree(cache.myAnalytics(viewer,period()));
        assertEquals(10000,mine.at("/summary/plays").asInt());
        assertEquals(1,connectionOpens.get());
        assertEquals(1,queries.stream().filter(query -> query.contains("FROM sound_history")).count());
        connectionOpens.set(0); queries.clear();
        cache.guildSounds(viewer,"10",period());
        assertEquals(1,connectionOpens.get());
        assertEquals(1,queries.stream().filter(query -> query.contains("JOIN tag t")).count());
    }
    @Test public void httpRoutesValidateAndReturnCanonicalSound()throws Exception{
        var response=mvc.perform(get("/api/beebot/sounds/1").servletPath("/api/beebot/sounds/1")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
        assertEquals("1",JSON.readTree(response.getContentAsString()).path("id").asText());
        mvc.perform(get("/api/beebot/sounds").servletPath("/api/beebot/sounds")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/beebot/sounds").servletPath("/api/beebot/sounds").param("q","air").param("limit","bad")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/beebot/guilds/20/sounds").servletPath("/api/beebot/guilds/20/sounds")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/beebot/sounds/1").servletPath("/api/beebot/sounds/1").contentType("application/json").content("{" )).andExpect(status().isBadRequest());
        var mine=mvc.perform(get("/api/beebot/sounds/me").servletPath("/api/beebot/sounds/me")).andExpect(status().isOk()).andReturn().getResponse();
        assertEquals(2,JSON.readTree(mine.getContentAsString()).at("/counts/all").asInt());
    }
}
