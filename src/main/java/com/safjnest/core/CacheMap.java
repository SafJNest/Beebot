package com.safjnest.core;


import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.safjnest.util.TimeConstant;

public class CacheMap<K, V> extends ConcurrentHashMap<K, V>{
    private final long DEFAULT_EXPIRATION_MILLISECOND;
    private final long MAX_EXPIRATION_MILLISECOND;
    private int MAX_SIZE;
    
    private ScheduledExecutorService scheduler;
    private ConcurrentHashMap<K, ScheduledFuture<?>> scheduledTasks;

    private PriorityQueue<K> expirationQueue;

    public CacheMap(long DEFAULT_EXPIRATION_MILLISECOND, long MAX_EXPIRATION_MILLISECOND, int MAX_SIZE) {
        super();
        
        this.DEFAULT_EXPIRATION_MILLISECOND = DEFAULT_EXPIRATION_MILLISECOND;
        this.MAX_EXPIRATION_MILLISECOND = MAX_EXPIRATION_MILLISECOND;
        this.MAX_SIZE = MAX_SIZE;
        
        init();
    }

    public CacheMap(int MAX_SIZE) {
        super();
        
        this.DEFAULT_EXPIRATION_MILLISECOND = TimeConstant.HOUR * 12;
        this.MAX_EXPIRATION_MILLISECOND = TimeConstant.HOUR * 24;
        this.MAX_SIZE = MAX_SIZE;

        init();
    }

    public CacheMap() {
        super();
        
        this.DEFAULT_EXPIRATION_MILLISECOND = TimeConstant.HOUR * 12;
        this.MAX_EXPIRATION_MILLISECOND = TimeConstant.HOUR * 24;

        this.MAX_SIZE = 117;

        init();
    }

    /* -------------------------------------------------------------------------- */

    private void init() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.scheduledTasks = new ConcurrentHashMap<>();

        this.expirationQueue = new PriorityQueue<>(Comparator.comparing(scheduledTasks::get));
    }


    /* -------------------------------------------------------------------------- */
    /*                                   Overrides                                */
    /* -------------------------------------------------------------------------- */

    @SuppressWarnings("unchecked")
    @Override
    public V get(Object key) {
        V value = super.get(key);
        if (value != null) {
            updateTime((K) key);
        }
        return value;
    }

    public List<V> get(List<K> keys) {
        List<V> values = new ArrayList<>();
        keys.forEach(key -> {
            V value = get(key);
            if (value != null) {
                updateTime(key);
                values.add(value);
            }
        });
        return values;
    }

    public List<V> get(K[] keys) {
        return get(List.of(keys));
    }

    @Override
    public V put(K key, V value) {
        updateTime(key);
        V current = super.put(key, value);
        if (size() > MAX_SIZE) {
            K oldestK = expirationQueue.poll();
            if (oldestK != null) {
                V oldestV = super.get(oldestK);
                this.remove(oldestK);
                return oldestV;
            }
        }

        return current;
    }
    

    @SuppressWarnings("unchecked")
    @Override
    public V remove(Object key) {
        cancelScheduledTask((K)key);
        return super.remove(key);
    }

    @Override
    public void clear() {
        super.clear();
        expirationQueue.clear();
        scheduledTasks.values().forEach(task -> task.cancel(false));
        scheduledTasks.clear();
    }

    @Override
    public Collection<V> values() {
        return values(true);
    }

    public Collection<V> values(boolean update) {
        if (update) scheduledTasks.keySet().forEach(this::updateTime);
        return super.values();
    }

    /* -------------------------------------------------------------------------- */
    /*                              Inside methods                                */
    /* -------------------------------------------------------------------------- */

    private synchronized void updateTime(K key) {
        long def_time = DEFAULT_EXPIRATION_MILLISECOND;
        ScheduledFuture<?> currentTask = scheduledTasks.get(key);
        if (currentTask != null) {
            def_time = currentTask.getDelay(TimeUnit.MILLISECONDS);
            cancelScheduledTask(key);
        }
        long new_time = calculateExpirationTime(def_time);
        ScheduledFuture<?> task = scheduler.schedule(() -> remove(key), new_time, TimeUnit.MILLISECONDS);
        scheduledTasks.put(key, task);

        expirationQueue.remove(key);
        expirationQueue.add(key);
    }

    private synchronized void cancelScheduledTask(K key) {
        ScheduledFuture<?> task = scheduledTasks.get(key);
        if (task != null) {
            task.cancel(false);
            scheduledTasks.remove(key);
            expirationQueue.remove(key);
        }
    }
    
    private long calculateExpirationTime(long elapsedTime) {
        if (elapsedTime < 0) {
            throw new IllegalArgumentException("elapsedTime must be non-negative");
        }
        double ratio = 1.0 - (double) elapsedTime / DEFAULT_EXPIRATION_MILLISECOND;
        if (ratio < 0) ratio = 0;
        long expirationTime = (long) (DEFAULT_EXPIRATION_MILLISECOND + ratio * (MAX_EXPIRATION_MILLISECOND - DEFAULT_EXPIRATION_MILLISECOND));
        return expirationTime;
    }

    /* -------------------------------------------------------------------------- */
    /*                                   Utilites                                 */
    /* -------------------------------------------------------------------------- */

    public void setMaxSize(int size) {
        if (size < this.MAX_SIZE) {
            this.MAX_SIZE = size;
            while (size() > MAX_SIZE) {
                K oldestK = expirationQueue.poll();
                if (oldestK != null) {
                    this.remove(oldestK);
                }
            }
            return;
        }
        this.MAX_SIZE = size;
    }

    public int getMaxSize() {
        return MAX_SIZE;
    }

    public long getExpirationTime(K key) {
        ScheduledFuture<?> task = scheduledTasks.get(key);
        if (task != null) {
            return task.getDelay(TimeUnit.MILLISECONDS);
        }
        return -1;
    }

}