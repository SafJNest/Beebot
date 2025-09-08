package com.safjnest.spring.api.repository.lol;

import com.safjnest.spring.api.model.lol.Match;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<Match, Integer> {

    Optional<Match> findByGameId(String gameId);
    
    Optional<Match> findByGameIdAndLeagueShard(String gameId, Integer leagueShard);
    
    List<Match> findByGameType(Integer gameType);
    
    List<Match> findByTimeStartBetween(LocalDateTime start, LocalDateTime end);
    
    @Query("SELECT m FROM Match m WHERE m.timeStart >= :timeStart AND m.timeEnd <= :timeEnd ORDER BY m.timeStart")
    List<Match> findAllMatchesInTimeRange(@Param("timeStart") LocalDateTime timeStart, @Param("timeEnd") LocalDateTime timeEnd);
}