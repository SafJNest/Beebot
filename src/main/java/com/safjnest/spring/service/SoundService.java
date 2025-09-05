package com.safjnest.spring.service;

import java.net.MalformedURLException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.spring.entity.Sound;
import com.safjnest.spring.repository.SoundRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class SoundService {
    private static final Path SOUND_DIRECTORY = Paths.get("rsc", "sounds").toAbsolutePath().normalize();
    
    private final SoundRepository soundRepository;
    
    @Autowired
    public SoundService(SoundRepository soundRepository) {
        this.soundRepository = soundRepository;
    }

    public Optional<Sound> getSoundById(String id) {
        try {
            Integer soundId = Integer.parseInt(id);
            return soundRepository.findById(soundId);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    public List<Sound> getSounds(String userId, int page, int limit) {
        Pageable pageable = PageRequest.of(page - 1, limit);
        Page<Sound> soundPage = soundRepository.findByUserIdOrPublic(userId, pageable);
        return soundPage.getContent();
    }

    public Optional<Resource> getSoundFile(Sound sound) {
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

    public boolean userHasAuth(String userId, Sound sound) {
        if (sound.getIsPublic() || sound.getUserId().equals(userId)) { // || PermissionHandler.isUntouchable(userId)
            return true;
        }
        return false;
    }
}
