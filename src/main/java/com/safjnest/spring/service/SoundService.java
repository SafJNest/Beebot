package com.safjnest.spring.service;

import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.spring.entity.SoundEntity;
import com.safjnest.spring.repository.SoundRepository;

@Service
@Transactional
public class SoundService {
    
    private static final Path SOUND_DIRECTORY = Paths.get("rsc", "sounds").toAbsolutePath().normalize();

    @Autowired
    private SoundRepository soundRepository;

    @Cacheable(value = "sounds", key = "#id")
    public Optional<SoundEntity> getSoundById(Long id) {
        return soundRepository.findById(id);
    }

    public List<SoundEntity> getSounds(String userId, int page, int limit) {
        return soundRepository.findByUserIdOrPublic(userId, PageRequest.of(page - 1, limit)).getContent();
    }

    public List<SoundEntity> getGuildSounds(String guildId) {
        return soundRepository.findByGuildId(guildId);
    }

    public List<SoundEntity> getRandomGuildSounds(String guildId) {
        return soundRepository.findRandomByGuildId(guildId);
    }

    public List<SoundEntity> searchSounds(String name, String guildId) {
        return soundRepository.findByNameLikeAndGuildId(name, guildId);
    }

    @CacheEvict(value = "sounds", key = "#result.id")
    public SoundEntity saveSound(SoundEntity sound) {
        return soundRepository.save(sound);
    }

    @CacheEvict(value = "sounds", key = "#id")
    public boolean deleteSound(Long id) {
        if (soundRepository.existsById(id)) {
            soundRepository.deleteById(id);
            return true;
        }
        return false;
    }

    @CacheEvict(value = "sounds", key = "#id")
    public boolean updateSound(Long id, String name, Boolean isPublic) {
        Optional<SoundEntity> sound = soundRepository.findById(id);
        if (sound.isPresent()) {
            SoundEntity entity = sound.get();
            if (name != null) entity.setName(name);
            if (isPublic != null) entity.setIsPublic(isPublic);
            soundRepository.save(entity);
            return true;
        }
        return false;
    }

    public Optional<Resource> getSoundFile(SoundEntity sound) {
        if (!Files.exists(SOUND_DIRECTORY) || !Files.isDirectory(SOUND_DIRECTORY)) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Directory not found");
        }

        try {
            Path filePath = SOUND_DIRECTORY.resolve(sound.getId() + "." + sound.getExtension());
            return Optional.of(new UrlResource(filePath.toUri()));
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Sound file not found");
        }
    }

    public boolean userHasAuth(String userId, SoundEntity sound) {
        return sound.getIsPublic() || sound.getUserId().equals(userId);
    }

    public Optional<SoundEntity> getAccessibleSound(Long id, String guildId, String userId) {
        return soundRepository.findAccessibleSound(id, guildId, userId);
    }
}
