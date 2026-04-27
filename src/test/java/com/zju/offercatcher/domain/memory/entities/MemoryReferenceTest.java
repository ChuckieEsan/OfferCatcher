package com.zju.offercatcher.domain.memory.entities;

import com.zju.offercatcher.domain.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * MemoryReference 实体测试
 */
@DisplayName("MemoryReference 实体测试")
class MemoryReferenceTest {

    private static final String REFERENCE_NAME = "preferences";
    private static final String CONTENT = "# 用户偏好\n- 喜欢使用 Java\n- 目标公司：阿里巴巴";

    @Nested
    @DisplayName("工厂方法 create")
    class CreateTests {

        @Test
        @DisplayName("应成功创建引用")
        void shouldCreateReferenceSuccessfully() {
            MemoryReference ref = MemoryReference.create(REFERENCE_NAME, CONTENT);

            assertThat(ref.getReferenceName()).isEqualTo(REFERENCE_NAME);
            assertThat(ref.getContent()).isEqualTo(CONTENT);
            assertThat(ref.getUpdatedAt()).isNotNull();
        }

        @Test
        @DisplayName("referenceName 为空应抛出异常")
        void shouldThrowExceptionWhenReferenceNameIsNull() {
            assertThatThrownBy(() -> MemoryReference.create(null, CONTENT))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("referenceName");
        }

        @Test
        @DisplayName("content 为空应抛出异常")
        void shouldThrowExceptionWhenContentIsNull() {
            assertThatThrownBy(() -> MemoryReference.create(REFERENCE_NAME, null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("content");
        }
    }

    @Nested
    @DisplayName("内容更新方法 updateContent")
    class UpdateContentTests {

        @Test
        @DisplayName("应成功更新内容")
        void shouldUpdateContentSuccessfully() {
            MemoryReference ref = MemoryReference.create(REFERENCE_NAME, CONTENT);
            LocalDateTime before = ref.getUpdatedAt();

            ref.updateContent("# 新偏好\n- 喜欢使用 Python");

            assertThat(ref.getContent()).contains("Python");
            assertThat(ref.getUpdatedAt()).isAfter(before);
        }

        @Test
        @DisplayName("新内容为空应抛出异常")
        void shouldThrowExceptionWhenNewContentIsNull() {
            MemoryReference ref = MemoryReference.create(REFERENCE_NAME, CONTENT);

            assertThatThrownBy(() -> ref.updateContent(null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("content");
        }
    }
}