package com.zju.offercatcher.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.service.MemoryApplicationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MemoryController.class)
class MemoryControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean MemoryApplicationService memoryService;

    @Nested
    @DisplayName("GET /api/v1/memory/me")
    class GetMemory {

        @Test
        @DisplayName("获取记忆返回 200")
        void getSuccess() throws Exception {
            when(memoryService.getMemoryContent(anyString())).thenReturn("## 用户记忆\n偏好：中文");
            when(memoryService.getPreferences(anyString())).thenReturn("语言：中文");
            when(memoryService.getBehaviors(anyString())).thenReturn("喜欢详细解释");

            mvc.perform(get("/api/v1/memory/me")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.preferences").exists())
                .andExpect(jsonPath("$.behaviors").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/memory/me/preferences")
    class GetPreferences {

        @Test
        @DisplayName("获取偏好返回 200")
        void getSuccess() throws Exception {
            when(memoryService.getPreferences("user-1")).thenReturn("语言：中文\n解释深度：详细");

            mvc.perform(get("/api/v1/memory/me/preferences")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/memory/me/preferences")
    class UpdatePreferences {

        @Test
        @DisplayName("更新偏好返回 200")
        void updateSuccess() throws Exception {
            String body = mapper.writeValueAsString(Map.of("content", "语言：中文\n解释深度：适中"));

            mvc.perform(put("/api/v1/memory/me/preferences")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/memory/me/behaviors")
    class GetBehaviors {

        @Test
        @DisplayName("获取行为模式返回 200")
        void getSuccess() throws Exception {
            when(memoryService.getBehaviors("user-1")).thenReturn("频繁提问系统设计");

            mvc.perform(get("/api/v1/memory/me/behaviors")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/memory/me/behaviors")
    class UpdateBehaviors {

        @Test
        @DisplayName("更新行为模式返回 200")
        void updateSuccess() throws Exception {
            String body = mapper.writeValueAsString(Map.of("content", "偏好系统设计题型"));

            mvc.perform(put("/api/v1/memory/me/behaviors")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk());
        }
    }
}
