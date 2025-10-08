package com.safjnest.util.lol.api;

import com.safjnest.util.lol.api.spring.SummonerDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SummonerRepository extends JpaRepository<SummonerDTO, Integer> {
    Optional<SummonerDTO> findByRiotId(String riotId);
    Optional<SummonerDTO> findByPuuid(String puuid);
    
    @EntityGraph(attributePaths = {"masteries"})
    Optional<SummonerDTO> findWithMasteriesByPuuid(String puuid);
    
    @EntityGraph(attributePaths = {"ranks"})
    Optional<SummonerDTO> findWithRanksByPuuid(String puuid);
}