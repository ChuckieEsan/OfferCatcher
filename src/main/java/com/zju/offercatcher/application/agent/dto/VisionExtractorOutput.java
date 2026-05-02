package com.zju.offercatcher.application.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record VisionExtractorOutput(
    String company,
    String position,
    List<QuestionItem> questions
) {
    public static final VisionExtractorOutput DEFAULT = new VisionExtractorOutput(
        "未知", "未知", List.of());

    public record QuestionItem(
        @JsonProperty("question_text") String questionText,
        @JsonProperty("question_type") String questionType,
        @JsonProperty("core_entities") List<String> coreEntities,
        Map<String, Object> metadata
    ) {
        public QuestionItem() {
            this("", "knowledge", List.of(), Map.of());
        }
    }
}
