package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 题目相关 DTO。
 */
public interface QuestionDto {

    record CreateRequest(
            @NotBlank @Size(max = 2000) String questionText,
            @NotBlank String company,
            @NotBlank String position,
            @NotBlank String questionType,
            List<String> coreEntities,
            String visibility
    ) {
    }

    record UpdateRequest(
            String answer,
            Integer masteryLevel,
            String questionText,
            List<String> coreEntities
    ) {
    }

    record Response(
            Long id,
            String questionHash,
            String questionText,
            String company,
            String position,
            String questionType,
            int masteryLevel,
            List<String> coreEntities,
            String questionAnswer,
            List<String> clusterIds,
            Object metadata,
            String visibility,
            String sourceType,
            String createdAt,
            String updatedAt
    ) {
    }

    record ListResponse(
            List<Response> questions,
            int total,
            int page,
            int pageSize
    ) {
    }

    record BatchAnswersRequest(
            @NotEmpty List<Long> questionIds
    ) {
    }

    record BatchAnswersResponse(
            java.util.Map<Long, String> answers
    ) {
    }
}
