package com.safjnest.spring.controller.beebot;

import com.safjnest.discord.auth.DiscordAuthService;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.discord.auth.DiscordAuthService.Authorization;
import com.safjnest.discord.auth.DiscordAuthService.Session;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;
import com.safjnest.spring.config.AuthFilter;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/beebot")
public class DiscordAuthController {

    public record AuthorizeRequest(String state, String redirectUri) {}

    public record ExchangeRequest(String code, String state, String redirectUri) {}

    private final DiscordAuthService auth;

    public DiscordAuthController() {
        this(new DiscordAuthService());
    }

    @Autowired
    public DiscordAuthController(Environment environment) {
        this(new DiscordAuthService(environment.getProperty("DISCORD_CLIENT_ID"), environment.getProperty("DISCORD_CLIENT_SECRET"),
            environment.getProperty("AUTH_ENCRYPTION_KEY")));
    }

    public DiscordAuthController(DiscordAuthService auth) {
        this.auth = auth;
    }

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @PostMapping("/auth/discord/authorize")
    public Authorization authorize(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context,
            @RequestBody(required = false) AuthorizeRequest request) {
        if (request == null)
            throw new Exception(Type.INVALID_REQUEST, "invalid_request", "A JSON request body is required");

        return auth.authorize(context, request.state(), request.redirectUri());
    }

    @PostMapping("/auth/session")
    public Session exchange(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context,
            @RequestBody(required = false) ExchangeRequest request) {
        if (request == null)
            throw new Exception(Type.INVALID_REQUEST, "invalid_request", "A JSON request body is required");

        return auth.exchangeSession(context, request.code(), request.state(), request.redirectUri());
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        auth.logout(context);

        return ResponseEntity.noContent().build();
    }
}
