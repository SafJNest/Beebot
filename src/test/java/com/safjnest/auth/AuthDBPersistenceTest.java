package com.safjnest.auth;

import com.safjnest.utils.CryptoUtils;
import com.safjnest.sql.database.AuthDB;

import static org.junit.Assert.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.Test;

public class AuthDBPersistenceTest {
    @Test
    public void sqlEnforcesRevocationExpiryAndClientBinding() throws Exception {
        String url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";
        try (Connection setup = DriverManager.getConnection(url); Statement sql = setup.createStatement()) {
            sql.execute("CREATE TABLE api_key(id VARCHAR(36) PRIMARY KEY,name VARCHAR(100) UNIQUE,key_hash VARCHAR(43) UNIQUE,redirect_uris CLOB,type VARCHAR(8) DEFAULT 'generic',discord_user_id VARCHAR(32),user_json CLOB,active BOOLEAN DEFAULT TRUE,revoked_at TIMESTAMP)");
            sql.execute("CREATE TABLE auth_session(token_hash VARCHAR(43) PRIMARY KEY,api_key_id VARCHAR(36),discord_user_id VARCHAR(32),encrypted_discord_token CLOB,user_json CLOB,expires_at BIGINT,revoked_at TIMESTAMP)");
            sql.execute("CREATE TABLE auth_transaction(state_hash VARCHAR(43),api_key_id VARCHAR(36),redirect_uri VARCHAR(2048),expires_at BIGINT,PRIMARY KEY(state_hash,api_key_id))");
        }
        AuthDB repository = new AuthDB(() -> {
            try { Connection connection = DriverManager.getConnection(url); connection.setAutoCommit(false); return connection; }
            catch (Exception failure) { return null; }
        });
        repository.createClient("one", "app-one", "hash-one", "https://example.com/callback");
        repository.createClient("two", "app-two", "hash-two", "https://example.com/callback");
        String generated = repository.generate("dashboard", "https://safjnest.com/auth/callback/");
        assertEquals("https://safjnest.com/auth/callback/", repository.findClient(CryptoUtils.sha256(generated)).redirectUris());
        long now = System.currentTimeMillis();
        String redirect = "https://example.com/Callback";
        repository.createTransaction("state", "one", redirect, now + 600000);
        assertFalse(repository.consumeTransaction("state", "two", redirect, now));
        assertFalse(repository.consumeTransaction("state", "one", "https://example.com/callback", now));
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = executor.submit(() -> { start.await(); return repository.consumeTransaction("state", "one", redirect, now); });
            var second = executor.submit(() -> { start.await(); return repository.consumeTransaction("state", "one", redirect, now); });
            start.countDown();
            assertNotEquals(first.get(), second.get());
        }
        assertFalse(repository.consumeTransaction("state", "one", redirect, now));
        repository.createTransaction("expired", "one", redirect, now - 1);
        assertFalse(repository.consumeTransaction("expired", "one", redirect, now));
        assertEquals(1, repository.cleanupTransactions(now));
        repository.createSession("session", new AuthDB.Session("one", "42", "encrypted", "{}", now + 60000));
        assertNotNull(repository.findSession("session", "one", now));
        assertNull(repository.findSession("session", "two", now));
        assertNull(repository.findSession("session", "one", now + 60001));
        assertTrue(repository.revokeSession("session", "one"));
        assertFalse(repository.sessionActive("session", "one", now));
        assertEquals("hash-one", repository.revokeClient("app-one"));
        assertNull(repository.findClient("hash-one"));
        assertEquals(1, repository.cleanupSessions(now));
    }

    @Test
    public void databaseFailureIsNotAnInvalidCredential() {
        AuthDB repository = new AuthDB(() -> null);
        try { repository.findClient("missing"); fail("Expected storage failure"); }
        catch (IllegalStateException expected) { assertEquals("Authentication database unavailable", expected.getMessage()); }
    }
}
