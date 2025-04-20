package com.safjnest.springapi.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;

import com.safjnest.model.sound.Sound;
import com.safjnest.springapi.service.SoundService;

@RestController
@RequestMapping("/api/sound")
public class SoundController {

    private SoundService soundService;

    @Autowired
    public SoundController(SoundService soundService) {
        this.soundService = soundService;
    }

    @GetMapping("/sounds")
    public List<Sound> getSounds(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int limit) {
        if (page <= 0 || limit <= 0 || limit > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page or limit");
        }

        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        return soundService.getSounds(userId, page, limit);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Sound> getSound(@PathVariable String id) {
        if (id == null || id.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing soundId");
        }

        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<Sound> sound = soundService.getSoundById(id);
        if (sound.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sound not found");
        }

        if (!soundService.userHasAuth(userId, sound.get())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to access this sound");
        }

        return ResponseEntity.ok(sound.get());
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadSound(@PathVariable String id) {

        String userId = SecurityContextHolder.getContext().getAuthentication().getName();

        Optional<Sound> sound = soundService.getSoundById(id);
        if (sound.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sound not found");
        }

        if(!soundService.userHasAuth(userId, sound.get())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You don't have permission to access this sound");
        }

        Optional<Resource> resource = soundService.getSoundFile(sound.get()); //throws if missing 
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.get().getFilename() + "\"")
            .body(resource.get());
    }
}
