package com.zju.offercatcher.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.service.ChatApplicationService;
import com.zju.offercatcher.application.agent.ChatAgentService;
import com.zju.offercatcher.domain.chat.aggregates.Conversation;
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

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean ChatApplicationService chatService;
    @MockitoBean ChatAgentService chatAgent;

    Conversation sampleConv = Conversation.create("user-1", "测试对话");

    @Nested
    @DisplayName("POST /api/v1/conversations")
    class CreateConversation {

        @Test
        @DisplayName("创建成功返回 200")
        void createSuccess() throws Exception {
            when(chatService.createConversation(anyString(), anyString())).thenReturn(sampleConv);

            String body = mapper.writeValueAsString(Map.of("title", "技术面试"));

            mvc.perform(post("/api/v1/conversations")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/conversations")
    class ListConversations {

        @Test
        @DisplayName("列表查询返回 200")
        void listSuccess() throws Exception {
            when(chatService.listConversations(anyString(), anyInt()))
                .thenReturn(List.of(sampleConv));

            mvc.perform(get("/api/v1/conversations")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversations.length()").value(1));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/conversations/{id}")
    class GetConversation {

        @Test
        @DisplayName("对话存在返回 200")
        void getSuccess() throws Exception {
            when(chatService.getConversation("user-1", 1L)).thenReturn(Optional.of(sampleConv));

            mvc.perform(get("/api/v1/conversations/1")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("对话不存在返回 404")
        void notFound() throws Exception {
            when(chatService.getConversation(anyString(), eq(999L))).thenReturn(Optional.empty());

            mvc.perform(get("/api/v1/conversations/999")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("PUT /api/v1/conversations/{id}/title")
    class UpdateTitle {

        @Test
        @DisplayName("标题更新成功")
        void updateSuccess() throws Exception {
            when(chatService.updateTitle(anyString(), anyLong(), anyString())).thenReturn(true);

            String body = mapper.writeValueAsString(Map.of("title", "新标题"));

            mvc.perform(put("/api/v1/conversations/1/title")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk());
        }

        @Test
        @DisplayName("标题为空返回 400")
        void emptyTitle() throws Exception {
            String body = mapper.writeValueAsString(Map.of("title", ""));

            mvc.perform(put("/api/v1/conversations/1/title")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/conversations/{id}")
    class DeleteConversation {

        @Test
        @DisplayName("删除成功返回 204")
        void deleteSuccess() throws Exception {
            when(chatService.deleteConversation(anyString(), anyLong())).thenReturn(true);

            mvc.perform(delete("/api/v1/conversations/1")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isNoContent());
        }
    }
}
