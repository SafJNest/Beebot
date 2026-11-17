package com.safjnest.spring.controller.beebot;

import java.util.List;

import com.safjnest.discord.auth.AuthException;
import com.safjnest.discord.auth.DiscordAuthService;
import com.safjnest.discord.auth.DiscordApi.AuthUser;
import com.safjnest.discord.auth.DiscordApi.Server;
import com.safjnest.discord.auth.DiscordAuthService.AuthContext;
import com.safjnest.discord.auth.DiscordAuthService.Authorization;
import com.safjnest.discord.auth.DiscordAuthService.Session;
import com.safjnest.discord.guild.DiscordGuildService;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import com.safjnest.spring.config.AuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

@RestController
@RequestMapping("/api/beebot")
public class DiscordAuthController {
    private final DiscordAuthService auth;
    private final DiscordGuildService guilds;

    public record AuthorizeRequest(String state, String redirectUri) {}

    public record ExchangeRequest(String code, String state, String redirectUri) {}


    public DiscordAuthController() { this(new DiscordAuthService(), new DiscordGuildService(null)); }

    @Autowired
    public DiscordAuthController(Environment environment) {
        this(new DiscordAuthService(environment.getProperty("DISCORD_CLIENT_ID"), environment.getProperty("DISCORD_CLIENT_SECRET"),
            environment.getProperty("AUTH_ENCRYPTION_KEY")), new DiscordGuildService(environment.getProperty("AUTH_ENCRYPTION_KEY")));
    }

    public DiscordAuthController(DiscordAuthService auth, DiscordGuildService guilds) {
        this.auth = auth;
        this.guilds = guilds;
    }

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @PostMapping("/auth/discord/authorize")
    public Authorization authorize(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context,
            @RequestBody(required = false) AuthorizeRequest request) {
        if (request == null) throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_request", "A JSON request body is required");
        return auth.authorize(context, request.state(), request.redirectUri());
    }

    @PostMapping("/auth/session")
    public Session exchange(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context,
            @RequestBody(required = false) ExchangeRequest request) {
        if (request == null) throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_request", "A JSON request body is required");
        return auth.exchangeSession(context, request.code(), request.state(), request.redirectUri());
    }

    @GetMapping({"/guilds", "/servers"})
    public List<Server> servers(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        return guilds.servers(context);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestAttribute(AuthFilter.AUTH_CONTEXT) AuthContext context) {
        auth.logout(context);
        return ResponseEntity.noContent().build();
    }
}
