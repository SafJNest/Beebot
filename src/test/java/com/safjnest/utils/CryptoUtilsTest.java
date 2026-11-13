package com.safjnest.utils;

import static org.junit.Assert.*;
import java.util.Base64;
import org.junit.Test;

public class CryptoUtilsTest {
    @Test
    public void preservesPersistedHashAndEncryptedTokenFormats() {
        assertEquals("ungWv48Bz-pBQUDeXa4iI7ADYaOWF3qctBD_YfIAFa0", CryptoUtils.sha256("abc"));
        var key = CryptoUtils.parseEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        String persisted = "AAAAAAAAAAAAAAAAqs4zXiISD0NzIa621BbDJxmz9z74nlf/aD8HxDw=";
        assertEquals("discord-token", CryptoUtils.decrypt(persisted, key));
        String encrypted = CryptoUtils.encrypt("discord-token", key);
        assertEquals("discord-token", CryptoUtils.decrypt(encrypted, key));
        assertNotEquals(encrypted, CryptoUtils.encrypt("discord-token", key));
        byte[] modified = Base64.getDecoder().decode(encrypted);
        modified[modified.length - 1] ^= 1;
        try { CryptoUtils.decrypt(Base64.getEncoder().encodeToString(modified), key); fail("Tampering must fail"); }
        catch (IllegalStateException expected) { }
        assertNull(CryptoUtils.parseEncryptionKey("invalid"));
        assertNull(CryptoUtils.parseEncryptionKey(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
