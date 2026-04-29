package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.chat.repositories.ConversationRepository;
import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.common.CacheKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 异步记忆检索服务
 *
 * 在对话开始时 fire-and-forget 触发检索，结果写入 Redis 缓存。
 * 下一次对话时注入已缓存的记忆上下文。
 *
 * 对应 Python: app/infrastructure/persistence/memory/memory_retrieval.py
 *   - trigger_retrieval()
 *   - retrieve_and_update_checkpoint()
 *   - merge_memory_context()
 */
@Service
public class MemoryRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetrievalService.class);

    private static final int MIN_QUERY_LENGTH = 10;
    private static final int DEFAULT_TOP_K = 5;
    private static final int MAX_CONTEXT_SIZE_BYTES = 20 * 1024;
    private static final int LOCK_TIMEOUT_SECONDS = 30;
    private static final int CONTEXT_TTL_SECONDS = 3600;

    private final SessionSummaryRepository sessionSummaryRepository;
    private final ConversationRepository conversationRepository;
    private final OnnxEmbeddingAdapter embeddingAdapter;
    private final RedisTemplate<String, String> redisTemplate;
    private final Executor workerExecutor;

    public MemoryRetrievalService(SessionSummaryRepository sessionSummaryRepository,
                                   ConversationRepository conversationRepository,
                                   OnnxEmbeddingAdapter embeddingAdapter,
                                   RedisTemplate<String, String> redisTemplate,
                                   @Qualifier("workerExecutor") Executor workerExecutor) {
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.conversationRepository = conversationRepository;
        this.embeddingAdapter = embeddingAdapter;
        this.redisTemplate = redisTemplate;
        this.workerExecutor = workerExecutor;
    }

    // ==================== Public API ====================

    /**
     * 读取上一次异步检索缓存的记忆上下文。
     * 返回 null 表示没有缓存（首次对话或检索尚未完成）。
     */
    public String getCachedContext(String userId, Long conversationId) {
        String key = CacheKeys.memoryContext(userId, conversationId);
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Fire-and-forget 触发异步记忆检索。
     *
     * 对当前轮次的消息计算 embedding，检索相关历史会话摘要，
     * 结果写入 Redis 缓存，供下一次对话注入。
     */
    public void triggerRetrieval(String userId, Long conversationId, String query) {
        if (query == null || query.strip().length() < MIN_QUERY_LENGTH) {
            log.debug("Retrieval not triggered: query too short (length={})",
                query != null ? query.length() : 0);
            return;
        }
        if (!embeddingAdapter.isInitialized()) {
            log.debug("Retrieval not triggered: embedding adapter not initialized");
            return;
        }

        CompletableFuture.runAsync(
            () -> doRetrieval(userId, conversationId, query),
            workerExecutor
        ).exceptionally(ex -> {
            log.error("Async memory retrieval failed for conversation {}: {}",
                conversationId, ex.getMessage(), ex);
            return null;
        });
    }

    // ==================== Retrieval Logic ====================

    private void doRetrieval(String userId, Long conversationId, String query) {
        // 1. Acquire lock
        String lockKey = CacheKeys.memoryRetrievalLock(userId, conversationId);
        Boolean locked = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", Duration.ofSeconds(LOCK_TIMEOUT_SECONDS));
        if (!Boolean.TRUE.equals(locked)) {
            log.debug("Retrieval skipped: lock held for conversation {}", conversationId);
            return;
        }

        try {
            // 2. Compute embedding
            float[] queryVector = embeddingAdapter.embed(query);

            // 3. Search session summaries
            List<SessionSummary> results = sessionSummaryRepository.searchByVector(
                userId, queryVector, DEFAULT_TOP_K);

            if (results.isEmpty()) {
                log.debug("No session summaries found for conversation {}", conversationId);
                return;
            }

            // 4. Enrich with conversation titles
            List<EnrichedSummary> enriched = enrichResults(results);

            // 5. Build and store memory context
            String existingContext = getCachedContext(userId, conversationId);
            String mergedContext = buildMergedContext(existingContext, enriched);

            String contextKey = CacheKeys.memoryContext(userId, conversationId);
            redisTemplate.opsForValue().set(contextKey, mergedContext,
                Duration.ofSeconds(CONTEXT_TTL_SECONDS));

            log.info("Memory context updated for conversation {}: {} summaries, {} chars",
                conversationId, enriched.size(), mergedContext.length());

        } catch (Exception e) {
            log.error("Memory retrieval failed for conversation {}: {}", conversationId, e.getMessage(), e);
        } finally {
            // 6. Release lock
            redisTemplate.delete(lockKey);
        }
    }

    // ==================== Enrichment & Formatting ====================

    private List<EnrichedSummary> enrichResults(List<SessionSummary> results) {
        List<EnrichedSummary> enriched = new ArrayList<>();
        for (SessionSummary s : results) {
            Optional<Conversation> conv = conversationRepository.findById(s.getConversationId());
            String title = conv.map(Conversation::getTitle).orElse("未知对话");
            String createdAt = s.getCreatedAt() != null ? s.getCreatedAt().toString() : "";
            enriched.add(new EnrichedSummary(s.getConversationId(), title, createdAt, s.getSummary()));
        }
        return enriched;
    }

    private String buildMergedContext(String existingContext, List<EnrichedSummary> newResults) {
        StringBuilder sb = new StringBuilder();
        if (existingContext != null && !existingContext.isBlank()) {
            sb.append(existingContext);
        }

        for (EnrichedSummary r : newResults) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            sb.append("### ").append(r.title).append(" (").append(r.createdAt).append(")\n");
            sb.append(r.summary);
        }

        // Capacity control: trim from start if exceeds max
        String result = sb.toString();
        byte[] bytes = result.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length > MAX_CONTEXT_SIZE_BYTES) {
            // Trim from the beginning, keeping the most recent sections
            String[] sections = result.split("\n\n### ");
            StringBuilder trimmed = new StringBuilder();
            for (int i = sections.length - 1; i >= 0; i--) {
                String prefix = i > 0 ? "### " : "";
                String candidate = prefix + sections[i] + (trimmed.isEmpty() ? "" : "\n\n" + trimmed);
                byte[] candidateBytes = candidate.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                if (candidateBytes.length <= MAX_CONTEXT_SIZE_BYTES) {
                    trimmed = new StringBuilder(candidate);
                } else {
                    break;
                }
            }
            log.debug("Memory context trimmed: {} sections, {} -> {} bytes",
                sections.length, bytes.length,
                trimmed.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
            return trimmed.toString();
        }

        return result;
    }

    // ==================== Inner Types ====================

    private record EnrichedSummary(Long conversationId, String title, String createdAt, String summary) {}
}
