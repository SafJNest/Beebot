package com.safjnest.sound;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SoundQueryCacheTest {
    @Test public void idsPreserveOrderCacheEmptyAndSeparateUsersAndGenerations() {
        Map<String,String> entries=new HashMap<>();List<Integer> ttls=new ArrayList<>();
        var cache=new SoundQueryCache(new SoundQueryCache.Store(){public String get(String key){return entries.get(key);}public void put(String key,String value,int ttl){entries.put(key,value);ttls.add(ttl);}});
        cache.initialize();AtomicInteger queries=new AtomicInteger();
        assertEquals(List.of("2","1"),cache.ids(List.of("100","q"),()->{queries.incrementAndGet();return List.of("2","1");}));
        assertEquals(List.of("2","1"),cache.ids(List.of("100","q"),()->{fail();return List.of();}));
        assertTrue(cache.ids(List.of("200","q"),()->{queries.incrementAndGet();return List.of();}).isEmpty());
        assertTrue(cache.ids(List.of("200","q"),()->{fail();return List.of();}).isEmpty());
        assertEquals(2,queries.get());assertTrue(ttls.contains(60));
        cache.invalidate();assertEquals(List.of("3"),cache.ids(List.of("100","q"),()->List.of("3")));
        entries.replaceAll((key,value)->key.contains(":v1:")?"invalid json":value);
        assertEquals(List.of("4"),cache.ids(List.of("100","q"),()->List.of("4")));
    }
    @Test public void unavailableRedisFallsBackToSource() {
        var cache=new SoundQueryCache(new SoundQueryCache.Store(){public String get(String key){return null;}public void put(String key,String value,int ttl){}});
        cache.initialize();assertEquals(List.of("1"),cache.ids(List.of("100"),()->List.of("1")));
    }
}
