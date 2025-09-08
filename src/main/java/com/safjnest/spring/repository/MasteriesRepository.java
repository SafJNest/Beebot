package com.safjnest.spring.repository;

import com.safjnest.spring.entity.Masteries;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MasteriesRepository extends JpaRepository<Masteries, Integer> {

    // Basic finder methods
    List<Masteries> findBySummonerId(Integer summonerId);
    
    List<Masteries> findByChampionId(Integer championId);
    
    Optional<Masteries> findBySummonerIdAndChampionId(Integer summonerId, Integer championId);

    // Order by champion level or points
    List<Masteries> findBySummonerIdOrderByChampionLevelDesc(Integer summonerId);
    
    List<Masteries> findBySummonerIdOrderByChampionPointsDesc(Integer summonerId);

    // updateSummonerMasteries - equivalent functionality would be handled by save/saveAll
    // since JPA repositories handle INSERT ... ON DUPLICATE KEY UPDATE automatically

    // Get masteries above certain level
    @Query("SELECT m FROM Masteries m WHERE m.summoner.id = :summonerId AND m.championLevel >= :minLevel")
    List<Masteries> findBySummonerIdAndMinLevel(@Param("summonerId") Integer summonerId, 
                                                 @Param("minLevel") Integer minLevel);

    // Get masteries above certain points
    @Query("SELECT m FROM Masteries m WHERE m.summoner.id = :summonerId AND m.championPoints >= :minPoints")
    List<Masteries> findBySummonerIdAndMinPoints(@Param("summonerId") Integer summonerId, 
                                                  @Param("minPoints") Integer minPoints);

    // Delete all masteries for a summoner
    void deleteBySummonerId(Integer summonerId);
}