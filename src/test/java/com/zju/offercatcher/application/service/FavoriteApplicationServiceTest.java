package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.favorite.aggregates.Favorite;
import com.zju.offercatcher.domain.favorite.repositories.FavoriteRepository;
import com.zju.offercatcher.domain.shared.exception.FavoriteNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FavoriteApplicationServiceTest {

    @Mock FavoriteRepository favoriteRepository;
    @InjectMocks FavoriteApplicationService service;

    @Nested
    @DisplayName("addFavorite")
    class Add {

        @Test
        @DisplayName("新收藏创建成功")
        void newFavorite() {
            when(favoriteRepository.findByUserIdAndQuestionId("user-1", "q1")).thenReturn(Optional.empty());
            Favorite result = service.addFavorite("user-1", "q1");
            assertThat(result).isNotNull();
            verify(favoriteRepository).save(any(Favorite.class));
        }

        @Test
        @DisplayName("已收藏返回已有记录")
        void existingFavorite() {
            Favorite existing = Favorite.create("user-1", "q1");
            when(favoriteRepository.findByUserIdAndQuestionId("user-1", "q1")).thenReturn(Optional.of(existing));
            assertThat(service.addFavorite("user-1", "q1")).isSameAs(existing);
            verify(favoriteRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("removeFavorite")
    class Remove {

        @Test
        @DisplayName("删除成功")
        void removeSuccess() {
            Favorite f = Favorite.create("user-1", "q1");
            when(favoriteRepository.findById(1L)).thenReturn(Optional.of(f));
            service.removeFavorite(1L, "user-1");
            verify(favoriteRepository).deleteById(1L, "user-1");
        }

        @Test
        @DisplayName("收藏不存在抛异常")
        void notFound() {
            when(favoriteRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.removeFavorite(999L, "user-1"))
                .isInstanceOf(FavoriteNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("checkFavorited")
    class Check {

        @Test
        @DisplayName("批量检查收藏状态")
        void batchCheck() {
            when(favoriteRepository.existsByUserIdAndQuestionId("user-1", "q1")).thenReturn(true);
            when(favoriteRepository.existsByUserIdAndQuestionId("user-1", "q2")).thenReturn(false);

            Map<String, Boolean> result = service.checkFavorited("user-1", List.of("q1", "q2"));
            assertThat(result).containsEntry("q1", true).containsEntry("q2", false);
        }
    }
}
