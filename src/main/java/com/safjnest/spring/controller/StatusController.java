package com.safjnest.spring.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.R4B.api.dto.status.BotStatus;
import com.safjnest.spring.status.StatusService;

@RestController
@RequestMapping("/api")
public class StatusController {

    private final StatusService statusService;

    public StatusController() {
        this.statusService = new StatusService();
    }

    @GetMapping("/status")
    public BotStatus status() {
        return statusService.current();
    }
}
