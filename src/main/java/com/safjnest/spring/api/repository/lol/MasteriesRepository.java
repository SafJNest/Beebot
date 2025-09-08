package com.safjnest.spring.api.repository.lol;

import com.safjnest.spring.api.model.lol.Masteries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasteriesRepository extends JpaRepository<Masteries, Integer> {

    List<Masteries> findBySummonerId(Integer summonerId);
    
    Optional<Masteries> findBySummonerIdAndChampionId(Integer summonerId, Integer championId);
    
    List<Masteries> findBySummonerIdOrderByChampionPointsDesc(Integer summonerId);
    
    List<Masteries> findBySummonerIdOrderByChampionLevelDesc(Integer summonerId);
    
    @Modifying
    @Query("DELETE FROM Masteries m WHERE m.summoner.id = :summonerId")
    void deleteBySummonerId(@Param("summonerId") Integer summonerId);
}