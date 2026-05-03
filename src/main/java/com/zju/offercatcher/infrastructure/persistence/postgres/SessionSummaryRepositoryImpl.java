package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import com.zju.offercatcher.domain.shared.enums.MemoryLayer;
import com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException;
import com.zju.offercatcher.infrastructure.persistence.qdrant.QdrantVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * SessionSummary Repository 实现
 */
@Repository
public class SessionSummaryRepositoryImpl implements SessionSummaryRepository {

    private static final Logger log = LoggerFactory.getLogger(SessionSummaryRepositoryImpl.class);

    private final SessionSummaryJpaRepository jpaRepository;
    private final QdrantVectorStore vectorStore;

    public SessionSummaryRepositoryImpl(SessionSummaryJpaRepository jpaRepository,
                                        QdrantVectorStore vectorStore) {
        this.jpaRepository = jpaRepository;
        this.vectorStore = vectorStore;
    }

    @Override
    public List<SessionSummary> findByUserId(String userId, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        return jpaRepository.findByUserIdPaginated(userId, size, offset)
                .stream()
                .map(SessionSummaryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<SessionSummary> findByUserIdAndMemoryLayer(String userId, MemoryLayer memoryLayer, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        return jpaRepository.findByUserIdAndMemoryLayerPaginated(userId, memoryLayer, size, offset)
                .stream()
                .map(SessionSummaryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<SessionSummary> findByConversationId(Long conversationId) {
        return jpaRepository.findByConversationId(conversationId)
                .stream()
                .map(SessionSummaryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<SessionSummary> findById(Long id) {
        return jpaRepository.findById(id)
                .map(SessionSummaryJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(SessionSummary summary) {
        SessionSummaryJpaEntity entity = SessionSummaryJpaEntity.fromDomain(summary);
        jpaRepository.save(entity);

        if (summary.getEmbedding() != null && summary.getEmbedding().length > 0) {
            vectorStore.upsertSessionSummary(summary);
        }
    }

    @Override
    @Transactional
    public void saveAll(List<SessionSummary> summaries) {
        List<SessionSummaryJpaEntity> entities = summaries.stream()
                .map(SessionSummaryJpaEntity::fromDomain)
                .toList();
        jpaRepository.saveAll(entities);

        for (SessionSummary summary : summaries) {
            if (summary.getEmbedding() != null && summary.getEmbedding().length > 0) {
                vectorStore.upsertSessionSummary(summary);
            }
        }
    }

    @Override
    @Transactional
    public void deleteById(Long id, String userId) {
        Optional<SessionSummaryJpaEntity> entity = jpaRepository.findById(id);
        if (entity.isEmpty()) {
            return;
        }
        if (!entity.get().getUserId().equals(userId)) {
            throw new UnauthorizedOperationException(userId, id.toString(), "delete session summary");
        }

        jpaRepository.deleteByIdAndUserId(id, userId);
        vectorStore.deleteSessionSummary(id);
    }

    @Override
    public List<SessionSummary> findTopKByImportance(String userId, int k) {
        return jpaRepository.findTopKByImportance(userId, k)
                .stream()
                .map(SessionSummaryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<SessionSummary> findMarkedForDeletion(String userId) {
        return jpaRepository.findMarkedForDeletion(userId)
                .stream()
                .map(SessionSummaryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public List<SessionSummary> searchByVector(String userId, float[] queryVector, int limit) {
        List<com.zju.offercatcher.infrastructure.persistence.qdrant.VectorSearchHit> hits =
                vectorStore.searchSessionSummaries(userId, queryVector, limit);

        if (hits.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = hits.stream()
                .map(hit -> Long.parseLong(hit.id()))
                .toList();

        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        return jpaRepository.findAllById(ids).stream()
                .map(SessionSummaryJpaEntity::toDomain)
                .toList();
    }

    @Override
    public long countByUserId(String userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public long countByUserIdAndMemoryLayer(String userId, MemoryLayer memoryLayer) {
        return jpaRepository.countByUserIdAndMemoryLayer(userId, memoryLayer);
    }
}
