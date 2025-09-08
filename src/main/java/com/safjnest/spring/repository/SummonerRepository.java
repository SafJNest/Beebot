package com.safjnest.spring.repository;

import com.safjnest.spring.entity.Summoner;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface SummonerRepository extends JpaRepository<Summoner, Integer> {

    // Basic finder methods
    List<Summoner> findByUserIdOrderById(String userId);
    
    Optional<Summoner> findByUserIdAndPuuid(String userId, String puuid);
    
    Optional<Summoner> findByPuuidAndLeagueShard(String puuid, Integer leagueShard);
    
    List<Summoner> findByPuuid(String puuid);
    
    List<Summoner> findByTracking(Integer tracking);

    // getFocusedSummoners - Full-text search equivalent using LIKE
    @Query("SELECT s.riotId FROM Summoner s WHERE s.riotId LIKE CONCAT('%', :query, '%') AND s.leagueShard = :shard")
    List<String> findFocusedSummoners(@Param("query") String query, @Param("shard") Integer shard, Pageable pageable);

    // getUserIdByLOLAccountId
    @Query("SELECT s.userId FROM Summoner s WHERE s.puuid = :puuid AND s.leagueShard = :shard")
    Optional<String> findUserIdByPuuidAndLeagueShard(@Param("puuid") String puuid, @Param("shard") Integer shard);

    // getSummonerIdByPuuid
    @Query("SELECT s.id FROM Summoner s WHERE s.puuid = :puuid AND s.leagueShard = :shard")
    Optional<Integer> findSummonerIdByPuuidAndLeagueShard(@Param("puuid") String puuid, @Param("shard") Integer shard);

    // hasSummonerData - check if summoner has participant data
    @Query("SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END FROM Summoner s JOIN s.participants p WHERE s.id = :summonerId")
    boolean hasSummonerData(@Param("summonerId") Integer summonerId);

    // trackSummoner update method
    @Modifying
    @Transactional
    @Query("UPDATE Summoner s SET s.tracking = :tracking WHERE s.userId = :userId AND s.puuid = :puuid")
    int updateTracking(@Param("userId") String userId, @Param("puuid") String puuid, @Param("tracking") Integer tracking);

    // deleteLOLaccount - soft delete by setting tracking to 0 and userId to null
    @Modifying
    @Transactional
    @Query("UPDATE Summoner s SET s.tracking = 0, s.userId = null WHERE s.userId = :userId AND s.puuid = :puuid")
    int deleteLolAccount(@Param("userId") String userId, @Param("puuid") String puuid);
}