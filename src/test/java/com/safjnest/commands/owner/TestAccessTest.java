package com.safjnest.commands.owner;

import static org.junit.Assert.*;

import java.lang.reflect.Proxy;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.safjnest.utils.PermissionHandler;
import net.dv8tion.jda.api.entities.User;
import org.junit.Test;

public class TestAccessTest {

    @Test
    public void keyOperationsRequireUntouchableOrEpriaRegardlessOfOwner() {
        for (String operation : new String[] {"generate", "revoke"}) {
            for (String kind : new String[] {"apikey", "personalkey"}) {
                for (String id : PermissionHandler.getUntouchables()) {
                    FakeEvent event = new FakeEvent(id, false);
                    assertTrue(com.safjnest.commands.owner.Test.canExecute(event, operation, new String[] {operation, kind + " name"}));
                    assertNull(event.privateReply);
                }
                FakeEvent epria = new FakeEvent(PermissionHandler.getEpria(), false);
                assertTrue(com.safjnest.commands.owner.Test.canExecute(epria, operation, new String[] {operation, kind + " name"}));
                FakeEvent owner = new FakeEvent("123", true);
                assertFalse(com.safjnest.commands.owner.Test.canExecute(owner, operation, new String[] {operation, kind + " name"}));
                assertEquals("Who do you think you are? 🤨", owner.privateReply);
                FakeEvent stranger = new FakeEvent("123", false);
                assertFalse(com.safjnest.commands.owner.Test.canExecute(stranger, operation, new String[] {operation, kind + " name"}));
                assertEquals("Who do you think you are? 🤨", stranger.privateReply);
            }
        }
    }

    @Test
    public void otherOperationsStayRestrictedToOwnerAndCoOwners() {
        for (String operation : new String[] {"gc", "regenerate", "cleanup", "generate", "revoke"}) {
            String[] arguments = {operation, "other"};
            assertTrue(com.safjnest.commands.owner.Test.canExecute(new FakeEvent("123", true), operation, arguments));
            FakeEvent stranger = new FakeEvent(PermissionHandler.getEpria(), false);
            assertFalse(com.safjnest.commands.owner.Test.canExecute(stranger, operation, arguments));
            assertNull(stranger.privateReply);
        }
    }

    private static class FakeEvent extends CommandEvent {
        private final User user;
        private final boolean owner;
        private String privateReply;

        FakeEvent(String id, boolean owner) {
            super(null, "!", "", null);
            this.user = (User) Proxy.newProxyInstance(User.class.getClassLoader(), new Class<?>[] {User.class},
                (proxy, method, arguments) -> method.getName().equals("getId") ? id : null);
            this.owner = owner;
        }

        @Override
        public User getAuthor() { return user; }

        @Override
        public boolean isOwner() { return owner; }

        @Override
        public void replyInDm(String message) { privateReply = message; }
    }
}
