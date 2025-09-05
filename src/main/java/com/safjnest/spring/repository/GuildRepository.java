package com.safjnest.spring.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.safjnest.spring.entity.GuildEntity;

@Repository
public interface GuildRepository extends JpaRepository<GuildEntity, String> {
    
    Optional<GuildEntity> findByGuildId(String guildId);
}