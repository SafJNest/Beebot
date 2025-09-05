package com.safjnest.spring.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.safjnest.spring.entity.MemberEntity;
import com.safjnest.spring.repository.MemberRepository;

@Service
@Transactional
public class MemberService {

    @Autowired
    private MemberRepository memberRepository;

    @Cacheable(value = "members", key = "#guildId + ':' + #userId")
    public MemberEntity getMember(String guildId, String userId) {
        return memberRepository.findByGuildIdAndUserId(guildId, userId).orElse(null);
    }

    @Cacheable(value = "members", key = "#guildId + ':' + #userId")
    public MemberEntity getMemberOrCreate(String guildId, String userId) {
        Optional<MemberEntity> member = memberRepository.findByGuildIdAndUserId(guildId, userId);
        if (member.isPresent()) {
            return member.get();
        }
        
        MemberEntity newMember = new MemberEntity(guildId, userId);
        return memberRepository.save(newMember);
    }

    @CacheEvict(value = "members", key = "#member.guildId + ':' + #member.userId")
    public MemberEntity saveMember(MemberEntity member) {
        return memberRepository.save(member);
    }

    @CacheEvict(value = "members", key = "#guildId + ':' + #userId")
    public boolean updateExperience(String guildId, String userId, int experience, int level, int messages) {
        Optional<MemberEntity> member = memberRepository.findByGuildIdAndUserId(guildId, userId);
        if (member.isPresent()) {
            MemberEntity entity = member.get();
            entity.setExperience(experience);
            entity.setLevel(level);
            entity.setMessages(messages);
            memberRepository.save(entity);
            return true;
        }
        return false;
    }

    public List<MemberEntity> getMembersByGuild(String guildId) {
        return memberRepository.findByGuildId(guildId);
    }

    public List<MemberEntity> getTopMembersByExperience(String guildId, int limit) {
        if (limit == 0) {
            return memberRepository.findByGuildIdOrderByExperienceDesc(guildId);
        }
        return memberRepository.findTopByExperience(guildId, limit);
    }
    
    // Method to get users by experience (compatible with BotDB.getUsersByExp)
    public List<MemberEntity> getUsersByExp(String guildId, int limit) {
        return getTopMembersByExperience(guildId, limit);
    }
}