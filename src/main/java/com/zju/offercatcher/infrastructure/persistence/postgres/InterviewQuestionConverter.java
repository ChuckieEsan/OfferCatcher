package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * InterviewQuestion JSON 转换器
 *
 * 将 InterviewQuestion 列表转换为 JSON 字符串存储到数据库。
 */
@Converter
public class InterviewQuestionConverter implements AttributeConverter<List<InterviewQuestion>, String> {

    private static final Logger log = LoggerFactory.getLogger(InterviewQuestionConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<InterviewQuestion> questions) {
        if (questions == null || questions.isEmpty()) {
            return "[]";
        }
        try {
            List<Map<String, Object>> questionData = questions.stream()
                .map(this::questionToMap)
                .toList();
            return objectMapper.writeValueAsString(questionData);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert InterviewQuestion to JSON", e);
            throw new RuntimeException("Failed to convert InterviewQuestion to JSON", e);
        }
    }

    @Override
    public List<InterviewQuestion> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> questionData = objectMapper.readValue(json,
                new TypeReference<List<Map<String, Object>>>() {});
            return questionData.stream()
                .map(this::mapToQuestion)
                .toList();
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to InterviewQuestion", e);
            throw new RuntimeException("Failed to convert JSON to InterviewQuestion", e);
        }
    }

    private Map<String, Object> questionToMap(InterviewQuestion q) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("questionId", q.getQuestionId());
        map.put("questionText", q.getQuestionText());
        map.put("questionType", q.getQuestionType());
        map.put("difficulty", q.getDifficulty().getValue());
        map.put("knowledgePoints", q.getKnowledgePoints());
        map.put("userAnswer", q.getUserAnswer());
        map.put("score", q.getScore());
        map.put("feedback", q.getFeedback());
        map.put("masteryBefore", q.getMasteryBefore());
        map.put("masteryAfter", q.getMasteryAfter());
        map.put("followUps", q.getFollowUps());
        map.put("currentFollowUpIdx", q.getCurrentFollowUpIdx());
        map.put("hintsGiven", q.getHintsGiven());
        map.put("status", q.getStatus().getValue());
        map.put("answeredAt", q.getAnsweredAt() != null ? q.getAnsweredAt().toString() : null);
        return map;
    }

    private InterviewQuestion mapToQuestion(Map<String, Object> map) {
        String answeredAtStr = (String) map.get("answeredAt");
        LocalDateTime answeredAt = answeredAtStr != null ? LocalDateTime.parse(answeredAtStr) : null;

        return InterviewQuestion.rebuild(
            (String) map.get("questionId"),
            (String) map.get("questionText"),
            (String) map.get("questionType"),
            DifficultyLevel.fromValue((String) map.get("difficulty")),
            (List<String>) map.get("knowledgePoints"),
            (String) map.get("userAnswer"),
            (Integer) map.get("score"),
            (String) map.get("feedback"),
            (Integer) map.get("masteryBefore"),
            (Integer) map.get("masteryAfter"),
            (List<String>) map.get("followUps"),
            (Integer) map.get("currentFollowUpIdx"),
            (List<String>) map.get("hintsGiven"),
            QuestionStatus.fromValue((String) map.get("status")),
            answeredAt
        );
    }
}