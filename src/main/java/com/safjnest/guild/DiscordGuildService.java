package com.safjnest.guild;

import com.safjnest.auth.DiscordApi.Server;
import java.util.List;
import javax.crypto.spec.SecretKeySpec;
import com.safjnest.auth.AuthException;
import com.safjnest.auth.DiscordApi;
import com.safjnest.auth.DiscordAuthService.AuthContext;
import com.safjnest.utils.CryptoUtils;
import org.springframework.http.HttpStatus;

public final class DiscordGuildService {
    private final DiscordApi discord;
    private final DiscordGuildCache cache;
    private final SecretKeySpec encryptionKey;

    public DiscordGuildService(String encryptionKey) { this(new DiscordApi(), DiscordGuildCache.getInstance(), encryptionKey); }
    public DiscordGuildService(DiscordApi discord, DiscordGuildCache cache, String encryptionKey) {
        this.discord = discord;
        this.cache = cache;
        this.encryptionKey = CryptoUtils.parseEncryptionKey(encryptionKey);
    }

    public List<Server> servers(AuthContext context) {
        requireUser(context);
        return cache.get(context, () -> load(context));
    }

    public void requireAdministrator(AuthContext context, String guildId) {
        for (Server server : servers(context)) if (server.id().equals(guildId)) return;
        throw new AuthException(HttpStatus.FORBIDDEN, "guild_forbidden", "Guild owner, Administrator or Manage Server permission required");
    }

    // ================================================================================================================

    private List<Server> load(AuthContext context) {
        if (context.personal()) return discord.guildsForUser(context.user().id());
        String token;
        try { token = CryptoUtils.decrypt(context.encryptedToken(), encryptionKey); }
        catch (IllegalStateException exception) { throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "auth_unavailable", "Authentication encryption key is invalid"); }
        return discord.guilds(token);
    }

    private void requireUser(AuthContext context) {
        if (context == null || context.user() == null)
            throw new AuthException(HttpStatus.UNAUTHORIZED, "invalid_session", "A user session is required");
    }
}
