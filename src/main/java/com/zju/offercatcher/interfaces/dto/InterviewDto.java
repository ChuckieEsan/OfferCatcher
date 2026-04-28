package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 面试相关 DTO。
 */
public interface InterviewDto {

    record CreateSessionRequest(
        @NotBlank String company,
        @NotBlank String position,
        @NotBlank String difficulty,
        @Min(1) @Max(50) int totalQuestions
    ) {}

    record SubmitAnswerRequest(
        @NotBlank String answer
    ) {}

    record SessionResponse(
        Long sessionId,
        String company,
        String position,
        String difficulty,
        int totalQuestions,
        String status,
        int currentQuestionIdx,
        int correctCount,
        int totalScore,
        String createdAt,
        String updatedAt,
        List<QuestionItemResponse> questions
    ) {}

    record QuestionItemResponse(
        Long questionId,
        String questionText,
        String questionType,
        String difficulty,
        List<String> coreEntities,
        String answer,
        Integer score,
        String feedback,
        String status,
        int followUpCount
    ) {}

    record ReportResponse(
        Long sessionId,
        String company,
        String position,
        String difficulty,
        String status,
        int totalQuestions,
        int answeredCount,
        int correctCount,
        int totalScore,
        double averageScore,
        double durationMinutes,
        List<QuestionItemResponse> questions
    ) {}
}
