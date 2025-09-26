package com.safjnest.core.cache;

import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;

import com.safjnest.core.cache.managers.GenericCache;
import java.util.concurrent.TimeUnit;

/**
 * Test Redis cache functionality
 */
public class RedisCacheTest {
    
    private GenericCache<String, String> testCache;
    
    @Before
    public void setUp() {
        testCache = new GenericCache<>(10, 1, TimeUnit.MINUTES, String.class, String.class);
    }
    
    @After
    public void tearDown() {
        if (testCache != null) {
            testCache.clear();
        }
    }
    
    @Test
    public void testPutAndGet() {
        String key = "test-key";
        String value = "test-value";
        
        testCache.put(key, value);
        String retrieved = testCache.get(key);
        
        assertEquals("Retrieved value should match put value", value, retrieved);
    }
    
    @Test
    public void testContains() {
        String key = "contains-key";
        String value = "contains-value";
        
        assertFalse("Key should not exist initially", testCache.contains(key));
        
        testCache.put(key, value);
        assertTrue("Key should exist after put", testCache.contains(key));
    }
    
    @Test
    public void testRemove() {
        String key = "remove-key";
        String value = "remove-value";
        
        testCache.put(key, value);
        assertTrue("Key should exist before remove", testCache.contains(key));
        
        String removed = testCache.remove(key);
        assertEquals("Removed value should match original", value, removed);
        assertFalse("Key should not exist after remove", testCache.contains(key));
    }
    
    @Test
    public void testNullValues() {
        String key = "null-key";
        
        String retrieved = testCache.get(key);
        assertNull("Non-existent key should return null", retrieved);
        
        assertFalse("Non-existent key should not be contained", testCache.contains(key));
    }
}