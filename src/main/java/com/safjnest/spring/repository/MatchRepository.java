package com.safjnest.spring.repository;

import com.safjnest.spring.entity.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Integer> {

    // Basic finder methods
    Optional<Match> findByGameIdAndLeagueShard(String gameId, Integer leagueShard);
    
    List<Match> findByGameType(Integer gameType);
    
    List<Match> findByTimeStartBetween(LocalDateTime start, LocalDateTime end);

    // getMatchData - Complex join with participant data where match id > 10353
    @Query("SELECT m.id, m.gameId, m.leagueShard, m.gameType, m.bans, m.timeStart, m.timeEnd, m.patch, " +
           "p.summoner.accountId, p.win, p.kda, p.rank, p.lp, p.gain, p.champion, p.lane, p.team, p.build " +
           "FROM Match m JOIN m.participants p WHERE m.id > :minId")
    List<Object[]> findMatchDataWithParticipants(@Param("minId") Integer minId);

    // setMatchEvent - Update events field
    @Modifying
    @Transactional
    @Query("UPDATE Match m SET m.events = :json WHERE m.id = :matchId")
    int updateMatchEvents(@Param("matchId") Integer matchId, @Param("json") String json);

    // For matches by time range
    @Query("SELECT m FROM Match m WHERE m.timeStart >= :startTime AND m.timeEnd <= :endTime")
    List<Match> findByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    // Check if match exists
    boolean existsByGameIdAndLeagueShard(String gameId, Integer leagueShard);
}