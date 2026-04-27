package com.zju.offercatcher.application.service;

import com.zju.offercatcher.application.agent.dto.ExtractedQuestionItem;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.domain.shared.enums.Visibility;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaRepository;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.qdrant.QdrantVectorStore;
import com.zju.offercatcher.infrastructure.persistence.qdrant.VectorSearchHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 入库应用服务。
 *
 * 编排面经入库用例：提取结果 → 去重/复用答案 → 向量化 → 持久化。
 * 对应 Python: app/application/services/ingestion_service.py
 *
 * 简化点：无 MQ（用 @Async worker 替代），无 Neo4j，无岗位归一化。
 */
@Service
public class IngestFlowService {

    private static final Logger log = LoggerFactory.getLogger(IngestFlowService.class);

    private final QuestionJpaRepository questionJpaRepo;
    private final QdrantVectorStore qdrantVectorStore;
    private final OnnxEmbeddingAdapter embeddingAdapter;

    public IngestFlowService(QuestionJpaRepository questionJpaRepo,
                             QdrantVectorStore qdrantVectorStore,
                             OnnxEmbeddingAdapter embeddingAdapter) {
        this.questionJpaRepo = questionJpaRepo;
        this.qdrantVectorStore = qdrantVectorStore;
        this.embeddingAdapter = embeddingAdapter;
    }

    @Transactional
    public IngestResult ingest(ExtractedQuestionItem extracted, String userId) {
        IngestResult result = new IngestResult();
        if (extracted == null || extracted.questions() == null || extracted.questions().isEmpty()) {
            return result;
        }

        for (ExtractedQuestionItem.QuestionItem item : extracted.questions()) {
            try {
                // 1. 检查是否已存在
                Optional<QuestionJpaEntity> existing = questionJpaRepo.findByQuestionId(item.questionId());
                if (existing.isPresent()) {
                    log.info("Question {} already exists, skip", item.questionId());
                    result.questionIds.add(item.questionId());
                    continue;
                }

                // 2. 尝试复用高相似度题目的答案
                String reusedAnswer = null;
                if (embeddingAdapter.isInitialized()) {
                    String context = buildContext(extracted.company(), extracted.position(),
                        item.questionType(), item.coreEntities(), item.questionText());
                    float[] vector = embeddingAdapter.embed(context);
                    List<VectorSearchHit> similar = qdrantVectorStore.search(
                        vector, userId, 1);
                    if (!similar.isEmpty()) {
                        reusedAnswer = tryReuseAnswer(similar.getFirst().id());
                    }
                }

                // 3. 创建 Question 聚合
                Question question = Question.createPrivate(userId, item.questionText(),
                    extracted.company(), extracted.position(),
                    QuestionType.fromValue(item.questionType()), item.coreEntities());
                if (reusedAnswer != null) {
                    question.updateAnswer(reusedAnswer);
                }

                // 4. 持久化
                QuestionJpaEntity entity = QuestionJpaEntity.fromDomain(question);
                questionJpaRepo.save(entity);

                if (embeddingAdapter.isInitialized()) {
                    String context = question.toContext();
                    float[] vector = embeddingAdapter.embed(context);
                    qdrantVectorStore.upsert(question, vector);
                }

                result.processed++;
                result.questionIds.add(item.questionId());
            } catch (Exception e) {
                log.error("Failed to ingest question {}: {}", item.questionId(), e.getMessage());
                result.failed++;
            }
        }

        log.info("Ingestion complete: processed={}, failed={}", result.processed, result.failed);
        return result;
    }

    private String tryReuseAnswer(String similarQuestionId) {
        return questionJpaRepo.findByQuestionId(similarQuestionId)
            .map(QuestionJpaEntity::getAnswer)
            .filter(a -> a != null && !a.isBlank())
            .orElse(null);
    }

    private String buildContext(String company, String position, String questionType,
                                List<String> entities, String questionText) {
        String entitiesStr = (entities != null && !entities.isEmpty())
            ? String.join(",", entities) : "综合";
        return String.format("公司：%s | 岗位：%s | 类型：%s | 考点：%s | 题目：%s",
            company, position, questionType, entitiesStr, questionText);
    }

    public static class IngestResult {
        public int processed;
        public int failed;
        public final List<String> questionIds = new ArrayList<>();
    }
}
