package com.zju.offercatcher.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.agent.ScorerAgent;
import com.zju.offercatcher.application.agent.dto.ScoreResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScoreController.class)
class ScoreControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ScorerAgent scorerAgent;

    private final ScoreResult sampleResult = new ScoreResult(
        1L, "HashMap 实现原理？", "基于数组+链表", "数组+链表",
        85, "LEVEL_2",
        List.of("概念准确"), List.of("缺少扩容说明"),
        "回答较完整，建议补充扩容机制");

    @Nested
    @DisplayName("POST /api/v1/score")
    class ScoreAnswer {

        @Test
        @DisplayName("评分成功返回 200")
        void scoreSuccess() throws Exception {
            when(scorerAgent.score(1L, "数组+链表")).thenReturn(sampleResult);

            String body = mapper.writeValueAsString(Map.of(
                "questionId", 1,
                "userAnswer", "数组+链表"));

            mvc.perform(post("/api/v1/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(85))
                .andExpect(jsonPath("$.masteryLevel").value("LEVEL_2"))
                .andExpect(jsonPath("$.strengths[0]").value("概念准确"))
                .andExpect(jsonPath("$.feedback").value("回答较完整，建议补充扩容机制"));
        }

        @Test
        @DisplayName("题目不存在返回 404")
        void questionNotFound() throws Exception {
            when(scorerAgent.score(anyLong(), anyString()))
                .thenThrow(new NoSuchElementException("Question not found: nonexistent"));

            String body = mapper.writeValueAsString(Map.of(
                "questionId", 999,
                "userAnswer", "test"));

            mvc.perform(post("/api/v1/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("缺少必填字段返回 400")
        void missingFields() throws Exception {
            String body = mapper.writeValueAsString(Map.of(
                "questionId", 1));

            mvc.perform(post("/api/v1/score")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }
    }
}
