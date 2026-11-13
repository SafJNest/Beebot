package com.safjnest.auth;

import java.util.List;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.function.Supplier;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safjnest.auth.DiscordAuthService.Authorization;
import com.safjnest.core.Bot;
import com.safjnest.utils.PermissionHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.http.HttpStatus;

public final class DiscordApi {
    private static final int TIMEOUT_SECONDS = 15;
    private static final int GUILD_LIMIT = 200;
    private static final String API = "https://discord.com/api/v10";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Logger LOG = LoggerFactory.getLogger(DiscordApi.class);
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    @FunctionalInterface
    public interface Transport { HttpResponse<String> send(HttpRequest request) throws Exception; }

    private final Transport transport;
    private final Supplier<JDA> discord;

    public record AuthUser(String id, String username, String globalName, String avatarUrl) {}

    public record Server(String id, String name, String iconUrl, boolean owner) {}

    public record DiscordToken(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") long expiresIn,
        @JsonProperty("refresh_token") String refreshToken,
        String scope
    ) {
        @Override public String toString() { return "DiscordToken[redacted]"; }
    }

    public DiscordApi() { this(request -> HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)), Bot::getJDA); }
    public DiscordApi(Transport transport, Supplier<JDA> discord) { this.transport = transport; this.discord = discord; }

    public Authorization authorize(String clientId, String state, String redirectUri, long ttl) {
        String query = "client_id=" + encode(clientId) + "&response_type=code&redirect_uri=" + encode(redirectUri)
            + "&scope=" + encode("identify guilds") + "&state=" + encode(state) + "&prompt=consent";
        return new Authorization("https://discord.com/oauth2/authorize?" + query, ttl);
    }

    public DiscordToken exchange(String clientId, String secret, String code, String redirectUri) {
        String body = "client_id=" + encode(clientId) + "&client_secret=" + encode(secret)
            + "&grant_type=authorization_code&code=" + encode(code) + "&redirect_uri=" + encode(redirectUri);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://discord.com/api/oauth2/token"))
            .timeout(Duration.ofSeconds(TIMEOUT_SECONDS)).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        JsonNode response = request(request, false);
        try { return JSON.treeToValue(response, DiscordToken.class); }
        catch (Exception exception) { throw malformed("Discord returned an invalid OAuth response"); }
    }

    public AuthUser me(String token) {
        JsonNode response = request(get("/users/@me", token), true);
        String id = text(response, "id"), username = text(response, "username");
        if (id == null || !id.matches("[0-9]{1,20}") || username == null || username.isBlank()) throw malformed("Discord returned an incomplete user profile");
        return new AuthUser(id, username, text(response, "global_name"), avatarUrl(id, text(response, "avatar")));
    }

    public List<Server> guilds(String token) {
        JsonNode response = request(get("/users/@me/guilds?limit=" + GUILD_LIMIT, token), true);
        if (!response.isArray() || response.size() > GUILD_LIMIT) throw malformed("Discord returned an invalid server list");
        var servers = new ArrayList<Server>();
        for (JsonNode guild : response) {
            String id = text(guild, "id"), name = text(guild, "name"), permissions = text(guild, "permissions");
            if (id == null || !id.matches("[0-9]{1,20}") || name == null || !guild.path("owner").isBoolean()
                    || permissions == null || !permissions.matches("[0-9]+")) throw malformed("Discord returned an incomplete server list");
            boolean owner = guild.get("owner").asBoolean();
            if (owner || PermissionHandler.hasPermission(permissions, Permission.ADMINISTRATOR)
                    || PermissionHandler.hasPermission(permissions, Permission.MANAGE_SERVER))
                servers.add(new Server(id, name, iconUrl(id, text(guild, "icon")), owner));
        }
        return List.copyOf(servers);
    }

    public List<Server> guildsForUser(String userId) {
        JDA jda = discord.get();
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) throw unavailable("Cannot verify personal key guild permissions");
        var servers = new ArrayList<Server>();
        for (Guild guild : jda.getGuilds()) {
            try {
                Member member = guild.getMemberById(userId);
                if (member == null && guild.isLoaded()) continue;
                if (member == null) member = guild.retrieveMemberById(userId).complete();
                if (member.isOwner() || member.hasPermission(Permission.ADMINISTRATOR) || member.hasPermission(Permission.MANAGE_SERVER))
                    servers.add(new Server(guild.getId(), guild.getName(), guild.getIconUrl(), member.isOwner()));
            } catch (ErrorResponseException exception) {
                var response = exception.getResponse();
                var raw = response == null ? null : response.getRawResponse();
                int status = response == null ? 0 : response.code;
                double retry = response == null ? 1 : Math.max(0, response.retryAfter / 1000.0);
                LOG.warn("Discord status={} X-RateLimit-Bucket={} X-RateLimit-Remaining={} retry_after={}", status,
                    raw == null ? null : raw.header("X-RateLimit-Bucket"), raw == null ? null : raw.header("X-RateLimit-Remaining"), retry);
                if (status == 429) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "discord_rate_limited", "Discord rate limit exceeded", retry);
                if (exception.getErrorResponse() != ErrorResponse.UNKNOWN_MEMBER) throw unavailable("Cannot verify personal key guild permissions");
            } catch (RuntimeException exception) { throw unavailable("Cannot verify personal key guild permissions"); }
        }
        return List.copyOf(servers);
    }

    public static String avatarUrl(String userId, String avatar) {
        if (avatar != null && !avatar.isBlank())
            return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatar + (avatar.startsWith("a_") ? ".gif" : ".png") + "?size=128";
        try { return "https://cdn.discordapp.com/embed/avatars/" + ((Long.parseUnsignedLong(userId) >>> 22) % 6) + ".png"; }
        catch (NumberFormatException exception) { return null; }
    }

    public static String iconUrl(String id, String icon) {
        return icon == null || icon.isBlank() ? null : "https://cdn.discordapp.com/icons/" + id + "/" + icon
            + (icon.startsWith("a_") ? ".gif" : ".png") + "?size=128";
    }

    // ================================================================================================================

    private JsonNode request(HttpRequest request, boolean bearer) {
        try {
            HttpResponse<String> response = transport.send(request);
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                double retry = retryAfter(response);
                LOG.warn("Discord status={} X-RateLimit-Bucket={} X-RateLimit-Remaining={} retry_after={}", status,
                    header(response, "X-RateLimit-Bucket"), header(response, "X-RateLimit-Remaining"), retry);
                if (status == 429) throw new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "discord_rate_limited", "Discord rate limit exceeded", retry);
                if (bearer && (status == 401 || status == 403)) throw new AuthException(HttpStatus.UNAUTHORIZED, "discord_token_expired", "Discord authorization expired or was revoked; log in again");
                if (status >= 500) throw unavailable("Discord is temporarily unavailable");
                throw malformed("Discord rejected the authentication request");
            }
            JsonNode body = JSON.readTree(response.body());
            if (body == null || body.isNull()) throw malformed("Discord returned an invalid response");
            return body;
        } catch (AuthException exception) { throw exception; }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) { throw malformed("Discord returned an invalid response"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw unavailable("Unable to reach Discord"); }
        catch (Exception exception) { throw unavailable("Unable to reach Discord"); }
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers() == null ? null : response.headers().firstValue(name).orElse(null);
    }

    private static double retryAfter(HttpResponse<String> response) {
        try {
            JsonNode value = JSON.readTree(response.body()).get("retry_after");
            if (value != null && value.isNumber() && Double.isFinite(value.asDouble()) && value.asDouble() >= 0) return value.asDouble();
        } catch (Exception ignored) { }
        try { double value = Double.parseDouble(header(response, "Retry-After")); if (Double.isFinite(value) && value >= 0) return value; }
        catch (Exception ignored) { }
        return 1;
    }

    private static HttpRequest get(String path, String token) {
        return HttpRequest.newBuilder(URI.create(API + path)).timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
            .header("Authorization", "Bearer " + token).GET().build();
    }
    private static String text(JsonNode node, String field) { JsonNode value = node.get(field); return value == null || value.isNull() ? null : value.asText(); }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
    private static AuthException malformed(String message) { return new AuthException(HttpStatus.BAD_GATEWAY, "discord_unavailable", message); }
    private static AuthException unavailable(String message) { return new AuthException(HttpStatus.SERVICE_UNAVAILABLE, "discord_unavailable", message); }
}
