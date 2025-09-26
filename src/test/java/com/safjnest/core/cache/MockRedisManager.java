package com.safjnest.core.cache;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mock implementation for testing cache logic without Redis dependency
 */
public class MockRedisManager {
    private Map<String, String> mockStorage = new ConcurrentHashMap<>();
    private Map<String, Long> mockExpiration = new ConcurrentHashMap<>();
    
    public MockJedis getResource() {
        return new MockJedis();
    }
    
    public void returnResource(MockJedis jedis) {
        // No-op for mock
    }
    
    public boolean isHealthy() {
        return true;
    }
    
    public void shutdown() {
        mockStorage.clear();
        mockExpiration.clear();
    }
    
    /**
     * Mock Jedis implementation
     */
    public class MockJedis implements AutoCloseable {
        
        public String ping() {
            return "PONG";
        }
        
        public String get(String key) {
            // Check if key has expired
            Long expiration = mockExpiration.get(key);
            if (expiration != null && System.currentTimeMillis() > expiration) {
                mockStorage.remove(key);
                mockExpiration.remove(key);
                return null;
            }
            return mockStorage.get(key);
        }
        
        public String set(String key, String value) {
            mockStorage.put(key, value);
            mockExpiration.remove(key); // Remove any existing expiration
            return "OK";
        }
        
        public String setex(String key, int seconds, String value) {
            mockStorage.put(key, value);
            mockExpiration.put(key, System.currentTimeMillis() + (seconds * 1000L));
            return "OK";
        }
        
        public boolean exists(String key) {
            return get(key) != null; // This will handle expiration check
        }
        
        public Long del(String key) {
            boolean existed = mockStorage.containsKey(key);
            mockStorage.remove(key);
            mockExpiration.remove(key);
            return existed ? 1L : 0L;
        }
        
        public Long ttl(String key) {
            Long expiration = mockExpiration.get(key);
            if (expiration == null || !mockStorage.containsKey(key)) {
                return -1L; // Key doesn't exist or no expiration
            }
            
            long remaining = (expiration - System.currentTimeMillis()) / 1000L;
            return remaining > 0 ? remaining : -2L; // -2 means expired
        }
        
        public Set<String> keys(String pattern) {
            // Simple implementation - just return all keys for "*"
            if ("*".equals(pattern)) {
                return mockStorage.keySet();
            }
            // For more complex patterns, could implement proper matching
            return mockStorage.keySet();
        }
        
        public String flushDB() {
            mockStorage.clear();
            mockExpiration.clear();
            return "OK";
        }
        
        @Override
        public void close() {
            // No-op for mock
        }
    }
}