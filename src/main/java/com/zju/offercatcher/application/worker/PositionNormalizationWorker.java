package com.zju.offercatcher.application.worker;

import com.zju.offercatcher.application.agent.PositionNormalizationAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 岗位归一化 Worker — 定时执行岗位名称归一化任务
 * <p>
 * 对应 Python: app/application/workers/position_normalization_worker.py
 */
@Component
@ConditionalOnProperty(name = "offercatcher.position-normalization.enabled", havingValue = "true", matchIfMissing = false)
public class PositionNormalizationWorker {

    private static final Logger log = LoggerFactory.getLogger(PositionNormalizationWorker.class);

    private final PositionNormalizationAgent normalizationService;

    public PositionNormalizationWorker(PositionNormalizationAgent normalizationService) {
        this.normalizationService = normalizationService;
    }

    @Scheduled(initialDelayString = "${offercatcher.position-normalization.initial-delay-ms:60000}",
            fixedDelayString = "${offercatcher.position-normalization.interval-ms:86400000}")
    public void runNormalization() {
        log.info("PositionNormalizationWorker triggered");
        try {
            Map<String, Integer> stats = normalizationService.runPipeline();
            if (!stats.isEmpty()) {
                log.info("PositionNormalizationWorker completed: {} position types migrated", stats.size());
            } else {
                log.info("PositionNormalizationWorker completed: no migrations needed");
            }
        } catch (Exception e) {
            log.error("PositionNormalizationWorker failed: {}", e.getMessage(), e);
        }
    }
}
