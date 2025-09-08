package com.safjnest.spring.api.repository.lol;

import com.safjnest.spring.api.model.lol.Summoner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SummonerRepository extends JpaRepository<Summoner, Integer> {

    List<Summoner> findByUserIdOrderById(String userId);
    
    Optional<Summoner> findByPuuidAndLeagueShard(String puuid, Integer leagueShard);
    
    List<Summoner> findByPuuid(String puuid);
    
    @Query(value = "SELECT * FROM summoner s WHERE MATCH(s.riot_id) AGAINST(CONCAT('+', :query, '*') IN BOOLEAN MODE) AND s.league_shard = :leagueShard LIMIT 25", nativeQuery = true)
    List<Summoner> findFocusedSummoners(@Param("query") String query, @Param("leagueShard") Integer leagueShard);

    @Query("SELECT s FROM Summoner s WHERE s.tracking = 1")
    List<Summoner> findTrackingSummoners();
}