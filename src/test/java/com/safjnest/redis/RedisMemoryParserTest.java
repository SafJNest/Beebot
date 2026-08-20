package com.safjnest.redis;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class RedisMemoryParserTest {

    @Test
    public void parsesUsedMemoryFromRedisInfo() {
        String info = "# Memory\nused_memory:428392448\nused_memory_human:408.55M\nused_memory_rss:512000000\n";
        assertEquals(Long.valueOf(428392448), RedisMemoryParser.parseUsedMemory(info));
    }

    @Test
    public void returnsNullWhenUsedMemoryIsMissing() {
        assertNull(RedisMemoryParser.parseUsedMemory(""));
        assertNull(RedisMemoryParser.parseUsedMemory("used_memory_rss:1"));
        assertNull(RedisMemoryParser.parseUsedMemory(null));
    }
}
