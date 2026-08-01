package com.safjnest.spring.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.safjnest.lol.model.ProfileIndexable;
import com.safjnest.lol.service.ProfileService;

@RestController
@RequestMapping("/api/lol")
public class ProfileIndexableController {

    private final ProfileService profileService = new ProfileService();

    @GetMapping("/profile/indexables")
    public List<ProfileIndexable> indexables() {
        return profileService.getIndexables();
    }
}
