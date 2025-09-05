package com.safjnest.spring.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.safjnest.spring.entity.Sound;
import com.safjnest.spring.entity.Member;
import com.safjnest.spring.repository.SoundRepository;
import com.safjnest.spring.repository.MemberRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Enhanced sound management service using Spring Data JPA.
 * This service demonstrates the new database integration approach.
 */
@Service
@Transactional
public class SoundManagementService {
    
    private final SoundRepository soundRepository;
    private final MemberRepository memberRepository;
    
    @Autowired
    public SoundManagementService(SoundRepository soundRepository, MemberRepository memberRepository) {
        this.soundRepository = soundRepository;
        this.memberRepository = memberRepository;
    }
    
    /**
     * Create a new sound using JPA entities
     */
    public Sound createSound(String name, String guildId, String userId, String extension, boolean isPublic) {
        Sound sound = new Sound();
        sound.setName(name);
        sound.setGuildId(guildId);
        sound.setUserId(userId);
        sound.setExtension(extension);
        sound.setIsPublic(isPublic);
        sound.setTime(LocalDateTime.now());
        sound.setPlays(0);
        sound.setLikes(0);
        sound.setDislikes(0);
        
        return soundRepository.save(sound);
    }
    
    /**
     * Get paginated sounds for a user with improved performance
     */
    public Page<Sound> getUserSounds(String userId, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit);
        return soundRepository.findByUserIdOrPublic(userId, pageable);
    }
    
    /**
     * Get sounds by guild with ordering
     */
    public List<Sound> getGuildSounds(String guildId) {
        return soundRepository.findByGuildIdOrderByName(guildId);
    }
    
    /**
     * Update sound statistics (plays, likes, dislikes)
     */
    public boolean updateSoundStats(Integer soundId, Integer plays, Integer likes, Integer dislikes) {
        Optional<Sound> soundOpt = soundRepository.findById(soundId);
        if (soundOpt.isPresent()) {
            Sound sound = soundOpt.get();
            if (plays != null) sound.setPlays(plays);
            if (likes != null) sound.setLikes(likes);
            if (dislikes != null) sound.setDislikes(dislikes);
            soundRepository.save(sound);
            return true;
        }
        return false;
    }
    
    /**
     * Delete a sound if user has permission
     */
    public boolean deleteSoundIfAuthorized(Integer soundId, String userId) {
        Optional<Sound> soundOpt = soundRepository.findById(soundId);
        if (soundOpt.isPresent()) {
            Sound sound = soundOpt.get();
            if (sound.getUserId().equals(userId)) {
                soundRepository.delete(sound);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Search sounds by name or ID for user
     */
    public List<Sound> searchUserSounds(String query, String userId) {
        return soundRepository.findByNameOrIdAndUserId(query, userId);
    }
    
    /**
     * Get sound count for user
     */
    public Long getUserSoundCount(String userId) {
        return soundRepository.countByUserId(userId);
    }
    
    /**
     * Get sound count for user in specific guild
     */
    public Long getUserSoundCountInGuild(String userId, String guildId) {
        return soundRepository.countByUserIdAndGuildId(userId, guildId);
    }
}