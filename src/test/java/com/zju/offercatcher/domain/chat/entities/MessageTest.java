package com.zju.offercatcher.domain.chat.entities;

import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Message 实体测试
 * <p>
 * 测试消息的核心业务逻辑：
 * - 工厂方法创建
 * - 参数校验
 * - 不可变性
 * - 角色判断
 */
@DisplayName("Message 实体测试")
class MessageTest {

    private static final Long MESSAGE_ID = 1L;
    private static final String CONTENT = "Hello, this is a test message";

    @Nested
    @DisplayName("工厂方法 create")
    class CreateTests {

        @Test
        @DisplayName("应成功创建用户消息")
        void shouldCreateUserMessageSuccessfully() {
            Message message = Message.create(MESSAGE_ID, MessageRole.USER, CONTENT);

            assertThat(message.getMessageId()).isEqualTo(MESSAGE_ID);
            assertThat(message.getRole()).isEqualTo(MessageRole.USER);
            assertThat(message.getContent()).isEqualTo(CONTENT);
            assertThat(message.getCreatedAt()).isNotNull();
            assertThat(message.getCreatedAt()).isBeforeOrEqualTo(LocalDateTime.now());
        }

        @Test
        @DisplayName("应成功创建助手消息")
        void shouldCreateAssistantMessageSuccessfully() {
            Message message = Message.create(MESSAGE_ID, MessageRole.ASSISTANT, CONTENT);

            assertThat(message.getRole()).isEqualTo(MessageRole.ASSISTANT);
        }

        @Test
        @DisplayName("messageId 为空应抛出异常")
        void shouldThrowExceptionWhenMessageIdIsNull() {
            assertThatThrownBy(() -> Message.create(null, MessageRole.USER, CONTENT))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("messageId");
        }

        @Test
        @DisplayName("role 为空应抛出异常")
        void shouldThrowExceptionWhenRoleIsNull() {
            assertThatThrownBy(() -> Message.create(MESSAGE_ID, null, CONTENT))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("role");
        }

        @Test
        @DisplayName("content 为空应抛出异常")
        void shouldThrowExceptionWhenContentIsNull() {
            assertThatThrownBy(() -> Message.create(MESSAGE_ID, MessageRole.USER, null))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("content");
        }

        @Test
        @DisplayName("content 为空白应抛出异常")
        void shouldThrowExceptionWhenContentIsBlank() {
            assertThatThrownBy(() -> Message.create(MESSAGE_ID, MessageRole.USER, "   "))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("content");
        }
    }

    @Nested
    @DisplayName("工厂方法 rebuild")
    class RebuildTests {

        @Test
        @DisplayName("应成功重建消息")
        void shouldRebuildMessageSuccessfully() {
            LocalDateTime createdAt = LocalDateTime.now().minusHours(1);

            Message message = Message.rebuild(MESSAGE_ID, MessageRole.USER, CONTENT, null, null, createdAt);

            assertThat(message.getMessageId()).isEqualTo(MESSAGE_ID);
            assertThat(message.getRole()).isEqualTo(MessageRole.USER);
            assertThat(message.getContent()).isEqualTo(CONTENT);
            assertThat(message.getReasoning()).isNull();
            assertThat(message.getToolCalls()).isNull();
            assertThat(message.getCreatedAt()).isEqualTo(createdAt);
        }
    }

    @Nested
    @DisplayName("角色判断方法")
    class RoleCheckTests {

        @Test
        @DisplayName("isUserMessage 应返回 true")
        void shouldReturnTrueForUserMessage() {
            Message message = Message.create(MESSAGE_ID, MessageRole.USER, CONTENT);

            assertThat(message.isUserMessage()).isTrue();
            assertThat(message.isAssistantMessage()).isFalse();
        }

        @Test
        @DisplayName("isAssistantMessage 应返回 true")
        void shouldReturnTrueForAssistantMessage() {
            Message message = Message.create(MESSAGE_ID, MessageRole.ASSISTANT, CONTENT);

            assertThat(message.isAssistantMessage()).isTrue();
            assertThat(message.isUserMessage()).isFalse();
        }
    }
}