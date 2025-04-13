package com.safjnest.springapi.api.controller;

import java.io.IOException;
import java.nio.file.DirectoryStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.safjnest.sql.DatabaseHandler;
import com.safjnest.sql.QueryRecord;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;

import com.safjnest.springapi.api.model.Sound;
import com.safjnest.springapi.service.SoundService;

@RestController
@RequestMapping("/api/sound")
public class SoundController {

    private static final Path SOUND_DIRECTORY = Paths.get("rsc", "SoundBoard").toAbsolutePath().normalize();

    private SoundService soundService;

    @Autowired
    public SoundController(SoundService soundService) {
        this.soundService = soundService;
    }

    @GetMapping("/{id}")
    public Sound getSound(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing soundId");
        }

        Sound sound = soundService.getSoundById(id);
        if (sound == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sound not found");
        }
        return sound;
    }

    private boolean userHasAuth(String userId, String soundId) {
        if (soundId == null || soundId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing sound id");
        }
        QueryRecord sound = DatabaseHandler.getSoundById(soundId);

        if (sound.getAsInt("public") == 1 || sound.get("user_id").equals(userId)) {
            return true;
        }
        return false;
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadSound(@PathVariable String id, @RequestParam(required = false) String userId) {
        try {
            if (!Files.exists(SOUND_DIRECTORY) || !Files.isDirectory(SOUND_DIRECTORY)) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Directory not found");
            }

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(SOUND_DIRECTORY, id + ".*")) {
                for (Path filePath : stream) {
                    Resource resource = new UrlResource(filePath.toUri());
                    if(!resource.exists() || !resource.isReadable()) {
                        continue;
                    }

                    if(!userHasAuth(userId, id)) {
                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to access this sound");
                    }
                        
                    return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
                }
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sound not found");
            }
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error in getting the file", e);
        }
    }

    /* wip
    private String authenticateUser(String authToken) {
        HttpRequest userInfoRequest = HttpRequest.newBuilder()
            .uri(URI.create("https://discord.com/api/users/@me"))
            .header("Authorization", "okRCi_fevOpE.e_lmSzmrDGizlX3IQwebClktbevMqQ-1743865691966-0.0.1.1-604800000")
            .build();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> userInfoResponse = null;
        try {
            userInfoResponse = client.send(userInfoRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("User Info:");
        System.out.println(userInfoResponse.body());

        return userInfoResponse.body();
    }
    */
}
