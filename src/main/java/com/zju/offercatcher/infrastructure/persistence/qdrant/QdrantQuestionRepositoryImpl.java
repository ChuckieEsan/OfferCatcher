package com.zju.offercatcher.infrastructure.persistence.qdrant;

import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.question.valueobjects.QuestionWithScore;
import com.zju.offercatcher.domain.shared.enums.Visibility;
import com.zju.offercatcher.domain.shared.exception.QuestionNotFoundException;
import com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.persistence.neo4j.Neo4jClient;
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
 * 整合 Qdrant（向量检索）、PostgreSQL（元数据存储）和 Neo4j（知识图谱）。
 *
 * 设计原则：
 * - Qdrant 存 embedding + userId + visibility（用于向量检索和预过滤）
 * - PostgreSQL 存所有元数据
 * - Neo4j 只同步 PUBLIC 题目（用户隔离：私有题目的数据不进图数据库）
 * - save() 同时写入 PostgreSQL、Qdrant（含 embedding 计算）和 Neo4j（仅 PUBLIC）
 */
@Repository
public class QdrantQuestionRepositoryImpl implements QuestionRepository {

    private static final Logger log = LoggerFactory.getLogger(QdrantQuestionRepositoryImpl.class);

    private final QuestionJpaRepository jpaRepository;
    private final QdrantVectorStore vectorStore;
    private final OnnxEmbeddingAdapter embeddingAdapter;
    private final Neo4jClient neo4jClient;

    public QdrantQuestionRepositoryImpl(QuestionJpaRepository jpaRepository,
                                        QdrantVectorStore vectorStore,
                                        OnnxEmbeddingAdapter embeddingAdapter,
                                        Neo4jClient neo4jClient) {
        this.jpaRepository = jpaRepository;
        this.vectorStore = vectorStore;
        this.embeddingAdapter = embeddingAdapter;
        this.neo4jClient = neo4jClient;
    }

    // ==================== 用户隔离查询方法 ====================

    @Override
    public List<QuestionWithScore> searchUserVisible(String userId, float[] queryVector, int limit) {
        return searchUserVisible(userId, queryVector, Collections.emptyMap(), limit);
    }

    @Override
    public List<QuestionWithScore> searchUserVisible(String userId, float[] queryVector,
                                                      Map<String, Object> filters, int limit) {
        List<VectorSearchHit> hits = vectorStore.search(queryVector, userId, limit);

        if (hits.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = hits.stream()
            .map(hit -> Long.parseLong(hit.id()))
            .toList();

        List<QuestionJpaEntity> entities = jpaRepository.findByIds(ids);
        Map<Long, Question> questionMap = entities.stream()
            .map(QuestionJpaEntity::toDomain)
            .collect(Collectors.toMap(Question::getId, q -> q));

        return hits.stream()
            .map(hit -> {
                Long id = Long.parseLong(hit.id());
                Question question = questionMap.get(id);
                if (question == null) {
                    log.warn("Question not found in PostgreSQL: id={}", id);
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

        List<Long> ids = hits.stream()
            .map(hit -> Long.parseLong(hit.id()))
            .toList();

        List<QuestionJpaEntity> entities = jpaRepository.findByIds(ids);
        Map<Long, Question> questionMap = entities.stream()
            .map(QuestionJpaEntity::toDomain)
            .collect(Collectors.toMap(Question::getId, q -> q));

        return hits.stream()
            .map(hit -> {
                Long id = Long.parseLong(hit.id());
                Question question = questionMap.get(id);
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

        List<Long> ids = hits.stream()
            .map(hit -> Long.parseLong(hit.id()))
            .toList();

        List<QuestionJpaEntity> entities = jpaRepository.findByIds(ids);
        Map<Long, Question> questionMap = entities.stream()
            .map(QuestionJpaEntity::toDomain)
            .collect(Collectors.toMap(Question::getId, q -> q));

        return hits.stream()
            .map(hit -> {
                Long id = Long.parseLong(hit.id());
                Question question = questionMap.get(id);
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
    public Optional<Question> findById(Long id) {
        return jpaRepository.findById(id)
            .map(QuestionJpaEntity::toDomain);
    }

    @Override
    public Optional<Question> findByQuestionHash(String questionHash) {
        return jpaRepository.findByQuestionHash(questionHash)
            .map(QuestionJpaEntity::toDomain);
    }

    @Override
    public List<Question> findByIds(List<Long> ids) {
        return jpaRepository.findByIds(ids)
            .stream()
            .map(QuestionJpaEntity::toDomain)
            .toList();
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

        if (question.getVisibility() == Visibility.PUBLIC) {
            syncToNeo4j(question);
        }
    }

    @Override
    @Transactional
    public void deleteById(Long id, String userId) {
        Optional<QuestionJpaEntity> entity = jpaRepository.findById(id);
        if (entity.isEmpty()) {
            throw new QuestionNotFoundException(id);
        }
        if (!entity.get().getUserId().equals(userId)) {
            throw new UnauthorizedOperationException(userId, id.toString(), "delete");
        }

        jpaRepository.deleteByIdAndUserId(id, userId);
        vectorStore.delete(id);
    }

    @Override
    @Transactional
    public void publishToPublic(Long id, String userId) {
        int updated = jpaRepository.updateVisibilityToPublic(id, userId);
        if (updated == 0) {
            throw new UnauthorizedOperationException(userId, id.toString(), "publish");
        }

        vectorStore.updateVisibility(id, Visibility.PUBLIC);

        jpaRepository.findById(id)
            .map(QuestionJpaEntity::toDomain)
            .ifPresent(this::syncToNeo4j);
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

        for (Question q : questions) {
            if (q.getVisibility() == Visibility.PUBLIC) {
                syncToNeo4j(q);
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
    public List<Question> findByKeyword(String userId, String keyword, int page, int size) {
        return jpaRepository.searchPrivateByKeyword(userId, keyword)
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

    // ==================== Neo4j 同步 ====================

    private void syncToNeo4j(Question question) {
        try {
            neo4jClient.recordQuestionEntities(
                question.getCompany(),
                question.getCoreEntities()
            );
            log.debug("Synced to Neo4j: question={}, company={}",
                question.getId(), question.getCompany());
        } catch (Exception e) {
            log.warn("Failed to sync question {} to Neo4j: {}",
                question.getId(), e.getMessage());
        }
    }
}
