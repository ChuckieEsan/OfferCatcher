package com.zju.offercatcher.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.agent.InterviewAgent;
import com.zju.offercatcher.application.service.InterviewApplicationService;
import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
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

@WebMvcTest(InterviewController.class)
class InterviewControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean InterviewApplicationService interviewService;
    @MockitoBean InterviewAgent interviewAgent;

    InterviewSession sampleSession = InterviewSession.create(
        "user-1", "字节跳动", "Java 后端", DifficultyLevel.MEDIUM, 5);

    @Nested
    @DisplayName("POST /api/v1/interview/sessions")
    class CreateSession {

        @Test
        @DisplayName("创建成功返回 200")
        void createSuccess() throws Exception {
            when(interviewAgent.createSession(anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(sampleSession);

            String body = mapper.writeValueAsString(Map.of(
                "company", "字节跳动",
                "position", "Java 后端",
                "difficulty", "medium",
                "totalQuestions", 5
            ));

            mvc.perform(post("/api/v1/interview/sessions")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/interview/sessions")
    class ListSessions {

        @Test
        @DisplayName("列表查询返回 200")
        void listSuccess() throws Exception {
            when(interviewService.listSessions(anyString(), anyInt(), any()))
                .thenReturn(List.of(sampleSession));

            mvc.perform(get("/api/v1/interview/sessions")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/interview/sessions/{id}")
    class GetSession {

        @Test
        @DisplayName("会话存在返回 200")
        void getSuccess() throws Exception {
            when(interviewService.getSession(1L, "user-1")).thenReturn(Optional.of(sampleSession));

            mvc.perform(get("/api/v1/interview/sessions/1")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("会话不存在返回 404")
        void notFound() throws Exception {
            when(interviewService.getSession(eq(999L), anyString())).thenReturn(Optional.empty());

            mvc.perform(get("/api/v1/interview/sessions/999")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/interview/sessions/{id}/skip")
    class SkipQuestion {

        @Test
        @DisplayName("跳过成功返回 200")
        void skipSuccess() throws Exception {
            when(interviewService.skipQuestion(anyLong(), anyString())).thenReturn(sampleSession);

            mvc.perform(post("/api/v1/interview/sessions/1/skip")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/interview/sessions/{id}/pause")
    class PauseSession {

        @Test
        @DisplayName("暂停成功返回 200")
        void pauseSuccess() throws Exception {
            when(interviewService.pauseSession(anyLong(), anyString())).thenReturn(sampleSession);

            mvc.perform(post("/api/v1/interview/sessions/1/pause")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/interview/sessions/{id}/end")
    class EndSession {

        @Test
        @DisplayName("结束成功返回 200")
        void endSuccess() throws Exception {
            when(interviewService.completeSession(anyLong(), anyString())).thenReturn(sampleSession);

            mvc.perform(post("/api/v1/interview/sessions/1/end")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/interview/sessions/{id}/report")
    class GetReport {

        @Test
        @DisplayName("报告存在返回 200")
        void reportSuccess() throws Exception {
            when(interviewService.getSession(1L, "user-1")).thenReturn(Optional.of(sampleSession));

            mvc.perform(get("/api/v1/interview/sessions/1/report")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/interview/sessions/{id}")
    class DeleteSession {

        @Test
        @DisplayName("删除成功返回 204")
        void deleteSuccess() throws Exception {
            when(interviewService.deleteSession(anyLong(), anyString())).thenReturn(true);

            mvc.perform(delete("/api/v1/interview/sessions/1")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isNoContent());
        }
    }
}
