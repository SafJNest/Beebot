package com.safjnest.springapi.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.safjnest.springapi.api.model.Sound;
import com.safjnest.springapi.service.SoundService;

@RestController
@RequestMapping
public class SoundController {

    private SoundService soundService;

    @Autowired
    public SoundController(SoundService soundService) {
        this.soundService = soundService;
    }

    @GetMapping("/sound")
    public Sound getSound(@RequestParam String id) {
        Sound sound = soundService.getSoundById(id);
        if (sound == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Sound not found");
        }
        return sound;
    }
}
