package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 面经提取相关 DTO。
 */
public interface ExtractDto {

    record SubmitRequest(
        @NotBlank String sourceType,
        String sourceContent,
        List<String> sourceImages
    ) {}

    record SubmitResponse(
        Long taskId,
        String message
    ) {}

    record TaskResponse(
        Long taskId,
        String userId,
        String sourceType,
        String sourceContent,
        List<String> sourceImages,
        String status,
        Map<String, Object> result,
        String createdAt,
        String updatedAt
    ) {}

    record TaskListItem(
        Long taskId,
        String status,
        String sourceType,
        String company,
        String position,
        int questionCount,
        String createdAt,
        String updatedAt
    ) {}

    record TaskListResponse(
        List<TaskListItem> items,
        int total,
        int page,
        int pageSize
    ) {}

    record UpdateRequest(
        String company,
        String position,
        List<Map<String, Object>> questions
    ) {}

    record ConfirmResponse(
        int processed,
        int failed,
        List<String> questionIds
    ) {}

    record ExtractTextRequest(
        @NotBlank String text
    ) {}

    record ExtractResponse(
        String company,
        String position,
        List<ExtractedQuestionDto> questions
    ) {}

    record ExtractedQuestionDto(
        String questionId,
        String questionText,
        String questionType,
        List<String> coreEntities,
        Map<String, Object> metadata
    ) {}
}
