package com.safjnest.util.lol;

import com.safjnest.util.lol.entity.SummonerDTO;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SummonerRepository extends JpaRepository<SummonerDTO, Integer> {

    @EntityGraph(attributePaths = {"ranks", "masteries"})
    Optional<SummonerDTO> findById(Integer id);

    @EntityGraph(attributePaths = {"ranks", "masteries"})
    Optional<SummonerDTO> findByPuuid(String puuid);
}
