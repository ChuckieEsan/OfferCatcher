package com.zju.offercatcher.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.service.QuestionApplicationService;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.interfaces.config.GlobalExceptionHandler;
import com.zju.offercatcher.interfaces.config.UserIdResolver;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QuestionController.class)
class QuestionControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean QuestionApplicationService questionService;

    Question sampleQuestion = Question.createPrivate(
        "user-1", "HashMap 的实现原理？", "阿里巴巴", "Java", QuestionType.KNOWLEDGE, List.of("HashMap"));

    @Nested
    @DisplayName("POST /api/v1/questions")
    class CreateQuestion {

        @Test
        @DisplayName("创建成功返回 201")
        void createSuccess() throws Exception {
            when(questionService.createQuestion(anyString(), anyString(), anyString(), anyString(),
                any(), anyList())).thenReturn(sampleQuestion);

            String body = mapper.writeValueAsString(Map.of(
                "questionText", "HashMap 的实现原理？",
                "company", "阿里巴巴",
                "position", "Java",
                "questionType", "knowledge",
                "coreEntities", List.of("HashMap")
            ));

            mvc.perform(post("/api/v1/questions")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.questionId").exists())
                .andExpect(jsonPath("$.company").value("阿里巴巴"));
        }

        @Test
        @DisplayName("缺少 X-User-Id 返回 400")
        void missingUserId() throws Exception {
            String body = mapper.writeValueAsString(Map.of(
                "questionText", "Test", "company", "A", "position", "B", "questionType", "knowledge"));

            mvc.perform(post("/api/v1/questions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/questions/{id}")
    class GetQuestion {

        @Test
        @DisplayName("题目存在返回 200")
        void getSuccess() throws Exception {
            when(questionService.getQuestion("q1")).thenReturn(Optional.of(sampleQuestion));

            mvc.perform(get("/api/v1/questions/q1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questionId").exists());
        }

        @Test
        @DisplayName("题目不存在返回 404")
        void notFound() throws Exception {
            when(questionService.getQuestion("nonexistent")).thenReturn(Optional.empty());

            mvc.perform(get("/api/v1/questions/nonexistent"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/questions/{id}")
    class UpdateQuestion {

        @Test
        @DisplayName("更新成功返回 200")
        void updateSuccess() throws Exception {
            when(questionService.updateQuestion(eq("q1"), anyString(), any()))
                .thenReturn(Optional.of(sampleQuestion));

            String body = mapper.writeValueAsString(Map.of("answer", "新答案"));

            mvc.perform(put("/api/v1/questions/q1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/questions")
    class ListQuestions {

        @Test
        @DisplayName("列表查询返回 200")
        void listSuccess() throws Exception {
            when(questionService.listQuestions(anyString(), any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(sampleQuestion));

            mvc.perform(get("/api/v1/questions")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.questions.length()").value(1));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/questions/batch/answers")
    class BatchAnswers {

        @Test
        @DisplayName("批量查询答案返回 200")
        void batchSuccess() throws Exception {
            when(questionService.getBatchAnswers(anyList())).thenReturn(Map.of("q1", "答案1"));

            String body = mapper.writeValueAsString(Map.of("questionIds", List.of("q1", "q2")));

            mvc.perform(post("/api/v1/questions/batch/answers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.q1").value("答案1"));
        }
    }
}
