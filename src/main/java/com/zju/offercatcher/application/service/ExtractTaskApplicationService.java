package com.zju.offercatcher.application.service;

import com.zju.offercatcher.application.agent.VisionExtractorAgent;
import com.zju.offercatcher.domain.question.aggregates.ExtractTask;
import com.zju.offercatcher.domain.question.aggregates.ExtractTaskStatus;
import com.zju.offercatcher.domain.question.valueobjects.ExtractedQuestionItem;
import com.zju.offercatcher.domain.shared.exception.NotFoundException;
import com.zju.offercatcher.domain.shared.exception.InvalidStateException;
import com.zju.offercatcher.infrastructure.persistence.postgres.ExtractTaskJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.ExtractTaskJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 提取任务应用服务。
 *
 * 任务生命周期管理：提交 -> 处理 -> 编辑 -> 确认入库。
 * 对应 Python: app/application/services/extract_task_service.py
 */
@Service
public class ExtractTaskApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ExtractTaskApplicationService.class);

    private final ExtractTaskJpaRepository taskJpaRepo;
    private final IngestFlowApplicationService ingestFlowApplicationService;
    private final VisionExtractorAgent visionExtractor;

    public ExtractTaskApplicationService(ExtractTaskJpaRepository taskJpaRepo,
                                          IngestFlowApplicationService ingestFlowApplicationService,
                                          VisionExtractorAgent visionExtractor) {
        this.taskJpaRepo = taskJpaRepo;
        this.ingestFlowApplicationService = ingestFlowApplicationService;
        this.visionExtractor = visionExtractor;
    }

    @Transactional
    public ExtractTask submit(String userId, String sourceType,
                              String sourceContent, List<String> sourceImages) {
        log.info("Submit extract task: user={}, type={}", userId, sourceType);
        ExtractTask task = ExtractTask.create(userId, sourceType, sourceContent, sourceImages);
        ExtractTaskJpaEntity entity = ExtractTaskJpaEntity.fromDomain(task);
        return taskJpaRepo.save(entity).toDomain();
    }

    @Transactional
    public ExtractTask processAndComplete(Long taskId, String userId) {
        ExtractTaskJpaEntity entity = taskJpaRepo.findByIdAndUserId(taskId, userId)
            .orElseThrow(() -> new NotFoundException("ExtractTask", taskId));

        ExtractTask task = entity.toDomain();
        task.startProcessing();

        try {
            String sourceContent = entity.getSourceContent();
            if (sourceContent == null || sourceContent.isBlank()) {
                throw new IllegalStateException("No source content to extract from");
            }

            ExtractedQuestionItem extracted = visionExtractor.extract(sourceContent);
            task.complete(extracted);
        } catch (Exception e) {
            log.error("Vision extraction failed for task {}: {}", taskId, e.getMessage());
            entity.setErrorMessage(e.getMessage());
            entity.setStatus(ExtractTaskStatus.PROCESSING);
            taskJpaRepo.save(entity);
            throw new RuntimeException("面经提取失败: " + e.getMessage(), e);
        }

        entity.setStatus(task.getStatus());
        entity.setExtractedInterview(task.getExtractedInterview());
        entity.setUpdatedAt(task.getUpdatedAt());
        return taskJpaRepo.save(entity).toDomain();
    }

    public List<ExtractTask> list(String userId, String status, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<ExtractTaskJpaEntity> entities;
        if (status != null && !status.isBlank()) {
            ExtractTaskStatus st = ExtractTaskStatus.valueOf(status.toUpperCase());
            entities = taskJpaRepo.findByUserIdAndStatus(userId, st);
        } else {
            entities = taskJpaRepo.findByUserId(userId);
        }
        return entities.stream()
            .skip(offset).limit(pageSize)
            .map(ExtractTaskJpaEntity::toDomain)
            .toList();
    }

    public int count(String userId, String status) {
        if (status != null && !status.isBlank()) {
            ExtractTaskStatus st = ExtractTaskStatus.valueOf(status.toUpperCase());
            return taskJpaRepo.countByUserIdAndStatus(userId, st);
        }
        return taskJpaRepo.countByUserId(userId);
    }

    public ExtractTask get(Long taskId, String userId) {
        return taskJpaRepo.findByIdAndUserId(taskId, userId)
            .map(ExtractTaskJpaEntity::toDomain)
            .orElseThrow(() -> new NotFoundException("ExtractTask", taskId));
    }

    @Transactional
    public ExtractTask edit(Long taskId, String userId,
                             String company, String position,
                             List<ExtractedQuestionItem.QuestionItem> questions) {
        ExtractTaskJpaEntity entity = taskJpaRepo.findByIdAndUserId(taskId, userId)
            .orElseThrow(() -> new NotFoundException("ExtractTask", taskId));

        if (entity.getStatus() != ExtractTaskStatus.COMPLETED) {
            throw new InvalidStateException("Cannot edit from status: " + entity.getStatus().name());
        }

        entity.setExtractedInterview(new ExtractedQuestionItem(company, position, questions));
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        return taskJpaRepo.save(entity).toDomain();
    }

    @Transactional
    public IngestFlowApplicationService.IngestResult confirm(Long taskId, String userId) {
        ExtractTaskJpaEntity entity = taskJpaRepo.findByIdAndUserId(taskId, userId)
            .orElseThrow(() -> new NotFoundException("ExtractTask", taskId));

        if (entity.getStatus() != ExtractTaskStatus.COMPLETED) {
            throw new InvalidStateException("Cannot confirm from status: " + entity.getStatus().name());
        }

        ExtractedQuestionItem interview = entity.getExtractedInterview();
        if (interview == null) {
            throw new InvalidStateException("ExtractTask has no result");
        }

        IngestFlowApplicationService.IngestResult result = ingestFlowApplicationService.ingest(interview, userId);

        entity.setStatus(ExtractTaskStatus.CONFIRMED);
        entity.setUpdatedAt(java.time.LocalDateTime.now());
        taskJpaRepo.save(entity);

        log.info("Task {} confirmed: processed={}", taskId, result.processed);
        return result;
    }

    @Transactional
    public void cancel(Long taskId, String userId) {
        ExtractTaskJpaEntity entity = taskJpaRepo.findByIdAndUserId(taskId, userId)
            .orElseThrow(() -> new NotFoundException("ExtractTask", taskId));

        ExtractTask task = entity.toDomain();
        task.cancel();
        entity.setStatus(task.getStatus());
        entity.setUpdatedAt(task.getUpdatedAt());
        taskJpaRepo.save(entity);
    }

    @Transactional
    public boolean delete(Long taskId, String userId) {
        return taskJpaRepo.deleteByIdAndUserId(taskId, userId) > 0;
    }

}
