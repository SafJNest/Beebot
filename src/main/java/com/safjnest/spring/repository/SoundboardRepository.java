package com.safjnest.spring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.safjnest.spring.entity.SoundboardEntity;

@Repository
public interface SoundboardRepository extends JpaRepository<SoundboardEntity, Long> {
    
    List<SoundboardEntity> findByGuildIdOrUserId(String guildId, String userId);
    
    Optional<SoundboardEntity> findByNameAndGuildId(String name, String guildId);
    
    @Query("SELECT s FROM SoundboardEntity s WHERE s.guildId = :guildId OR s.userId = :userId ORDER BY RAND() LIMIT 25")
    List<SoundboardEntity> findRandomByGuildIdOrUserId(@Param("guildId") String guildId, @Param("userId") String userId);
    
    @Query("SELECT s FROM SoundboardEntity s WHERE s.name LIKE :name% AND (s.guildId = :guildId OR s.userId = :userId) ORDER BY RAND() LIMIT 25")
    List<SoundboardEntity> findByNameLikeAndGuildIdOrUserId(@Param("name") String name, @Param("guildId") String guildId, @Param("userId") String userId);
    
    boolean existsByIdAndGuildIdOrUserId(Long id, String guildId, String userId);
}