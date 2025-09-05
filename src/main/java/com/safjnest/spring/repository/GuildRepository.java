package com.safjnest.spring.repository;

import com.safjnest.spring.entity.Guild;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuildRepository extends JpaRepository<Guild, String> {
    
    Optional<Guild> findByGuildId(String guildId);
    
    @Query("SELECT g FROM Guild g WHERE g.blacklistEnabled = true AND g.threshold <= :threshold AND g.blacklistChannel IS NOT NULL AND g.guildId != :excludeGuildId")
    List<Guild> findByThresholdAndBlacklistEnabled(@Param("threshold") int threshold, @Param("excludeGuildId") String excludeGuildId);
    
    @Query("SELECT g FROM Guild g WHERE g.expEnabled = true")
    List<Guild> findByExpEnabled();
}