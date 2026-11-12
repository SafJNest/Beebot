package com.safjnest.spring.controller;

import java.util.List;
import com.safjnest.auth.DiscordAuthService;
import com.safjnest.auth.AuthException;
import com.safjnest.auth.DiscordApi.AuthUser;
import com.safjnest.auth.DiscordApi.Server;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.auth.DiscordAuthService.Authorization;
import com.safjnest.auth.DiscordAuthService.Session;
import com.safjnest.guild.DiscordGuildService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletResponse;
import com.safjnest.spring.config.BeebotAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;

@RestController
@RequestMapping("/api/beebot")
public class DiscordAuthController {
    private final DiscordAuthService auth;
    private final DiscordGuildService guilds;
    private final java.util.function.Function<AuthContext, java.util.Map<String, com.safjnest.model.sound.Sound>> greets;

    public record AuthorizeRequest(String state, String redirectUri) {}

    public record ExchangeRequest(String code, String state, String redirectUri) {}

    public record Me(AuthUser user, List<Server> servers, java.util.Map<String, com.safjnest.model.sound.Sound> greets) {}

    public DiscordAuthController() { this(new DiscordAuthService(), new DiscordGuildService(null)); }

    @Autowired
    public DiscordAuthController(Environment environment) {
        this(new DiscordAuthService(environment.getProperty("DISCORD_CLIENT_ID"), environment.getProperty("DISCORD_CLIENT_SECRET"),
            environment.getProperty("AUTH_ENCRYPTION_KEY")), new DiscordGuildService(environment.getProperty("AUTH_ENCRYPTION_KEY")));
    }

    public DiscordAuthController(DiscordAuthService auth, DiscordGuildService guilds) {
        this(auth, guilds, context -> {
            var sounds = com.safjnest.core.cache.managers.SoundCache.getInstance();
            return sounds.greets(sounds.context(context.user().id()));
        });
    }
    public DiscordAuthController(DiscordAuthService auth, DiscordGuildService guilds,
            java.util.function.Function<AuthContext, java.util.Map<String, com.safjnest.model.sound.Sound>> greets) {
        this.auth = auth; this.guilds = guilds; this.greets = greets;
    }

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @PostMapping("/auth/discord/authorize")
    public Authorization authorize(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext context,
            @RequestBody(required = false) AuthorizeRequest request) {
        if (request == null) throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_request", "A JSON request body is required");
        return auth.authorize(context, request.state(), request.redirectUri());
    }

    @PostMapping("/auth/session")
    public Session exchange(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext context,
            @RequestBody(required = false) ExchangeRequest request) {
        if (request == null) throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_request", "A JSON request body is required");
        return auth.exchangeSession(context, request.code(), request.state(), request.redirectUri());
    }

    @GetMapping("/me")
    public Me me(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext context) {
        return new Me(context.user(), guilds.servers(context), greets.apply(context));
    }

    @GetMapping({"/guilds", "/servers"})
    public List<Server> servers(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext context) {
        return guilds.servers(context);
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext context) {
        auth.logout(context);
        return ResponseEntity.noContent().build();
    }
}
