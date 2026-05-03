package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 检索相关 DTO。
 */
public interface SearchDto {

    record SearchRequest(
            @NotBlank String query,
            String company,
            String position,
            @Min(1) int k,
            float scoreThreshold,
            Integer masteryLevel,
            String questionType,
            List<String> coreEntities,
            List<String> clusterIds
    ) {
    }

    record SearchResponse(
            List<SearchResultItem> results,
            int total
    ) {
    }

    record SearchResultItem(
            String questionId,
            String questionText,
            String company,
            String position,
            String masteryLevel,
            String questionType,
            List<String> coreEntities,
            List<String> clusterIds,
            String questionAnswer,
            Object metadata,
            float score
    ) {
    }
}
