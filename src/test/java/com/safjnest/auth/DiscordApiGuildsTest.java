package com.safjnest.auth;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.requests.restaction.CacheRestAction;
import org.junit.Test;

public class DiscordApiGuildsTest {

    @Test
    public void checksFreshMembershipAndAdministratorOnEveryRequest() {
        AtomicBoolean administrator = new AtomicBoolean(true);
        AtomicInteger calls = new AtomicInteger();
        Guild owner = guild("1", () -> member(true, false), calls);
        Guild admin = guild("2", () -> member(false, administrator.get()), calls);
        Guild manageOnly = guild("3", () -> member(false, false), calls);
        Guild missing = guild("4", () -> { throw ErrorResponseException.create(ErrorResponse.UNKNOWN_MEMBER, new net.dv8tion.jda.api.requests.Response(null, 404, "Not Found", 0L, java.util.Set.of())); }, calls);
        JDA jda = jda(List.of(owner, admin, manageOnly, missing), JDA.Status.CONNECTED);
        DiscordApi service = new DiscordApi(request -> { throw new AssertionError(); }, () -> jda);
        var servers = service.guildsForUser("42");
        assertEquals(2, servers.size());
        assertTrue(servers.get(0).owner());
        assertFalse(servers.get(1).owner());
        administrator.set(false);
        assertEquals(1, service.guildsForUser("42").size());
        assertEquals(8, calls.get());
    }

    @Test
    public void unavailableBotOrMembershipLookupFailsClosed() {
        for (JDA jda : new JDA[] {null, jda(List.of(), JDA.Status.DISCONNECTED)}) {
            try { new DiscordApi(request -> { throw new AssertionError(); }, () -> jda).guildsForUser("42"); fail("Expected unavailable Discord"); }
            catch (AuthException exception) { assertEquals(503, exception.getStatusCode().value()); }
        }
        Guild failed = guild("1", () -> { throw new IllegalStateException("Discord failure"); }, new AtomicInteger());
        try { new DiscordApi(request -> { throw new AssertionError(); }, () -> jda(List.of(failed), JDA.Status.CONNECTED)).guildsForUser("42"); fail("Expected lookup failure"); }
        catch (AuthException exception) { assertEquals(503, exception.getStatusCode().value()); }
    }

    private Member member(boolean owner, boolean administrator) {
        return member(owner, administrator, false);
    }

    private Member member(boolean owner, boolean administrator, boolean manageServer) {
        return proxy(Member.class, (instance, method, arguments) -> switch (method.getName()) {
            case "isOwner" -> owner;
            case "hasPermission" -> {
                Permission permission = ((Permission[]) arguments[0])[0];
                yield permission == Permission.ADMINISTRATOR ? administrator : permission == Permission.MANAGE_SERVER && manageServer;
            }
            default -> null;
        });
    }

    @Test
    public void usesCachedMemberAndAcceptsManageServer() {
        Member cached = member(false, false, true);
        Guild guild = proxy(Guild.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getId" -> "1";
            case "getName" -> "Guild 1";
            case "getMemberById" -> cached;
            case "retrieveMemberById" -> throw new AssertionError("Cached member must not trigger a request");
            default -> null;
        });
        var servers = new DiscordApi(request -> { throw new AssertionError(); }, () -> jda(List.of(guild), JDA.Status.CONNECTED)).guildsForUser("42");
        assertEquals(1, servers.size());
        assertEquals("1", servers.get(0).id());
    }

    @Test
    public void completeMemberCacheProvesAbsenceWithoutRequests() {
        Guild guild = proxy(Guild.class, (instance, method, arguments) -> switch (method.getName()) {
            case "isLoaded" -> true;
            case "getMemberById" -> null;
            case "retrieveMemberById" -> throw new AssertionError("Complete cache proves absence");
            default -> null;
        });
        assertTrue(new DiscordApi(request -> { throw new AssertionError(); }, () -> jda(List.of(guild), JDA.Status.CONNECTED))
            .guildsForUser("42").isEmpty());
    }

    private Guild guild(String id, java.util.function.Supplier<Member> member, AtomicInteger calls) {
        return proxy(Guild.class, (instance, method, arguments) -> switch (method.getName()) {
            case "isLoaded" -> false;
            case "getId" -> id;
            case "getName" -> "Guild " + id;
            case "retrieveMemberById" -> {
                assertEquals("42", arguments[0]);
                yield proxy(CacheRestAction.class, (action, actionMethod, actionArguments) -> switch (actionMethod.getName()) {
                    case "useCache" -> { assertEquals(false, actionArguments[0]); yield action; }
                    case "complete" -> { calls.incrementAndGet(); yield member.get(); }
                    default -> null;
                });
            }
            default -> null;
        });
    }

    private JDA jda(List<Guild> guilds, JDA.Status status) {
        return proxy(JDA.class, (instance, method, arguments) -> switch (method.getName()) {
            case "getGuilds" -> guilds;
            case "getStatus" -> status;
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
    }
}
