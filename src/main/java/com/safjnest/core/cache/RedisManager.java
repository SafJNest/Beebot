package com.safjnest.core.cache;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;

/**
 * Redis connection manager singleton
 * Handles connection pooling and provides Redis client instances
 */
public class RedisManager {
    private static RedisManager instance;
    private JedisPool jedisPool;
    
    private RedisManager() {
        initializePool();
    }
    
    public static synchronized RedisManager getInstance() {
        if (instance == null) {
            instance = new RedisManager();
        }
        return instance;
    }
    
    private void initializePool() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(20);
        poolConfig.setMaxIdle(10);
        poolConfig.setMinIdle(5);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setTestWhileIdle(true);
        poolConfig.setMinEvictableIdleTimeMillis(Duration.ofSeconds(60).toMillis());
        poolConfig.setTimeBetweenEvictionRunsMillis(Duration.ofSeconds(30).toMillis());
        
        // Default to localhost:6379, can be configured via environment variables
        String host = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        String password = System.getenv("REDIS_PASSWORD");
        
        if (password != null && !password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000);
        }
    }
    
    public Jedis getResource() {
        return jedisPool.getResource();
    }
    
    public void returnResource(Jedis jedis) {
        if (jedis != null) {
            jedis.close();
        }
    }
    
    public boolean isHealthy() {
        try (Jedis jedis = getResource()) {
            return "PONG".equals(jedis.ping());
        } catch (JedisException e) {
            return false;
        }
    }
    
    public void shutdown() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}