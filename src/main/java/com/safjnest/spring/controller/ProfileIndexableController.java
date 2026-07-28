package com.safjnest.spring.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.safjnest.lol.model.ProfileIndexable;
import com.safjnest.lol.service.ProfileIndexableService;

@RestController
@RequestMapping("/api/lol")
public class ProfileIndexableController {

    @GetMapping("/profile/indexables")
    public List<ProfileIndexable> indexables() {
        return ProfileIndexableService.get();
    }
}
