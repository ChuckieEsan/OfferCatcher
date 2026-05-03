package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.service.StatsApplicationService;
import com.zju.offercatcher.interfaces.dto.StatsDto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/stats")
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    private final StatsApplicationService statsService;

    public StatsController(StatsApplicationService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/overview")
    public ResponseEntity<OverviewStats> getOverview() {
        log.info("Get stats overview");
        return ResponseEntity.ok(statsService.getOverview());
    }

    @GetMapping("/companies")
    public ResponseEntity<List<CompanyStats>> getCompanyStats() {
        log.info("Get company stats");
        return ResponseEntity.ok(statsService.getCompanyStats());
    }

    @GetMapping("/positions")
    public ResponseEntity<List<PositionStats>> getPositionStats() {
        log.info("Get position stats");
        return ResponseEntity.ok(statsService.getPositionStats());
    }

    @GetMapping("/entities")
    public ResponseEntity<List<EntityStats>> getEntityStats(
            @RequestParam(required = false) String company,
            @RequestParam(defaultValue = "20") int limit) {
        log.info("Get entity stats: company={}, limit={}", company, limit);
        return ResponseEntity.ok(statsService.getEntityStats(company, limit));
    }

    @GetMapping("/clusters")
    public ResponseEntity<List<ClusterStats>> getClusterStats() {
        log.info("Get cluster stats");
        return ResponseEntity.ok(statsService.getClusterStats());
    }
}
