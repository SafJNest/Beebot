package com.safjnest.springapi.api.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.safjnest.springapi.service.ApiKeyService;

//@RestController
//@RequestMapping("/api/keys")
public class ApiKeyController {

    @Autowired
    private ApiKeyService apiKeyService;

    @PostMapping("/generate")
    public ResponseEntity<String> generateKey(@RequestParam String owner) {
        String newKey = apiKeyService.generateAndSaveApiKey(owner);
        return ResponseEntity.ok(newKey);
    }
}