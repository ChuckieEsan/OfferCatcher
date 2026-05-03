package com.zju.offercatcher.interfaces.dto;

import java.util.Map;

public final class StatsDto {

    private StatsDto() {
    }

    public record OverviewStats(
            long totalQuestions,
            long totalCompanies,
            long totalPositions,
            Map<String, Integer> byType,
            Map<Integer, Integer> byMastery,
            long hasAnswer,
            long noAnswer
    ) {
    }

    public record CompanyStats(
            String company,
            int count,
            int mastered,
            int hasAnswer
    ) {
    }

    public record PositionStats(
            String position,
            int count
    ) {
    }

    public record EntityStats(
            String entity,
            int count
    ) {
    }

    public record ClusterStats(
            String clusterId,
            int count
    ) {
    }
}
