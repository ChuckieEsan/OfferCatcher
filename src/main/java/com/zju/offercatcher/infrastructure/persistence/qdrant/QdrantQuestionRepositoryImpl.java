package com.zju.offercatcher.infrastructure.persistence.qdrant;

import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.question.valueobjects.QuestionWithScore;
import com.zju.offercatcher.domain.shared.enums.Visibility;
import com.zju.offercatcher.domain.shared.exception.QuestionNotFoundException;
import com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Question Repository 实现
 *
 * 整合 Qdrant（向量检索）和 PostgreSQL（元数据存储）。
 *
 * 设计原则：
 * - Qdrant 存 embedding + userId + visibility（用于向量检索和预过滤）
 * - PostgreSQL 存所有元数据
 * - save() 同时写入 PostgreSQL 和 Qdrant（含 embedding 计算）
 */
@Repository
public class QdrantQuestionRepositoryImpl implements QuestionRepository {

    private static final Logger log = LoggerFactory.getLogger(QdrantQuestionRepositoryImpl.class);

    private final QuestionJpaRepository jpaRepository;
    private final QdrantVectorStore vectorStore;
    private final OnnxEmbeddingAdapter embeddingAdapter;

    public QdrantQuestionRepositoryImpl(QuestionJpaRepository jpaRepository,
                                        QdrantVectorStore vectorStore,
                                        OnnxEmbeddingAdapter embeddingAdapter) {
        this.jpaRepository = jpaRepository;
        this.vectorStore = vectorStore;
        this.embeddingAdapter = embeddingAdapter;
    }

    // ==================== 用户隔离查询方法 ====================

    @Override
    public List<QuestionWithScore> searchUserVisible(String userId, float[] queryVector, int limit) {
        return searchUserVisible(userId, queryVector, Collections.emptyMap(), limit);
    }

    @Override
    public List<QuestionWithScore> searchUserVisible(String userId, float[] queryVector,
                                                      Map<String, Object> filters, int limit) {
        // 1. Qdrant 向量搜索（带预过滤）
        List<VectorSearchHit> hits = vectorStore.search(queryVector, userId, limit);

        if (hits.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. PostgreSQL 批量查询元数据
        List<String> ids = hits.stream()
            .map(VectorSearchHit::id)
            .toList();

        List<QuestionJpaEntity> entities = jpaRepository.findByQuestionIds(ids);
        Map<String, Question> questionMap = entities.stream()
            .map(QuestionJpaEntity::toDomain)
            .collect(Collectors.toMap(Question::getQuestionId, q -> q));

        // 3. 合并结果（保持向量搜索的相似度顺序）
        return hits.stream()
            .map(hit -> {
                Question question = questionMap.get(hit.id());
                if (question == null) {
                    log.warn("Question not found in PostgreSQL: {}", hit.id());
                    return null;
                }
                return new QuestionWithScore(question, hit.score());
            })
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public List<QuestionWithScore> searchPublicOnly(float[] queryVector, int limit) {
        List<VectorSearchHit> hits = vectorStore.searchPublic(queryVector, limit);

        if (hits.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> ids = hits.stream()
            .map(VectorSearchHit::id)
            .toList();

        List<QuestionJpaEntity> entities = jpaRepository.findByQuestionIds(ids);
        Map<String, Question> questionMap = entities.stream()
            .map(QuestionJpaEntity::toDomain)
            .collect(Collectors.toMap(Question::getQuestionId, q -> q));

        return hits.stream()
            .map(hit -> {
                Question question = questionMap.get(hit.id());
                return question != null ? new QuestionWithScore(question, hit.score()) : null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public List<QuestionWithScore> searchPrivateOnly(String userId, float[] queryVector, int limit) {
        List<VectorSearchHit> hits = vectorStore.searchPrivate(userId, queryVector, limit);

        if (hits.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> ids = hits.stream()
            .map(VectorSearchHit::id)
            .toList();

        List<QuestionJpaEntity> entities = jpaRepository.findByQuestionIds(ids);
        Map<String, Question> questionMap = entities.stream()
            .map(QuestionJpaEntity::toDomain)
            .collect(Collectors.toMap(Question::getQuestionId, q -> q));

        return hits.stream()
            .map(hit -> {
                Question question = questionMap.get(hit.id());
                return question != null ? new QuestionWithScore(question, hit.score()) : null;
            })
            .filter(Objects::nonNull)
            .toList();
    }

    @Override
    public long countByUserIdAndVisibility(String userId, Visibility visibility) {
        return jpaRepository.countByUserIdAndVisibility(userId, visibility);
    }

    // ==================== 基本 CRUD ====================

    @Override
    public Optional<Question> findById(String questionId) {
        return jpaRepository.findByQuestionId(questionId)
            .map(QuestionJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(Question question) {
        QuestionJpaEntity entity = QuestionJpaEntity.fromDomain(question);
        jpaRepository.save(entity);

        if (embeddingAdapter.isInitialized()) {
            float[] vector = embeddingAdapter.embed(question.toContext());
            vectorStore.upsert(question, vector);
        }
    }

    @Override
    @Transactional
    public void deleteById(String questionId, String userId) {
        // 1. 验证所有权
        Optional<QuestionJpaEntity> entity = jpaRepository.findByQuestionId(questionId);
        if (entity.isEmpty()) {
            throw new QuestionNotFoundException(questionId);
        }
        if (!entity.get().getUserId().equals(userId)) {
            throw new UnauthorizedOperationException(userId, questionId, "delete");
        }

        // 2. 删除 PostgreSQL 记录
        jpaRepository.deleteByQuestionIdAndUserId(questionId, userId);

        // 3. 删除 Qdrant 点
        vectorStore.delete(questionId);
    }

    @Override
    @Transactional
    public void publishToPublic(String questionId, String userId) {
        // 1. 更新 PostgreSQL visibility
        int updated = jpaRepository.updateVisibilityToPublic(questionId, userId);
        if (updated == 0) {
            throw new UnauthorizedOperationException(userId, questionId, "publish");
        }

        // 2. 更新 Qdrant payload visibility
        vectorStore.updateVisibility(questionId, Visibility.PUBLIC);
    }

    // ==================== 批量操作 ====================

    @Override
    @Transactional
    public void saveAll(List<Question> questions) {
        List<QuestionJpaEntity> entities = questions.stream()
            .map(QuestionJpaEntity::fromDomain)
            .toList();
        jpaRepository.saveAll(entities);

        if (embeddingAdapter.isInitialized()) {
            for (Question q : questions) {
                float[] vector = embeddingAdapter.embed(q.toContext());
                vectorStore.upsert(q, vector);
            }
        }
    }

    @Override
    public List<Question> findByUserId(String userId, int page, int size) {
        int offset = page * size;
        return jpaRepository.findByUserIdPaginated(userId, size, offset)
            .stream()
            .map(QuestionJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Question> findPublicQuestions(int page, int size) {
        int offset = page * size;
        return jpaRepository.findPublicQuestionsPaginated(size, offset)
            .stream()
            .map(QuestionJpaEntity::toDomain)
            .toList();
    }

    @Override
    public List<Question> findByCompanyForUser(String userId, String company, int page, int size) {
        return jpaRepository.findByCompanyForUser(userId, company)
            .stream()
            .map(QuestionJpaEntity::toDomain)
            .toList();
    }
}