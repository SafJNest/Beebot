package com.safjnest.spring.api.repository.lol;

import com.safjnest.spring.api.model.lol.Participant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Integer> {

    Optional<Participant> findBySummonerIdAndMatchId(Integer summonerId, Integer matchId);
    
    List<Participant> findBySummonerId(Integer summonerId);
    
    @Query("SELECT p FROM Participant p JOIN p.match m WHERE p.summoner.id = :summonerId AND m.gameId = :gameId")
    Optional<Participant> findBySummonerIdAndGameId(@Param("summonerId") Integer summonerId, @Param("gameId") String gameId);
    
    @Query("SELECT p FROM Participant p JOIN p.match m WHERE p.summoner.id = :summonerId AND p.summoner.leagueShard = :leagueShard AND m.timeStart >= :timeStart AND m.timeEnd <= :timeEnd")
    List<Participant> findBySummonerIdAndTimeRange(@Param("summonerId") Integer summonerId, @Param("leagueShard") Integer leagueShard, @Param("timeStart") LocalDateTime timeStart, @Param("timeEnd") LocalDateTime timeEnd);
    
    @Query("SELECT p.champion, COUNT(*) AS games, SUM(CASE WHEN p.win = true THEN 1 ELSE 0 END) AS wins, " +
           "SUM(CASE WHEN p.win = false THEN 1 ELSE 0 END) AS losses, " +
           "AVG(CAST(SUBSTRING(p.kda, 1, LOCATE('/', p.kda) - 1) AS UNSIGNED)) AS avgKills, " +
           "AVG(CAST(SUBSTRING(p.kda, LOCATE('/', p.kda) + 1, LOCATE('/', p.kda, LOCATE('/', p.kda) + 1) - LOCATE('/', p.kda) - 1) AS UNSIGNED)) AS avgDeaths, " +
           "AVG(CAST(SUBSTRING(p.kda, LOCATE('/', p.kda, LOCATE('/', p.kda) + 1) + 1) AS UNSIGNED)) AS avgAssists, " +
           "SUM(p.gain) AS totalLpGain " +
           "FROM Participant p WHERE p.summoner.id = :summonerId GROUP BY p.champion ORDER BY games DESC")
    List<Object[]> getAdvancedLOLData(@Param("summonerId") Integer summonerId);
    
    @Query("SELECT p.champion, COUNT(*) AS games, SUM(CASE WHEN p.win = true THEN 1 ELSE 0 END) AS wins, " +
           "SUM(CASE WHEN p.win = false THEN 1 ELSE 0 END) AS losses, " +
           "AVG(CAST(SUBSTRING(p.kda, 1, LOCATE('/', p.kda) - 1) AS UNSIGNED)) AS avgKills, " +
           "AVG(CAST(SUBSTRING(p.kda, LOCATE('/', p.kda) + 1, LOCATE('/', p.kda, LOCATE('/', p.kda) + 1) - LOCATE('/', p.kda) - 1) AS UNSIGNED)) AS avgDeaths, " +
           "AVG(CAST(SUBSTRING(p.kda, LOCATE('/', p.kda, LOCATE('/', p.kda) + 1) + 1) AS UNSIGNED)) AS avgAssists, " +
           "SUM(p.gain) AS totalLpGain " +
           "FROM Participant p JOIN p.match m WHERE p.summoner.id = :summonerId " +
           "AND (:timeStart IS NULL OR m.timeStart >= :timeStart) " +
           "AND (:timeEnd IS NULL OR m.timeEnd <= :timeEnd) " +
           "AND (:gameType IS NULL OR m.gameType = :gameType) " +
           "GROUP BY p.champion ORDER BY games DESC")
    List<Object[]> getAdvancedLOLDataWithFilters(@Param("summonerId") Integer summonerId, 
                                                 @Param("timeStart") LocalDateTime timeStart, 
                                                 @Param("timeEnd") LocalDateTime timeEnd, 
                                                 @Param("gameType") Integer gameType);

    @Query("SELECT m.gameId, m.gameType, p.win FROM Participant p JOIN p.match m WHERE p.summoner.id = :summonerId " +
           "AND (:timeStart IS NULL OR m.timeStart >= :timeStart) " +
           "AND (:timeEnd IS NULL OR m.timeEnd <= :timeEnd)")
    List<Object[]> getAllGamesForAccount(@Param("summonerId") Integer summonerId, 
                                        @Param("timeStart") LocalDateTime timeStart, 
                                        @Param("timeEnd") LocalDateTime timeEnd);

    boolean existsBySummonerId(Integer summonerId);
}