package com.safjnest.spring.repository;

import com.safjnest.spring.entity.Participant;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ParticipantRepository extends JpaRepository<Participant, Integer> {

    // Basic finder methods
    List<Participant> findBySummonerId(Integer summonerId);
    
    List<Participant> findByChampion(Short champion);
    
    List<Participant> findByWin(Boolean win);

    // getSummonerData - Basic summoner data by ID
    @Query("SELECT p.summoner.id, p.match.gameId, p.rank, p.lp, p.gain, p.win, p.match.timeStart, p.match.timeEnd, p.match.patch " +
           "FROM Participant p WHERE p.summoner.id = :summonerId AND p.match.gameType = :gameType ORDER BY p.match.gameId")
    List<Object[]> findSummonerDataByGameType(@Param("summonerId") Integer summonerId, @Param("gameType") Integer gameType);

    // getSummonerData with game_id
    @Query("SELECT p.summoner.id, p.match.gameId, p.rank, p.lp, p.gain, p.win, p.match.timeStart, p.match.patch " +
           "FROM Participant p WHERE p.summoner.id = :summonerId AND p.match.gameId = :gameId")
    List<Object[]> findSummonerDataByGameId(@Param("summonerId") Integer summonerId, @Param("gameId") String gameId);

    // getSummonerData with time range and shard
    @Query("SELECT p.summoner.id, p.match.gameId, p.rank, p.lp, p.gain, p.win, p.match.timeStart, p.match.timeEnd, p.match.patch " +
           "FROM Participant p WHERE p.summoner.id = :summonerId AND p.match.leagueShard = :shard " +
           "AND p.match.timeStart >= :timeStart AND p.match.timeEnd <= :timeEnd")
    List<Object[]> findSummonerDataByTimeRange(@Param("summonerId") Integer summonerId, 
                                               @Param("shard") Integer shard,
                                               @Param("timeStart") LocalDateTime timeStart, 
                                               @Param("timeEnd") LocalDateTime timeEnd);

    // getAdvancedLOLData - Complex aggregation query (simplified for H2 compatibility)
    @Query("SELECT p.champion, COUNT(*) as games, SUM(CASE WHEN p.win = true THEN 1 ELSE 0 END) as wins, " +
           "SUM(CASE WHEN p.win = false THEN 1 ELSE 0 END) as losses, " +
           "SUM(p.gain) as totalLpGain " +
           "FROM Participant p WHERE p.summoner.id = :summonerId " +
           "GROUP BY p.champion ORDER BY games DESC")
    List<Object[]> findAdvancedLolDataBySummoner(@Param("summonerId") Integer summonerId);

    // getAdvancedLOLData with time range and queue filter (simplified for H2 compatibility)
    @Query("SELECT p.champion, COUNT(*) as games, SUM(CASE WHEN p.win = true THEN 1 ELSE 0 END) as wins, " +
           "SUM(CASE WHEN p.win = false THEN 1 ELSE 0 END) as losses, " +
           "SUM(p.gain) as totalLpGain " +
           "FROM Participant p WHERE p.summoner.id = :summonerId " +
           "AND (:gameType IS NULL OR p.match.gameType = :gameType) " +
           "AND (:timeStart IS NULL OR p.match.timeStart >= :timeStart) " +
           "AND (:timeEnd IS NULL OR p.match.timeEnd <= :timeEnd) " +
           "GROUP BY p.champion ORDER BY games DESC")
    List<Object[]> findAdvancedLolDataWithFilters(@Param("summonerId") Integer summonerId,
                                                   @Param("gameType") Integer gameType,
                                                   @Param("timeStart") LocalDateTime timeStart,
                                                   @Param("timeEnd") LocalDateTime timeEnd);

    // getAllGamesForAccount
    @Query("SELECT p.match.gameId, p.match.gameType, p.win FROM Participant p " +
           "WHERE p.summoner.id = :summonerId " +
           "AND (:timeStart IS NULL OR p.match.timeStart >= :timeStart) " +
           "AND (:timeEnd IS NULL OR p.match.timeEnd <= :timeEnd)")
    List<Object[]> findAllGamesForAccount(@Param("summonerId") Integer summonerId,
                                          @Param("timeStart") LocalDateTime timeStart,
                                          @Param("timeEnd") LocalDateTime timeEnd);

    // Complex query for getRegistredLolAccount - latest ranked game data (simplified for H2)
    @Query("SELECT s.puuid, s.leagueShard, p.match.gameId, p.rank, p.lp, p.match.timeStart " +
           "FROM Summoner s LEFT JOIN Participant p ON s.id = p.summoner.id " +
           "WHERE s.tracking = 1 AND p.match.timeStart >= :timeStart AND p.match.gameType = 43 " +
           "ORDER BY s.id, p.match.timeStart DESC")
    List<Object[]> findRegisteredLolAccountsWithLatestData(@Param("timeStart") LocalDateTime timeStart);

    // Single summoner version of getRegistredLolAccount (simplified for H2)
    @Query("SELECT s.puuid, s.leagueShard, p.match.gameId, p.rank, p.lp, p.match.timeStart " +
           "FROM Summoner s LEFT JOIN Participant p ON s.id = p.summoner.id " +
           "WHERE s.tracking = 1 AND s.id = :summonerId AND p.match.timeStart >= :timeStart AND p.match.gameType = 43 " +
           "ORDER BY p.match.timeStart DESC")
    List<Object[]> findRegisteredLolAccountWithLatestData(@Param("summonerId") Integer summonerId, 
                                                           @Param("timeStart") LocalDateTime timeStart);

    // Check if participant exists for summoner and match
    boolean existsBySummonerIdAndMatchId(Integer summonerId, Integer matchId);
}