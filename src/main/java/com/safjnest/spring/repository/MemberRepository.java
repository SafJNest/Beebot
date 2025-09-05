package com.safjnest.spring.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.safjnest.spring.entity.MemberEntity;

@Repository
public interface MemberRepository extends JpaRepository<MemberEntity, Long> {
    
    Optional<MemberEntity> findByGuildIdAndUserId(String guildId, String userId);
    
    List<MemberEntity> findByGuildId(String guildId);
    
    @Query("SELECT m FROM MemberEntity m WHERE m.guildId = :guildId ORDER BY m.experience DESC")
    List<MemberEntity> findByGuildIdOrderByExperienceDesc(@Param("guildId") String guildId);
    
    @Query("SELECT m FROM MemberEntity m WHERE m.guildId = :guildId ORDER BY m.experience DESC LIMIT :limit")
    List<MemberEntity> findTopByExperience(@Param("guildId") String guildId, @Param("limit") int limit);
}