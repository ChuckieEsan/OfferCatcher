package com.zju.offercatcher.domain.favorite.aggregates;

import com.zju.offercatcher.domain.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Favorite 聚合根测试
 *
 * 测试收藏的核心业务逻辑：
 * - 工厂方法创建
 * - 用户隔离验证
 * - 参数校验
 * - 不可变性
 */
@DisplayName("Favorite 聚合根测试")
class FavoriteTest {

    private static final String USER_ID = "user-001";
    private static final Long QUESTION_ID = 1L;

    // ==================== 工厂方法测试 ====================

    @Nested
    @DisplayName("工厂方法 create")
    class CreateTests {

        @Test
        @DisplayName("应成功创建收藏")
        void shouldCreateFavoriteSuccessfully() {
            Favorite favorite = Favorite.create(USER_ID, QUESTION_ID);

            assertThat(favorite.getUserId()).isEqualTo(USER_ID);
            assertThat(favorite.getQuestionId()).isEqualTo(QUESTION_ID);
            assertThat(favorite.getFavoriteId()).isNotNull();
            assertThat(favorite.getCreatedAt()).isNotNull();
            assertThat(favorite.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("应生成有效的 Snowflake ID 作为 favoriteId")
        void shouldGenerateValidSnowflakeIdAsFavoriteId() {
            Favorite favorite = Favorite.create(USER_ID, QUESTION_ID);

            assertThat(favorite.getFavoriteId()).isNotNull();
            assertThat(favorite.getFavoriteId()).isPositive();
        }

        @Test
        @DisplayName("userId 为空应抛出异常")
        void shouldThrowExceptionWhenUserIdIsNull() {
            assertThatThrownBy(() -> Favorite.create(null, QUESTION_ID))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("userId 为空白应抛出异常")
        void shouldThrowExceptionWhenUserIdIsBlank() {
            assertThatThrownBy(() -> Favorite.create("", QUESTION_ID))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("questionId 为空应抛出异常")
        void shouldThrowExceptionWhenQuestionIdIsNull() {
            assertThatThrownBy(() -> Favorite.create(USER_ID, null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("questionId");
        }

        @Test
        @DisplayName("questionId 为空白应抛出异常")
        void shouldThrowExceptionWhenQuestionIdIsBlank() {
            assertThatThrownBy(() -> Favorite.create(USER_ID, (Long) null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("questionId");
        }
    }

    @Nested
    @DisplayName("工厂方法 createWithId")
    class CreateWithIdTests {

        @Test
        @DisplayName("应成功创建收藏（指定 ID）")
        void shouldCreateFavoriteWithSpecifiedId() {
            Long favoriteId = 1L;
            Favorite favorite = Favorite.createWithId(favoriteId, USER_ID, QUESTION_ID);

            assertThat(favorite.getFavoriteId()).isEqualTo(favoriteId);
            assertThat(favorite.getUserId()).isEqualTo(USER_ID);
            assertThat(favorite.getQuestionId()).isEqualTo(QUESTION_ID);
        }

        @Test
        @DisplayName("favoriteId 为空应抛出异常")
        void shouldThrowExceptionWhenFavoriteIdIsNull() {
            assertThatThrownBy(() -> Favorite.createWithId(null, USER_ID, QUESTION_ID))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("favoriteId");
        }

    }

    @Nested
    @DisplayName("工厂方法 rebuild")
    class RebuildTests {

        @Test
        @DisplayName("应成功重建收藏")
        void shouldRebuildFavoriteSuccessfully() {
            Long favoriteId = 1L;
            LocalDateTime createdAt = LocalDateTime.now().minusHours(1);

            Favorite favorite = Favorite.rebuild(favoriteId, USER_ID, QUESTION_ID, createdAt);

            assertThat(favorite.getFavoriteId()).isEqualTo(favoriteId);
            assertThat(favorite.getUserId()).isEqualTo(USER_ID);
            assertThat(favorite.getQuestionId()).isEqualTo(QUESTION_ID);
            assertThat(favorite.getCreatedAt()).isEqualTo(createdAt);
        }
    }

    // ==================== 用户隔离测试 ====================

    @Nested
    @DisplayName("用户隔离方法 isOwnedBy")
    class IsOwnedByTests {

        @Test
        @DisplayName("所有者应返回 true")
        void shouldReturnTrueForOwner() {
            Favorite favorite = Favorite.create(USER_ID, QUESTION_ID);

            assertThat(favorite.isOwnedBy(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("非所有者应返回 false")
        void shouldReturnFalseForNonOwner() {
            Favorite favorite = Favorite.create(USER_ID, QUESTION_ID);

            assertThat(favorite.isOwnedBy("other-user")).isFalse();
        }

        @Test
        @DisplayName("空用户 ID 应返回 false")
        void shouldReturnFalseForNullUserId() {
            Favorite favorite = Favorite.create(USER_ID, QUESTION_ID);

            assertThat(favorite.isOwnedBy(null)).isFalse();
        }
    }
}