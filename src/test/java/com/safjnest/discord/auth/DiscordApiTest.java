package com.safjnest.discord.auth;

import static org.junit.Assert.*;

import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.safjnest.discord.auth.DiscordApi.GuildAccess;
import com.safjnest.discord.auth.DiscordApi.Server;
import com.safjnest.exception.Exception;
import com.safjnest.exception.Exception.Type;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.Test;

public class DiscordApiTest {

    @Test
    public void ordinaryMembershipAndAdministrativeRightsStayDistinct() {
        List<Guild> guilds = List.of(
            guild("1", member(true, Set.of()), true),
            guild("2", member(false, Set.of(Permission.ADMINISTRATOR)), true),
            guild("3", member(false, Set.of(Permission.MANAGE_SERVER)), true),
            guild("4", member(false, Set.of()), true),
            guild("5", null, true)
        );
        DiscordApi api = api(guilds);
        Map<String, GuildAccess> memberships = api.guildAccess("42");
        List<String> administrative = new ArrayList<>();

        for (Server server : api.guildsForUser("42"))
            administrative.add(server.id());

        assertEquals(List.of("1", "2", "3"), administrative);
        assertEquals(List.of("1", "2", "3", "4"), new ArrayList<>(memberships.keySet()));
        assertTrue(memberships.get("1").administrator());
        assertTrue(memberships.get("2").administrator());
        assertFalse(memberships.get("3").administrator());
        assertFalse(memberships.get("4").administrator());
    }

    @Test
    public void missingMemberIsFetchedOnlyForAnIncompleteGuild() {
        AtomicInteger fetched = new AtomicInteger();
        AtomicReference<Member> cached = new AtomicReference<>();
        Member loaded = member(false, Set.of(Permission.MANAGE_SERVER));
        Guild incomplete = proxy(Guild.class, (object, method, args) -> switch (method.getName()) {
            case "getId" -> "1";
            case "getName" -> "Guild";
            case "getIconUrl" -> null;
            case "getMemberById" -> cached.get();
            case "isLoaded" -> false;
            case "retrieveMemberById" -> proxy(CacheRestAction.class, (action, call, arguments) -> {
                assertEquals("complete", call.getName());
                fetched.incrementAndGet();
                cached.set(loaded);
                return loaded;
            });
            default -> throw new UnsupportedOperationException(method.getName());
        });
        DiscordApi api = api(List.of(guild("2", null, true), incomplete));

        assertEquals(Set.of("1"), api.guildAccess("42").keySet());
        assertEquals(1, fetched.get());
        assertEquals(1, api.guildsForUser("42").size());
        assertEquals(1, fetched.get());
    }

    @Test
    public void disconnectedBotAndMembershipFailuresAreNotEmptyLists() {
        DiscordApi disconnected = new DiscordApi(request -> { throw new AssertionError("No HTTP expected"); }, () -> null);
        assertEquals(Type.UNAVAILABLE, assertThrows(Exception.class, () -> disconnected.guildAccess("42")).getType());
        assertEquals(Type.UNAVAILABLE, assertThrows(Exception.class, () -> disconnected.guildsForUser("42")).getType());

        Guild broken = proxy(Guild.class, (object, method, args) -> { throw new IllegalStateException("Unavailable"); });
        DiscordApi failing = api(List.of(broken));
        assertEquals("discord_unavailable", assertThrows(Exception.class, () -> failing.guildAccess("42")).getCode());
        assertEquals("discord_unavailable", assertThrows(Exception.class, () -> failing.guildsForUser("42")).getCode());
    }

    @Test
    public void permissionLookupFailuresKeepTheUnavailableClassification() {
        Member broken = proxy(Member.class, (object, method, args) -> { throw new IllegalStateException("Unavailable permissions"); });
        DiscordApi api = api(List.of(guild("1", broken, true)));

        assertEquals(Type.UNAVAILABLE, assertThrows(Exception.class, () -> api.guildAccess("42")).getType());
        assertEquals(Type.UNAVAILABLE, assertThrows(Exception.class, () -> api.guildsForUser("42")).getType());
    }

    @Test
    public void uncachedDisplayIdentityKeepsUnknownAndNullAvatar() {
        DiscordApi api = new DiscordApi(request -> { throw new AssertionError("No HTTP expected"); }, () -> null);
        Map<String, Object> user = api.user("42");

        assertEquals(List.of("id", "name", "avatarUrl"), new ArrayList<>(user.keySet()));
        assertEquals("42", user.get("id"));
        assertEquals("Unknown", user.get("name"));
        assertNull(user.get("avatarUrl"));
    }

    @Test
    public void discordRateLimitPreservesBodyHeaderAndFallbackRetry() {
        List<HttpResponse<String>> responses = List.of(
            reply(429, "{\"retry_after\":1.25}", Map.of("Retry-After", List.of("9"))),
            reply(429, "{\"retry_after\":-1}", Map.of("Retry-After", List.of("2.5"))),
            reply(429, "bad", Map.of("Retry-After", List.of("invalid")))
        );
        List<Double> expected = List.of(1.25, 2.5, 1.0);

        for (int index = 0; index < responses.size(); index++) {
            HttpResponse<String> response = responses.get(index);
            DiscordApi api = new DiscordApi(request -> response, () -> null);
            Exception failure = assertThrows(Exception.class, () -> api.me("token"));

            assertEquals(Type.RATE_LIMITED, failure.getType());
            assertEquals("discord_rate_limited", failure.getCode());
            assertEquals(expected.get(index), failure.getRetryAfter());
        }
    }

    @Test
    public void expiredAuthorizationAndUpstreamFailuresKeepTheirClassification() {
        for (int status : List.of(401, 403)) {
            DiscordApi api = new DiscordApi(request -> reply(status, "{}", Map.of()), () -> null);
            Exception failure = assertThrows(Exception.class, () -> api.me("token"));

            assertEquals(Type.UNAUTHENTICATED, failure.getType());
            assertEquals("discord_token_expired", failure.getCode());
        }

        DiscordApi unavailable = new DiscordApi(request -> reply(500, "{}", Map.of()), () -> null);
        DiscordApi rejected = new DiscordApi(request -> reply(400, "{}", Map.of()), () -> null);
        assertEquals(Type.UNAVAILABLE, assertThrows(Exception.class, () -> unavailable.me("token")).getType());
        assertEquals(Type.INVALID_UPSTREAM_RESPONSE, assertThrows(Exception.class, () -> rejected.exchange("app", "secret", "code", "https://example/callback")).getType());
    }

    @Test
    public void malformedResponsesStayErrorsAndOauthGuildPermissionsRemainFiltered() {
        DiscordApi malformed = new DiscordApi(request -> reply(200, "not-json", Map.of()), () -> null);
        assertEquals(Type.INVALID_UPSTREAM_RESPONSE, assertThrows(Exception.class, () -> malformed.me("token")).getType());

        String guilds = "[{\"id\":\"1\",\"name\":\"ordinary\",\"owner\":false,\"permissions\":\"0\"},"
            + "{\"id\":\"2\",\"name\":\"manage\",\"owner\":false,\"permissions\":\"32\"},"
            + "{\"id\":\"3\",\"name\":\"owner\",\"owner\":true,\"permissions\":\"0\"}]";
        DiscordApi api = new DiscordApi(request -> {
            assertEquals("Bearer token", request.headers().firstValue("Authorization").orElse(null));
            assertEquals("/api/v10/users/@me/guilds", request.uri().getPath());
            assertEquals("limit=200", request.uri().getQuery());
            return reply(200, guilds, Map.of());
        }, () -> null);
        List<Server> servers = api.guilds("token");

        assertEquals(2, servers.size());
        assertEquals("2", servers.get(0).id());
        assertEquals("3", servers.get(1).id());
    }

    @Test
    public void transportFailureAndInterruptionDoNotBecomeMissingData() {
        DiscordApi unavailable = new DiscordApi(request -> { throw new IOException("Disconnected"); }, () -> null);
        assertEquals(Type.UNAVAILABLE, assertThrows(Exception.class, () -> unavailable.me("token")).getType());

        DiscordApi interrupted = new DiscordApi(request -> { throw new InterruptedException(); }, () -> null);
        try {
            assertEquals(Type.UNAVAILABLE, assertThrows(Exception.class, () -> interrupted.me("token")).getType());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    /* -------------------------------------------------------------------------- */

    private static DiscordApi api(List<Guild> guilds) {
        JDA jda = proxy(JDA.class, (object, method, args) -> switch (method.getName()) {
            case "getStatus" -> JDA.Status.CONNECTED;
            case "getGuilds" -> guilds;
            default -> throw new UnsupportedOperationException(method.getName());
        });

        return new DiscordApi(request -> { throw new AssertionError("No HTTP expected"); }, () -> jda);
    }

    private static Guild guild(String id, Member member, boolean loaded) {
        return proxy(Guild.class, (object, method, args) -> switch (method.getName()) {
            case "getId" -> id;
            case "getName" -> "Guild " + id;
            case "getIconUrl" -> null;
            case "getMemberById" -> member;
            case "isLoaded" -> loaded;
            default -> throw new AssertionError("Unexpected guild call: " + method.getName());
        });
    }

    private static Member member(boolean owner, Set<Permission> permissions) {
        return proxy(Member.class, (object, method, args) -> {
            if (method.getName().equals("isOwner"))
                return owner;

            if (method.getName().equals("hasPermission")) {
                for (Permission permission : (Permission[]) args[0]) {
                    if (!permissions.contains(permission))
                        return false;
                }

                return true;
            }

            throw new AssertionError("Unexpected member call: " + method.getName());
        });
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> reply(int status, String body, Map<String, List<String>> headers) {
        return proxy(HttpResponse.class, (object, method, args) -> switch (method.getName()) {
            case "statusCode" -> status;
            case "body" -> body;
            case "headers" -> HttpHeaders.of(headers, (name, value) -> true);
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler));
    }
}
