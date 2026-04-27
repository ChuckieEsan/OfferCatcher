package com.zju.offercatcher.domain.memory.aggregates;

import com.zju.offercatcher.domain.memory.entities.MemoryReference;
import com.zju.offercatcher.domain.shared.enums.MemoryStatus;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Memory 聚合根测试
 */
@DisplayName("Memory 聚合根测试")
class MemoryTest {

    private static final String USER_ID = "user-001";
    private static final String CONTENT = "# 用户记忆\n\n## 基本信息\n- 目标岗位：Java 开发\n- 目标公司：阿里巴巴";

    @Nested
    @DisplayName("工厂方法 create")
    class CreateTests {

        @Test
        @DisplayName("应成功创建记忆")
        void shouldCreateMemorySuccessfully() {
            Memory memory = Memory.create(USER_ID, CONTENT);

            assertThat(memory.getUserId()).isEqualTo(USER_ID);
            assertThat(memory.getContent()).isEqualTo(CONTENT);
            assertThat(memory.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
            assertThat(memory.getReferences()).isEmpty();
            assertThat(memory.isActive()).isTrue();
            assertThat(memory.getCreatedAt()).isNotNull();
        }

        @Test
        @DisplayName("userId 为空应抛出异常")
        void shouldThrowExceptionWhenUserIdIsNull() {
            assertThatThrownBy(() -> Memory.create(null, CONTENT))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("content 为空应抛出异常")
        void shouldThrowExceptionWhenContentIsNull() {
            assertThatThrownBy(() -> Memory.create(USER_ID, null))
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
            Memory memory = Memory.create(USER_ID, CONTENT);
            LocalDateTime before = memory.getUpdatedAt();

            memory.updateContent("# 新记忆\n\n## 更新信息");

            assertThat(memory.getContent()).contains("新记忆");
            assertThat(memory.getUpdatedAt()).isAfter(before);
        }
    }

    @Nested
    @DisplayName("引用操作")
    class ReferenceTests {

        @Test
        @DisplayName("应成功添加引用")
        void shouldAddReferenceSuccessfully() {
            Memory memory = Memory.create(USER_ID, CONTENT);
            MemoryReference ref = MemoryReference.create("preferences", "# 偏好设置");

            memory.addReference(ref);

            assertThat(memory.getReferences()).hasSize(1);
            assertThat(memory.getReference("preferences")).isPresent();
        }

        @Test
        @DisplayName("同名引用应更新内容")
        void shouldUpdateExistingReference() {
            Memory memory = Memory.create(USER_ID, CONTENT);
            MemoryReference ref1 = MemoryReference.create("preferences", "# 偏好设置1");
            MemoryReference ref2 = MemoryReference.create("preferences", "# 偏好设置2");

            memory.addReference(ref1);
            memory.addReference(ref2);

            assertThat(memory.getReferences()).hasSize(1);
            assertThat(memory.getReference("preferences").get().getContent()).contains("偏好设置2");
        }

        @Test
        @DisplayName("应正确获取引用")
        void shouldGetReferenceCorrectly() {
            Memory memory = Memory.create(USER_ID, CONTENT);
            memory.addReference(MemoryReference.create("preferences", "# 偏好"));
            memory.addReference(MemoryReference.create("behaviors", "# 行为"));

            assertThat(memory.getReference("preferences")).isPresent();
            assertThat(memory.getReference("behaviors")).isPresent();
            assertThat(memory.getReference("nonexistent")).isEmpty();
        }

        @Test
        @DisplayName("应成功移除引用")
        void shouldRemoveReferenceSuccessfully() {
            Memory memory = Memory.create(USER_ID, CONTENT);
            memory.addReference(MemoryReference.create("preferences", "# 偏好"));

            boolean removed = memory.removeReference("preferences");

            assertThat(removed).isTrue();
            assertThat(memory.getReferences()).isEmpty();
        }

        @Test
        @DisplayName("移除不存在引用应返回 false")
        void shouldReturnFalseWhenRemovingNonexistentReference() {
            Memory memory = Memory.create(USER_ID, CONTENT);

            boolean removed = memory.removeReference("nonexistent");

            assertThat(removed).isFalse();
        }
    }

    @Nested
    @DisplayName("状态管理")
    class StatusTests {

        @Test
        @DisplayName("应成功归档记忆")
        void shouldArchiveMemorySuccessfully() {
            Memory memory = Memory.create(USER_ID, CONTENT);

            memory.archive();

            assertThat(memory.getStatus()).isEqualTo(MemoryStatus.ARCHIVED);
            assertThat(memory.isArchived()).isTrue();
            assertThat(memory.isActive()).isFalse();
        }

        @Test
        @DisplayName("应成功恢复记忆")
        void shouldRestoreMemorySuccessfully() {
            Memory memory = Memory.create(USER_ID, CONTENT);
            memory.archive();

            memory.restore();

            assertThat(memory.getStatus()).isEqualTo(MemoryStatus.ACTIVE);
            assertThat(memory.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("用户隔离方法 isOwnedBy")
    class IsOwnedByTests {

        @Test
        @DisplayName("所有者应返回 true")
        void shouldReturnTrueForOwner() {
            Memory memory = Memory.create(USER_ID, CONTENT);

            assertThat(memory.isOwnedBy(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("非所有者应返回 false")
        void shouldReturnFalseForNonOwner() {
            Memory memory = Memory.create(USER_ID, CONTENT);

            assertThat(memory.isOwnedBy("other-user")).isFalse();
        }
    }
}