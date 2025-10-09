package com.safjnest.util.lol.api;

import com.safjnest.util.lol.api.spring.ParticipantDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ParticipantRepository extends JpaRepository<ParticipantDTO, Integer> {
    
    @Query("SELECT p FROM ParticipantDTO p JOIN p.summoner s WHERE s.puuid = :puuid ORDER BY p.match.timeStart DESC")
    List<ParticipantDTO> findBySummonerPuuid(String puuid, Pageable pageable);
    
    @Query("SELECT p FROM ParticipantDTO p JOIN p.summoner s WHERE s.puuid = :puuid AND p.win = true")
    List<ParticipantDTO> findWinsBySummonerPuuid(String puuid, Pageable pageable);
}