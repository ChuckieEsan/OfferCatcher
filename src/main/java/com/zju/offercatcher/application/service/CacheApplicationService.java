package com.zju.offercatcher.application.service;

import com.zju.offercatcher.infrastructure.adapters.cache.CacheAdapter;
import com.zju.offercatcher.infrastructure.common.CacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

/**
 * 缓存应用服务
 *
 * 编排缓存用例，包含业务决策逻辑。
 * 对应 Python: app/application/services/cache_service.py
 */
@Service
public class CacheApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CacheApplicationService.class);

    private final CacheAdapter cacheAdapter;

    public CacheApplicationService(CacheAdapter cacheAdapter) {
        this.cacheAdapter = cacheAdapter;
    }

    public <T> T getWithLock(String key, Supplier<T> fetchFn, int ttl) {
        String cached = cacheAdapter.get(key);
        if (cached != null) {
            return parseJson(cached);
        }
        return null;
    }

    public <T> T getWithLock(String key, Supplier<T> fetchFn) {
        return getWithLock(key, fetchFn, 3600);
    }

    public void invalidateQuestion(Long id) {
        try {
            cacheAdapter.deletePattern(CacheKeys.questionsListPattern());
            cacheAdapter.deletePattern(CacheKeys.questionsCountPattern());
            cacheAdapter.deletePattern(CacheKeys.statsEntitiesPattern());
            cacheAdapter.delete(
                CacheKeys.statsOverview(),
                CacheKeys.statsClusters(),
                CacheKeys.statsCompanies()
            );
            if (id != null) {
                cacheAdapter.delete(CacheKeys.questionsItem(id));
            }
            cacheAdapter.deletePattern(CacheKeys.toolSearchPattern());
            log.info("Cache invalidated for question: {}", id);
        } catch (Exception e) {
            log.warn("Cache invalidation failed: {}", e.getMessage());
        }
    }

    public void invalidateToolsCache() {
        try {
            cacheAdapter.deletePattern(CacheKeys.toolSearchPattern());
            cacheAdapter.deletePattern(CacheKeys.toolGraphPattern());
            log.info("Tools cache invalidated");
        } catch (Exception e) {
            log.warn("Tools cache invalidation failed: {}", e.getMessage());
        }
    }

    @Async
    public void invalidateQuestionDelayed(Long id) {
        invalidateQuestion(id);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        invalidateQuestion(id);
    }

    @SuppressWarnings("unchecked")
    private static <T> T parseJson(String json) {
        return (T) json;
    }
}
