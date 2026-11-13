package com.safjnest.sound;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import com.safjnest.sql.database.SoundDB;

public class SoundVolumeTest {
    @Test public void aggregatesOneMillionPlaysWithoutReturningEvents() throws Exception {
        String url="jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MariaDB";
        try(Connection anchor=DriverManager.getConnection(url);Statement statement=anchor.createStatement()) {
            statement.execute("SET TIME ZONE 'UTC'");
            statement.execute("CREATE TABLE sound_history(id INT AUTO_INCREMENT PRIMARY KEY,sound_id INT,user_id VARCHAR(19),guild_id VARCHAR(19),time TIMESTAMP,source INT)");
            statement.execute("INSERT INTO sound_history(sound_id,user_id,guild_id,time,source) SELECT 1,CAST(MOD(X,1000) AS VARCHAR),CASE WHEN X<=500000 THEN '10' ELSE '20' END,DATEADD('SECOND',-MOD(X,86400),CURRENT_TIMESTAMP),MOD(X,4) FROM SYSTEM_RANGE(1,1000000)");
            statement.execute("CREATE INDEX sound_history_sound_time ON sound_history(sound_id,time)");
            statement.execute("CREATE INDEX sound_history_guild_time_sound ON sound_history(guild_id,time,sound_id)");
            var database=new SoundDB(()->{try{return DriverManager.getConnection(url);}catch(SQLException e){throw new IllegalStateException(e);}});
            Instant end=Instant.now().plusSeconds(1),start=end.minusSeconds(172800);
            long began=System.nanoTime();
            com.safjnest.sql.QueryRecord sound = new com.safjnest.sql.QueryRecord(); sound.put("id","1");
            var service = new SoundAnalytics(database, new SoundAccess() {
                @Override public Map<String,Object> user(String id) { return SoundValues.map("id",id,"name",id,"avatarUrl",null); }
            });
            var result = service.sound(sound,"0",null,new SoundPeriod("all",java.time.ZoneId.of("Europe/Rome"),null,end,null,null),Set.of("10"));
            var json = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);
            assertEquals(1000000,json.at("/totals/plays").asLong());
            assertEquals(1000,json.at("/totals/listeners").asInt());
            assertEquals(500000,json.at("/otherGuilds/plays").asInt());
            assertEquals(100,json.at("/my/history").size());
            var plan=database.read("EXPLAIN SELECT COUNT(*) FROM sound_history WHERE sound_id=? AND time>=? AND time<?",1,Timestamp.from(start),Timestamp.from(end));
            assertTrue(plan.toString().toUpperCase(Locale.ROOT).contains("SOUND_HISTORY_SOUND_TIME"));
            System.out.println("Sound Java analytics volume check: 1,000,000 H2 rows, 1,000 distinct users, "+((System.nanoTime()-began)/1000000)+" ms (not a MariaDB benchmark)");
        }
    }
}
