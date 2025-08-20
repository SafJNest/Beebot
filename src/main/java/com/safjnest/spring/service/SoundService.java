package com.safjnest.spring.service;

import java.net.MalformedURLException;

import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.core.cache.managers.SoundCache;
import com.safjnest.model.sound.Sound;
import com.safjnest.sql.DatabaseHandler;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

@Service
public class SoundService {
    private static final Path SOUND_DIRECTORY = Paths.get("rsc", "sounds").toAbsolutePath().normalize();

    public Optional<Sound> getSoundById(String id) {
        Sound sound = SoundCache.getSoundById(id);
        return sound == null ? Optional.empty() : Optional.of(sound);
    }

    public List<Sound> getSounds(String userId, int page, int limit) {
        return DatabaseHandler.getSounds(userId, page, limit);
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
        if (sound.isPublic() || sound.getUserId().equals(userId)) { // || PermissionHandler.isUntouchable(userId)
            return true;
        }
        return false;
    }
}
