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

    public String getWithLock(String key, Supplier<String> fetchFn, int ttlSeconds) {
        String cached = get(key);
        if (cached != null) {
            return cached;
        }

        String lockValue = acquireLock(key, 30);
        if (lockValue == null) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return get(key);
        }

        try {
            cached = get(key);
            if (cached != null) {
                return cached;
            }

            String value = fetchFn.get();
            if (value != null) {
                set(key, value, ttlSeconds);
            }
            return value;
        } finally {
            releaseLock(key, lockValue);
        }
    }
}
