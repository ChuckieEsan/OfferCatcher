package com.zju.offercatcher.application.agent.dto;

import java.util.List;
import java.util.Map;

/**
 * 提取的面试题目 DTO
 *
 * 对应 Python: app/domain/question/aggregates.py ExtractedInterview / QuestionItem
 */
public record ExtractedQuestionItem(
    String company,
    String position,
    List<QuestionItem> questions
) {
    public record QuestionItem(
        String questionId,
        String questionText,
        String questionType,
        List<String> coreEntities,
        Map<String, Object> metadata
    ) {}
}
