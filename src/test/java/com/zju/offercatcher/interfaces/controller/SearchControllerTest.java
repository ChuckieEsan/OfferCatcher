package com.zju.offercatcher.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.service.RetrievalApplicationService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @MockitoBean
    RetrievalApplicationService retrievalService;

    @Nested
    @DisplayName("POST /api/v1/search")
    class Search {

        @Test
        @DisplayName("搜索成功返回 200")
        void searchSuccess() throws Exception {
            RetrievalApplicationService.SearchResult result = new RetrievalApplicationService.SearchResult(
                    "q1", "HashMap 实现原理？", "阿里巴巴", "Java 后端",
                    "LEVEL_1", "knowledge", List.of("HashMap"), List.of("c1"),
                    "HashMap 基于数组+链表...", Map.of(), 0.92f
            );
            when(retrievalService.searchWithRerank(anyString(), anyString(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenReturn(List.of(result));

            String body = mapper.writeValueAsString(Map.of(
                    "query", "HashMap",
                    "company", "阿里巴巴",
                    "k", 10,
                    "scoreThreshold", 0.3
            ));

            mvc.perform(post("/api/v1/search")
                            .header("X-User-Id", "user-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.length()").value(1))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.results[0].questionId").value("q1"))
                    .andExpect(jsonPath("$.results[0].score").value(0.92));
        }

        @Test
        @DisplayName("空结果返回 200")
        void emptyResult() throws Exception {
            when(retrievalService.searchWithRerank(anyString(), anyString(), any(), any(), anyInt(), anyInt(), any(), any(), any(), any()))
                    .thenReturn(List.of());

            String body = mapper.writeValueAsString(Map.of(
                    "query", "nonexistent", "k", 10, "scoreThreshold", 0.3
            ));

            mvc.perform(post("/api/v1/search")
                            .header("X-User-Id", "user-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.results.length()").value(0));
        }
    }
}
