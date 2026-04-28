package com.zju.offercatcher.interfaces.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.service.FavoriteApplicationService;
import com.zju.offercatcher.domain.favorite.aggregates.Favorite;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FavoriteController.class)
class FavoriteControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean FavoriteApplicationService favoriteService;

    Favorite sampleFavorite = Favorite.create("user-1", 1L);

    @Nested
    @DisplayName("POST /api/v1/favorites")
    class AddFavorite {

        @Test
        @DisplayName("添加收藏成功返回 201")
        void addSuccess() throws Exception {
            when(favoriteService.addFavorite(anyString(), anyLong())).thenReturn(sampleFavorite);

            String body = mapper.writeValueAsString(Map.of("questionId", 1));

            mvc.perform(post("/api/v1/favorites")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.favoriteId").exists());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/favorites")
    class ListFavorites {

        @Test
        @DisplayName("列表查询返回 200")
        void listSuccess() throws Exception {
            when(favoriteService.listFavorites(anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(sampleFavorite));

            mvc.perform(get("/api/v1/favorites")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorites.length()").value(1));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/favorites/{id}")
    class RemoveFavorite {

        @Test
        @DisplayName("删除成功返回 204")
        void removeSuccess() throws Exception {
            mvc.perform(delete("/api/v1/favorites/1")
                    .header("X-User-Id", "user-1"))
                .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/favorites/check")
    class CheckFavorited {

        @Test
        @DisplayName("检查收藏状态返回 200")
        void checkSuccess() throws Exception {
            when(favoriteService.checkFavorited(anyString(), anyList()))
                .thenReturn(Map.of(1L, true, 2L, false));

            String body = mapper.writeValueAsString(Map.of("questionIds", List.of(1, 2)));

            mvc.perform(post("/api/v1/favorites/check")
                    .header("X-User-Id", "user-1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.favorited['1']").value(true))
                .andExpect(jsonPath("$.favorited['2']").value(false));
        }
    }
}
