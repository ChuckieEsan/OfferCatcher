package com.zju.offercatcher.application.worker;

import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaRepository;
import com.zju.offercatcher.infrastructure.persistence.qdrant.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 重嵌入 Worker。
 *
 * 当题目文本更新后，重新计算向量并更新 Qdrant。
 * 对应 Python: app/application/workers/reembed_worker.py（批处理脚本）
 */
@Component
public class ReembedWorker {

    private static final Logger log = LoggerFactory.getLogger(ReembedWorker.class);
    private static final int BATCH_SIZE = 20;
    // 每 60 秒检查一次
    private static final long POLL_DELAY_MS = 60_000;

    private final QuestionJpaRepository questionJpaRepository;
    private final QdrantVectorStore qdrantVectorStore;
    private final OnnxEmbeddingAdapter embeddingAdapter;

    private volatile LocalDateTime lastCheck = LocalDateTime.now();

    public ReembedWorker(QuestionJpaRepository questionJpaRepository,
                         QdrantVectorStore qdrantVectorStore,
                         OnnxEmbeddingAdapter embeddingAdapter) {
        this.questionJpaRepository = questionJpaRepository;
        this.qdrantVectorStore = qdrantVectorStore;
        this.embeddingAdapter = embeddingAdapter;
    }

    @Scheduled(fixedDelay = POLL_DELAY_MS)
    public void reembedStaleVectors() {
        if (!embeddingAdapter.isInitialized()) {
            return;
        }

        LocalDateTime since = lastCheck;
        lastCheck = LocalDateTime.now();

        List<QuestionJpaEntity> updated = questionJpaRepository.findRecentlyUpdated(since, BATCH_SIZE);
        if (updated.isEmpty()) {
            return;
        }

        log.info("Reembed worker found {} updated questions since {}", updated.size(), since);

        int succeeded = 0;
        int failed = 0;
        for (QuestionJpaEntity entity : updated) {
            try {
                Question question = entity.toDomain();
                String context = question.toContext();
                float[] vector = embeddingAdapter.embed(context);
                qdrantVectorStore.upsert(question, vector);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.error("Reembed failed for question {}: {}", entity.getQuestionId(), e.getMessage());
            }
        }

        log.info("Reembed batch complete: {} succeeded, {} failed", succeeded, failed);
    }
}
