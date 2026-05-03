package com.zju.offercatcher.domain.question.valueobjects;

import java.util.List;
import java.util.Map;

/**
 * 提取的面试题目值对象。
 * <p>
 * 禁止用 Map.of() 构造 —— Jackson 不转换 Map key，会导致 snake_case 泄漏到 API 响应。
 */
public record ExtractedQuestionItem(
        String company,
        String position,
        List<QuestionItem> questions
) {
    public record QuestionItem(
            String questionHash,
            String questionText,
            String questionType,
            List<String> coreEntities,
            Map<String, Object> metadata
    ) {
    }
}
