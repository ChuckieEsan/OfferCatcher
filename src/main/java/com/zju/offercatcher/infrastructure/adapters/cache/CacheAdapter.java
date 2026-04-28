package com.zju.offercatcher.infrastructure.adapters.cache;

import com.zju.offercatcher.infrastructure.config.RedisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class CacheAdapter {

    private static final Logger log = LoggerFactory.getLogger(CacheAdapter.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final int defaultTtl;

    private static final String LOCK_PREFIX = "lock:";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class
        );
    }

    public CacheAdapter(RedisTemplate<String, String> redisTemplate, RedisProperties properties) {
        this.redisTemplate = redisTemplate;
        this.defaultTtl = properties.getTtl();
        log.info("CacheAdapter initialized: ttl={}s", defaultTtl);
    }

    // ==================== 基本缓存操作 ====================

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void set(String key, String value, int ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
    }

    public void set(String key, String value) {
        set(key, value, defaultTtl);
    }

    public void delete(String... keys) {
        redisTemplate.delete(List.of(keys));
    }

    public void deletePattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    // ==================== 分布式锁 ====================

    public String acquireLock(String key, int timeoutSeconds) {
        String lockKey = LOCK_PREFIX + key;
        String lockValue = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(timeoutSeconds));
        return Boolean.TRUE.equals(acquired) ? lockValue : null;
    }

    public void releaseLock(String key, String lockValue) {
        String lockKey = LOCK_PREFIX + key;
        redisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(lockKey), lockValue);
    }

    // ==================== 带锁的缓存读取 ====================

    private static final int MAX_RETRIES = 3;

    public String getWithLock(String key, Supplier<String> fetchFn, int ttlSeconds) {
        // 1. Check cache
        String cached = get(key);
        if (cached != null) {
            return cached;
        }

        // 2. Retry with lock
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String lockValue = acquireLock(key, 10);
            if (lockValue != null) {
                try {
                    // Double-check cache
                    cached = get(key);
                    if (cached != null) {
                        return cached;
                    }

                    // Fetch from source
                    String value = fetchFn.get();
                    if (value != null) {
                        set(key, value, ttlSeconds);
                    }
                    return value;
                } finally {
                    releaseLock(key, lockValue);
                }
            }

            // Lock held by another thread — wait with exponential backoff
            long waitMs = 100L * (1L << attempt); // 100 / 200 / 400 ms
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return fetchFn.get(); // Interrupted — fetch directly
            }
        }

        // 3. All retries exhausted — fallback to direct fetch without caching
        log.warn("Lock acquisition failed after {} retries: {}, fetching without cache", MAX_RETRIES, key);
        return fetchFn.get();
    }
}
