package com.safjnest.spring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.safjnest.spring.entity.SummonerEntity;

@Repository
public interface SummonerRepository extends JpaRepository<SummonerEntity, Long> {
    
    List<SummonerEntity> findByUserId(String userId);
    
    Optional<SummonerEntity> findByPuuidAndLeagueShard(String puuid, Integer leagueShard);
    
    Optional<SummonerEntity> findByPuuid(String puuid);
    
    List<SummonerEntity> findByTrackingTrue();
    
    @Query("SELECT s FROM SummonerEntity s WHERE s.riotId LIKE %:query% AND s.leagueShard = :shard")
    List<SummonerEntity> searchByRiotIdAndShard(@Param("query") String query, @Param("shard") Integer shard);
}