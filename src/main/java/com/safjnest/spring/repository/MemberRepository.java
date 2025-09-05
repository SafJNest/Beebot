package com.safjnest.spring.repository;

import com.safjnest.spring.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MemberRepository extends JpaRepository<Member, Integer> {
    
    Optional<Member> findByUserIdAndGuildId(String userId, String guildId);
    
    @Query("SELECT m FROM Member m WHERE m.guildId = :guildId ORDER BY m.experience DESC")
    List<Member> findByGuildIdOrderByExperienceDesc(@Param("guildId") String guildId);
    
    @Query("SELECT m FROM Member m WHERE m.guildId = :guildId ORDER BY m.experience DESC")
    List<Member> findByGuildIdOrderByExperienceDesc(@Param("guildId") String guildId, Pageable pageable);
}