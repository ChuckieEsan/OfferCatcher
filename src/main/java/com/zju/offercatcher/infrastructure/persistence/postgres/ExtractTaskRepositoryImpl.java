package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.question.aggregates.ExtractTask;
import com.zju.offercatcher.domain.question.aggregates.ExtractTaskStatus;
import com.zju.offercatcher.domain.question.valueobjects.ExtractedQuestionItem;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class ExtractTaskRepositoryImpl {

    private final ExtractTaskJpaRepository jpaRepo;

    public ExtractTaskRepositoryImpl(ExtractTaskJpaRepository jpaRepo) {
        this.jpaRepo = jpaRepo;
    }

    @Transactional
    public ExtractTask save(ExtractTask task) {
        ExtractTaskJpaEntity entity = ExtractTaskJpaEntity.fromDomain(task);
        ExtractTaskJpaEntity saved = jpaRepo.save(entity);
        return saved.toDomain();
    }

    public Optional<ExtractTask> findByIdAndUserId(Long taskId, String userId) {
        return jpaRepo.findByIdAndUserId(taskId, userId).map(ExtractTaskJpaEntity::toDomain);
    }

    public List<ExtractTask> findByUserId(String userId, String status, int limit, int offset) {
        // For simplicity, we handle pagination in-memory. For production, add Pageable support.
        List<ExtractTaskJpaEntity> entities;
        if (status != null && !status.isBlank()) {
            ExtractTaskStatus st = ExtractTaskStatus.valueOf(status.toUpperCase());
            entities = jpaRepo.findByUserIdAndStatus(userId, st);
        } else {
            entities = jpaRepo.findByUserId(userId);
        }
        return entities.stream()
                .skip(offset)
                .limit(limit)
                .map(ExtractTaskJpaEntity::toDomain)
                .toList();
    }

    public int countByUserId(String userId, String status) {
        if (status != null && !status.isBlank()) {
            ExtractTaskStatus st = ExtractTaskStatus.valueOf(status.toUpperCase());
            return jpaRepo.countByUserIdAndStatus(userId, st);
        }
        return jpaRepo.countByUserId(userId);
    }

    public List<ExtractTask> findPendingTasks(int limit) {
        return jpaRepo.findPendingTasks(limit).stream()
                .map(ExtractTaskJpaEntity::toDomain)
                .toList();
    }

    @Transactional
    public void updateStatus(Long taskId, ExtractTaskStatus status) {
        jpaRepo.findById(taskId).ifPresent(entity -> {
            entity.setStatus(status);
            entity.setUpdatedAt(java.time.LocalDateTime.now());
            jpaRepo.save(entity);
        });
    }

    @Transactional
    public ExtractTask updateResult(Long taskId, ExtractedQuestionItem result) {
        ExtractTaskJpaEntity entity = jpaRepo.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        entity.setExtractedInterview(result);
        entity.setStatus(ExtractTaskStatus.COMPLETED);
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        return jpaRepo.save(entity).toDomain();
    }

    @Transactional
    public ExtractTask updateEdit(Long taskId, String userId, String company,
                                  String position, List<ExtractedQuestionItem.QuestionItem> questions) {
        ExtractTaskJpaEntity entity = jpaRepo.findByIdAndUserId(taskId, userId)
                .orElse(null);
        if (entity == null || entity.getStatus() != ExtractTaskStatus.COMPLETED) {
            return null;
        }
        entity.setExtractedInterview(new ExtractedQuestionItem(company, position, questions));
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        return jpaRepo.save(entity).toDomain();
    }

    @Transactional
    public boolean deleteByIdAndUserId(Long taskId, String userId) {
        return jpaRepo.deleteByIdAndUserId(taskId, userId) > 0;
    }
}
