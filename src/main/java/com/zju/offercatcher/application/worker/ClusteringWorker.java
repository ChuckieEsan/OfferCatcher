package com.zju.offercatcher.application.worker;

import com.zju.offercatcher.application.service.ClusteringApplicationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 聚类 Worker — 定时执行 KMeans 聚类任务（Cron 调度，默认每天 18:00）。
 * <p>
 * 对应 Python: app/application/workers/clustering_worker.py
 */
@Component
@ConditionalOnProperty(name = "offercatcher.clustering.enabled", havingValue = "true", matchIfMissing = false)
public class ClusteringWorker {

    private static final Logger log = LoggerFactory.getLogger(ClusteringWorker.class);

    private final ClusteringApplicationService clusteringService;

    public ClusteringWorker(ClusteringApplicationService clusteringService) {
        this.clusteringService = clusteringService;
    }

    @Scheduled(cron = "${offercatcher.clustering.cron:0 0 18 * * ?}")
    public void runClustering() {
        log.info("ClusteringWorker triggered");
        try {
            ClusteringApplicationService.ClusteringResult result = clusteringService.runClustering(null);
            log.info("ClusteringWorker completed: {} questions → {} clusters, silhouette={:.4f}",
                    result.totalQuestions(), result.clusterCount(), result.silhouetteScore());
        } catch (Exception e) {
            log.error("ClusteringWorker failed: {}", e.getMessage(), e);
        }
    }
}
