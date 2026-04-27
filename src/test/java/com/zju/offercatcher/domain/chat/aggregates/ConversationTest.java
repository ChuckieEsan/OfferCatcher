package com.zju.offercatcher.domain.chat.aggregates;

import com.zju.offercatcher.domain.shared.enums.ConversationStatus;
import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.domain.shared.exception.InvalidStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;

/**
 * Conversation 聚合根测试
 *
 * 测试对话的核心业务逻辑：
 * - 工厂方法创建
 * - 消息追加
 * - 状态管理
 * - 用户隔离
 */
@DisplayName("Conversation 聚合根测试")
class ConversationTest {

    private static final String USER_ID = "user-001";
    private static final String TITLE = "测试对话";

    @Nested
    @DisplayName("工厂方法 create")
    class CreateTests {

        @Test
        @DisplayName("应成功创建对话")
        void shouldCreateConversationSuccessfully() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            assertThat(conversation.getUserId()).isEqualTo(USER_ID);
            assertThat(conversation.getTitle()).isEqualTo(TITLE);
            assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ACTIVE);
            assertThat(conversation.getConversationId()).isNotNull();
            assertThat(conversation.getCreatedAt()).isNotNull();
            assertThat(conversation.getUpdatedAt()).isNotNull();
            assertThat(conversation.getMessages()).isEmpty();
        }

        @Test
        @DisplayName("标题为空应使用默认标题")
        void shouldUseDefaultTitleWhenTitleIsNull() {
            Conversation conversation = Conversation.create(USER_ID, null);

            assertThat(conversation.getTitle()).isEqualTo("新对话");
        }

        @Test
        @DisplayName("标题为空白应使用默认标题")
        void shouldUseDefaultTitleWhenTitleIsBlank() {
            Conversation conversation = Conversation.create(USER_ID, "   ");

            assertThat(conversation.getTitle()).isEqualTo("新对话");
        }

        @Test
        @DisplayName("userId 为空应抛出异常")
        void shouldThrowExceptionWhenUserIdIsNull() {
            assertThatThrownBy(() -> Conversation.create(null, TITLE))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("userId 为空白应抛出异常")
        void shouldThrowExceptionWhenUserIdIsBlank() {
            assertThatThrownBy(() -> Conversation.create("", TITLE))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("userId");
        }
    }

    @Nested
    @DisplayName("消息追加方法 addMessage")
    class AddMessageTests {

        @Test
        @DisplayName("应成功追加用户消息")
        void shouldAddUserMessageSuccessfully() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            conversation.addMessage(MessageRole.USER, "你好");

            assertThat(conversation.getMessages()).hasSize(1);
            assertThat(conversation.getMessages().get(0).getRole()).isEqualTo(MessageRole.USER);
        }

        @Test
        @DisplayName("应成功追加助手消息")
        void shouldAddAssistantMessageSuccessfully() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            conversation.addMessage(MessageRole.ASSISTANT, "你好，有什么可以帮助你的？");

            assertThat(conversation.getMessages()).hasSize(1);
            assertThat(conversation.getMessages().get(0).getRole()).isEqualTo(MessageRole.ASSISTANT);
        }

        @Test
        @DisplayName("对已结束对话添加消息应抛出异常")
        void shouldThrowExceptionWhenAddingMessageToEndedConversation() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);
            conversation.end();

            assertThatThrownBy(() -> conversation.addMessage(MessageRole.USER, "你好"))
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("对话已结束");
        }

        @Test
        @DisplayName("追加消息应更新 updatedAt")
        void shouldUpdateUpdatedAtWhenAddingMessage() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);
            LocalDateTime before = conversation.getUpdatedAt();

            conversation.addMessage(MessageRole.USER, "你好");

            assertThat(conversation.getUpdatedAt()).isAfter(before);
        }
    }

    @Nested
    @DisplayName("标题更新方法 updateTitle")
    class UpdateTitleTests {

        @Test
        @DisplayName("应成功更新标题")
        void shouldUpdateTitleSuccessfully() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            conversation.updateTitle("新标题");

            assertThat(conversation.getTitle()).isEqualTo("新标题");
        }

        @Test
        @DisplayName("标题为空应抛出异常")
        void shouldThrowExceptionWhenTitleIsNull() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            assertThatThrownBy(() -> conversation.updateTitle(null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("标题");
        }
    }

    @Nested
    @DisplayName("结束对话方法 end")
    class EndTests {

        @Test
        @DisplayName("应成功结束对话")
        void shouldEndConversationSuccessfully() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            conversation.end();

            assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ENDED);
            assertThat(conversation.isEnded()).isTrue();
            assertThat(conversation.isActive()).isFalse();
        }

        @Test
        @DisplayName("重复结束应抛出异常")
        void shouldThrowExceptionWhenEndingAlreadyEndedConversation() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);
            conversation.end();

            assertThatThrownBy(() -> conversation.end())
                .isInstanceOf(InvalidStateException.class)
                .hasMessageContaining("已经结束");
        }
    }

    @Nested
    @DisplayName("用户隔离方法 isOwnedBy")
    class IsOwnedByTests {

        @Test
        @DisplayName("所有者应返回 true")
        void shouldReturnTrueForOwner() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            assertThat(conversation.isOwnedBy(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("非所有者应返回 false")
        void shouldReturnFalseForNonOwner() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            assertThat(conversation.isOwnedBy("other-user")).isFalse();
        }
    }

    @Nested
    @DisplayName("查询方法")
    class QueryTests {

        @Test
        @DisplayName("getLastMessage 应返回最后一条消息")
        void shouldReturnLastMessage() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);
            conversation.addMessage(MessageRole.USER, "消息1");
            conversation.addMessage(MessageRole.ASSISTANT, "消息2");

            assertThat(conversation.getLastMessage()).isPresent();
            assertThat(conversation.getLastMessage().get().getContent()).isEqualTo("消息2");
        }

        @Test
        @DisplayName("getLastMessage 空对话应返回空")
        void shouldReturnEmptyForEmptyConversation() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);

            assertThat(conversation.getLastMessage()).isEmpty();
        }

        @Test
        @DisplayName("getUserMessages 应只返回用户消息")
        void shouldReturnOnlyUserMessages() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);
            conversation.addMessage(MessageRole.USER, "用户消息");
            conversation.addMessage(MessageRole.ASSISTANT, "助手消息");
            conversation.addMessage(MessageRole.USER, "用户消息2");

            assertThat(conversation.getUserMessages()).hasSize(2);
        }

        @Test
        @DisplayName("getAssistantMessages 应只返回助手消息")
        void shouldReturnOnlyAssistantMessages() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);
            conversation.addMessage(MessageRole.USER, "用户消息");
            conversation.addMessage(MessageRole.ASSISTANT, "助手消息");
            conversation.addMessage(MessageRole.USER, "用户消息2");

            assertThat(conversation.getAssistantMessages()).hasSize(1);
        }

        @Test
        @DisplayName("messageCount 应返回消息总数")
        void shouldReturnMessageCount() {
            Conversation conversation = Conversation.create(USER_ID, TITLE);
            conversation.addMessage(MessageRole.USER, "消息1");
            conversation.addMessage(MessageRole.ASSISTANT, "消息2");

            assertThat(conversation.messageCount()).isEqualTo(2);
        }
    }
}