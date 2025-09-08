package com.safjnest.spring.api.repository.lol;

import com.safjnest.spring.api.model.lol.Rank;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RankRepository extends JpaRepository<Rank, Integer> {

    List<Rank> findBySummonerId(Integer summonerId);
    
    Optional<Rank> findBySummonerIdAndGameType(Integer summonerId, Integer gameType);
    
    List<Rank> findByGameType(Integer gameType);
    
    @Query("SELECT r FROM Rank r WHERE r.rank >= :minRank ORDER BY r.rank DESC, r.lp DESC")
    List<Rank> findHighRankedPlayers(@Param("minRank") Integer minRank);
}