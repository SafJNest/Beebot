package com.safjnest.spring.controller;

import java.util.Map;
import java.util.function.BiConsumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.safjnest.auth.AuthException;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.guild.GuildService;
import com.safjnest.model.guild.GuildSettings;
import com.safjnest.spring.config.BeebotAuthFilter;
import com.safjnest.guild.DiscordGuildService;
import org.springframework.core.env.Environment;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/beebot/guilds")
public class GuildController {

    private final GuildService guilds;
    private final BiConsumer<AuthContext, String> guildAccess;

    @Autowired
    public GuildController(Environment environment) {
        this(new GuildService(), new DiscordGuildService(environment.getProperty("AUTH_ENCRYPTION_KEY"))::requireAdministrator);
    }

    public GuildController(GuildService guilds, BiConsumer<AuthContext, String> guildAccess) {
        this.guilds = guilds;
        this.guildAccess = guildAccess;
    }

    @ModelAttribute
    public void preventCaching(HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    }

    @GetMapping("/{guildId}")
    public GuildSettings getGuild(@PathVariable("guildId") String guildId,
            @RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext context) {
        requireAdministrator(context, guildId);
        return guilds.getGuild(guildId);
    }

    @PostMapping("/{guildId}/settings")
    public Map<String, Object> updateSettings(@PathVariable("guildId") String guildId,
            @RequestAttribute(BeebotAuthFilter.AUTH_CONTEXT) AuthContext context,
            @RequestBody(required = false) JsonNode body) {
        requireAdministrator(context, guildId);
        return guilds.updateSettings(guildId, body);
    }

    // ================================================================================================================

    private void requireAdministrator(AuthContext context, String guildId) {
        if (context == null || context.user() == null)
            throw new AuthException(HttpStatus.UNAUTHORIZED, "invalid_session", "A user session is required");
        if (!GuildService.isGuildId(guildId))
            throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_guild_id", "Invalid guild ID");
        guildAccess.accept(context, guildId);
    }
}
