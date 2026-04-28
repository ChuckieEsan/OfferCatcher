package com.zju.offercatcher.application.worker;

import com.zju.offercatcher.application.agent.VisionExtractorAgent;
import com.zju.offercatcher.application.agent.dto.ExtractedQuestionItem;
import com.zju.offercatcher.domain.question.aggregates.ExtractTask;
import com.zju.offercatcher.domain.question.aggregates.ExtractTaskStatus;
import com.zju.offercatcher.infrastructure.persistence.postgres.ExtractTaskJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.ExtractTaskJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * 面经提取 Worker。
 *
 * 轮询 PostgreSQL 查找 PENDING 状态的提取任务，调用 VisionExtractorAgent 异步处理。
 * 对应 Python: app/application/workers/extract_worker.py
 */
@Component
@ConditionalOnProperty(name = "offercatcher.worker.extract.enabled", havingValue = "true", matchIfMissing = true)
public class ExtractTaskWorker {

    private static final Logger log = LoggerFactory.getLogger(ExtractTaskWorker.class);
    private static final int BATCH_SIZE = 3;
    private static final long POLL_DELAY_MS = 5_000;

    private final ExtractTaskJpaRepository taskJpaRepo;
    private final VisionExtractorAgent visionExtractor;

    public ExtractTaskWorker(ExtractTaskJpaRepository taskJpaRepo,
                              VisionExtractorAgent visionExtractor) {
        this.taskJpaRepo = taskJpaRepo;
        this.visionExtractor = visionExtractor;
    }

    @Scheduled(fixedDelay = POLL_DELAY_MS)
    public void processPendingTasks() {
        List<ExtractTaskJpaEntity> pending = taskJpaRepo.findPendingTasks(BATCH_SIZE);
        if (pending.isEmpty()) {
            return;
        }

        log.info("Extract task worker found {} pending tasks", pending.size());

        for (ExtractTaskJpaEntity entity : pending) {
            processOne(entity);
        }
    }

    @Transactional
    void processOne(ExtractTaskJpaEntity entity) {
        Long taskId = entity.getTaskId();
        String userId = entity.getUserId();
        ExtractTask task = entity.toDomain();

        try {
            task.startProcessing();
            entity.setStatus(task.getStatus());
            entity.setUpdatedAt(task.getUpdatedAt());
            taskJpaRepo.save(entity);

            String sourceContent = entity.getSourceContent();
            if (sourceContent == null || sourceContent.isBlank()) {
                entity.setErrorMessage("Source content is empty");
                entity.setStatus(ExtractTaskStatus.PENDING);
                taskJpaRepo.save(entity);
                return;
            }

            ExtractedQuestionItem extracted = visionExtractor.extract(sourceContent);
            Map<String, Object> result = Map.of(
                "company", extracted.company(),
                "position", extracted.position(),
                "questions", extracted.questions().stream().map(q -> Map.<String, Object>of(
                    "question_id", q.questionId(),
                    "question_text", q.questionText(),
                    "question_type", q.questionType(),
                    "core_entities", q.coreEntities(),
                    "metadata", q.metadata()
                )).toList()
            );

            task.complete(result);
            entity.setStatus(task.getStatus());
            entity.setExtractedInterview(task.getExtractedInterview());
            entity.setUpdatedAt(task.getUpdatedAt());
            taskJpaRepo.save(entity);

            log.info("Extract task {} completed: company={}, {} questions",
                taskId, extracted.company(), extracted.questions().size());

        } catch (Exception e) {
            log.error("Extract task {} failed: {}", taskId, e.getMessage());
            entity.setErrorMessage(e.getMessage());
            entity.setStatus(ExtractTaskStatus.PENDING);
            taskJpaRepo.save(entity);
        }
    }
}
