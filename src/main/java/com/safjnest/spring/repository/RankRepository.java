package com.safjnest.spring.repository;

import com.safjnest.spring.entity.Rank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RankRepository extends JpaRepository<Rank, Integer> {

    // Basic finder methods
    List<Rank> findBySummonerId(Integer summonerId);
    
    List<Rank> findByGameType(Integer gameType);
    
    Optional<Rank> findBySummonerIdAndGameType(Integer summonerId, Integer gameType);

    // Order by rank or LP
    List<Rank> findBySummonerIdOrderByRankDesc(Integer summonerId);
    
    List<Rank> findBySummonerIdOrderByLpDesc(Integer summonerId);

    // updateSummonerEntries - equivalent functionality would be handled by save/saveAll
    // since JPA repositories handle INSERT ... ON DUPLICATE KEY UPDATE automatically

    // Get ranks above certain LP
    @Query("SELECT r FROM Rank r WHERE r.summoner.id = :summonerId AND r.lp >= :minLp")
    List<Rank> findBySummonerIdAndMinLp(@Param("summonerId") Integer summonerId, 
                                        @Param("minLp") Integer minLp);

    // Get ranks by win/loss ratio
    @Query("SELECT r FROM Rank r WHERE r.summoner.id = :summonerId AND (r.wins * 100.0 / (r.wins + r.losses)) >= :minWinRate")
    List<Rank> findBySummonerIdAndMinWinRate(@Param("summonerId") Integer summonerId, 
                                             @Param("minWinRate") Double minWinRate);

    // Delete all ranks for a summoner
    void deleteBySummonerId(Integer summonerId);

    // Find top ranks across all summoners for a specific game type
    @Query("SELECT r FROM Rank r WHERE r.gameType = :gameType ORDER BY r.rank DESC, r.lp DESC")
    List<Rank> findTopRanksByGameType(@Param("gameType") Integer gameType);
}