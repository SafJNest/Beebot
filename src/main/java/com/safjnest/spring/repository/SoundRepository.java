package com.safjnest.spring.repository;

import com.safjnest.spring.entity.Sound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SoundRepository extends JpaRepository<Sound, Integer> {
    
    Optional<Sound> findById(Integer id);
    
    List<Sound> findByName(String name);
    
    @Query("SELECT s FROM Sound s WHERE s.userId = :userId OR s.isPublic = true ORDER BY s.id ASC")
    Page<Sound> findByUserIdOrPublic(@Param("userId") String userId, Pageable pageable);
    
    @Query("SELECT s FROM Sound s WHERE s.guildId = :guildId ORDER BY s.name ASC")
    List<Sound> findByGuildIdOrderByName(@Param("guildId") String guildId);
    
    @Query("SELECT s FROM Sound s WHERE s.guildId = :guildId ORDER BY RAND() LIMIT 25")
    List<Sound> findRandomByGuildId(@Param("guildId") String guildId);
    
    @Query("SELECT s FROM Sound s WHERE s.userId = :userId")
    List<Sound> findByUserId(@Param("userId") String userId);
    
    @Query("SELECT s FROM Sound s WHERE s.userId = :userId AND (s.guildId = :guildId OR s.isPublic = true)")
    List<Sound> findByUserIdAndGuildIdOrPublic(@Param("userId") String userId, @Param("guildId") String guildId);
    
    @Query("SELECT s FROM Sound s WHERE s.name LIKE :name% AND s.guildId = :guildId ORDER BY RAND() LIMIT 25")
    List<Sound> findByNameStartingWithAndGuildId(@Param("name") String name, @Param("guildId") String guildId);
    
    @Query("SELECT s FROM Sound s WHERE (s.name LIKE :query% OR CAST(s.id AS string) LIKE :query%) AND s.userId = :userId ORDER BY RAND() LIMIT 25")
    List<Sound> findByNameOrIdAndUserId(@Param("query") String query, @Param("userId") String userId);
    
    @Query("SELECT s FROM Sound s WHERE s.id = :id AND (s.guildId = :guildId OR s.isPublic = true OR s.userId = :authorId)")
    Optional<Sound> findByIdAndGuildIdOrPublicOrAuthor(@Param("id") Integer id, @Param("guildId") String guildId, @Param("authorId") String authorId);
    
    @Query("SELECT s FROM Sound s WHERE s.name = :name AND (s.guildId = :guildId OR s.isPublic = true OR s.userId = :authorId)")
    List<Sound> findByNameAndGuildIdOrPublicOrAuthor(@Param("name") String name, @Param("guildId") String guildId, @Param("authorId") String authorId);
    
    @Query("SELECT COUNT(s) FROM Sound s WHERE s.userId = :userId")
    Long countByUserId(@Param("userId") String userId);
    
    @Query("SELECT COUNT(s) FROM Sound s WHERE s.userId = :userId AND s.guildId = :guildId")
    Long countByUserIdAndGuildId(@Param("userId") String userId, @Param("guildId") String guildId);
}