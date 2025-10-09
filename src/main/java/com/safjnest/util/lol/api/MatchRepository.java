package com.safjnest.util.lol.api;

import com.safjnest.util.lol.api.spring.MatchDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MatchRepository extends JpaRepository<MatchDTO, Integer> {
    
    Optional<MatchDTO> findByGameId(String gameId);
    
    @Query("SELECT m FROM MatchDTO m JOIN m.participants p JOIN p.summoner s WHERE s.puuid = :puuid ORDER BY m.timeStart DESC")
    List<MatchDTO> findBySummonerPuuid(@Param("puuid") String puuid, Pageable pageable);
    
    @Query("SELECT m FROM MatchDTO m JOIN m.participants p JOIN p.summoner s WHERE s.puuid = :puuid AND m.queue = :queue ORDER BY m.timeStart DESC")
    List<MatchDTO> findBySummonerPuuidAndQueue(@Param("puuid") String puuid, @Param("queue") String queue, Pageable pageable);
}