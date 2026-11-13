package com.safjnest.auth;

import com.safjnest.auth.DiscordAuthService.Session;
import static org.junit.Assert.*;
import org.junit.Test;

public class DiscordAuthServiceTest {
    @Test
    public void checksAdministratorRatherThanManageGuild() {
        assertTrue(com.safjnest.utils.PermissionHandler.hasPermission("8", net.dv8tion.jda.api.Permission.ADMINISTRATOR));
        assertTrue(com.safjnest.utils.PermissionHandler.hasPermission("40", net.dv8tion.jda.api.Permission.ADMINISTRATOR));
        assertFalse(com.safjnest.utils.PermissionHandler.hasPermission("32", net.dv8tion.jda.api.Permission.ADMINISTRATOR));
        assertFalse(com.safjnest.utils.PermissionHandler.hasPermission("invalid", net.dv8tion.jda.api.Permission.ADMINISTRATOR));
    }

    @Test
    public void restrictsCallbackSchemesAndComponents() {
        assertTrue(DiscordAuthService.validRedirectUri("https://safjnest.com/auth/callback/"));
        assertTrue(DiscordAuthService.validRedirectUri("http://localhost:3000/auth/callback/"));
        assertFalse(DiscordAuthService.validRedirectUri("http://example.com/auth/callback/"));
        assertFalse(DiscordAuthService.validRedirectUri("https://example.com/callback?next=x"));
        assertFalse(DiscordAuthService.validRedirectUri("https://user@example.com/callback"));
    }

    @Test
    public void sessionStringDoesNotExposeBearer() {
        var session = new Session("secret-token", 1, null);
        assertFalse(session.toString().contains("secret-token"));
    }

}
