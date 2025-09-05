package com.safjnest.spring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.safjnest.spring.entity.SoundEntity;

@Repository
public interface SoundRepository extends JpaRepository<SoundEntity, Long> {
    
    List<SoundEntity> findByUserId(String userId);
    
    List<SoundEntity> findByGuildId(String guildId);
    
    @Query("SELECT s FROM SoundEntity s WHERE s.userId = :userId OR s.isPublic = true")
    Page<SoundEntity> findByUserIdOrPublic(@Param("userId") String userId, Pageable pageable);
    
    @Query("SELECT s FROM SoundEntity s WHERE s.guildId = :guildId ORDER BY RAND() LIMIT 25")
    List<SoundEntity> findRandomByGuildId(@Param("guildId") String guildId);
    
    @Query("SELECT s FROM SoundEntity s WHERE s.name LIKE :name% AND s.guildId = :guildId ORDER BY RAND() LIMIT 25")
    List<SoundEntity> findByNameLikeAndGuildId(@Param("name") String name, @Param("guildId") String guildId);
    
    Optional<SoundEntity> findByIdAndGuildId(Long id, String guildId);
    
    Optional<SoundEntity> findByNameAndGuildId(String name, String guildId);
    
    @Query("SELECT s FROM SoundEntity s WHERE (s.guildId = :guildId OR s.isPublic = true OR s.userId = :userId) AND s.id = :id")
    Optional<SoundEntity> findAccessibleSound(@Param("id") Long id, @Param("guildId") String guildId, @Param("userId") String userId);
}