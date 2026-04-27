package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.chat.repositories.ConversationRepository;
import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.domain.shared.exception.ConversationNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatApplicationServiceTest {

    @Mock ConversationRepository conversationRepository;
    @InjectMocks ChatApplicationService service;

    Conversation sample = Conversation.create("user-1", "测试对话");

    @Nested
    @DisplayName("createConversation")
    class Create {

        @Test
        @DisplayName("默认标题应为 新对话")
        void defaultTitle() {
            Conversation c = service.createConversation("user-1", null);
            assertThat(c.getTitle()).isEqualTo("新对话");
            verify(conversationRepository).save(any(Conversation.class));
        }
    }

    @Nested
    @DisplayName("getConversation")
    class Get {

        @Test
        @DisplayName("用户所有对话可访问")
        void ownedByUser() {
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(sample));
            assertThat(service.getConversation("user-1", 1L)).isPresent();
        }

        @Test
        @DisplayName("其他用户对话不可访问")
        void notOwnedByUser() {
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(sample));
            assertThat(service.getConversation("user-2", 1L)).isEmpty();
        }
    }

    @Nested
    @DisplayName("addMessage")
    class AddMessage {

        @Test
        @DisplayName("添加消息成功")
        void addSuccess() {
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(sample));
            Long msgId = service.addMessage("user-1", 1L, MessageRole.USER, "你好");
            assertThat(msgId).isNotNull();
            verify(conversationRepository).save(any(Conversation.class));
        }

        @Test
        @DisplayName("对话不存在抛异常")
        void conversationNotFound() {
            when(conversationRepository.findById(999L)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.addMessage("user-1", 999L, MessageRole.USER, "hi"))
                .isInstanceOf(ConversationNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("generateTitle")
    class GenerateTitle {

        @Test
        @DisplayName("已生成标题的对话跳过")
        void skipWhenTitled() {
            sample.updateTitle("已有标题");
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(sample));
            assertThat(service.generateTitle("user-1", 1L, msgs -> "新标题")).isEmpty();
        }

        @Test
        @DisplayName("消息不足 4 条跳过")
        void skipWhenInsufficientMessages() {
            Conversation conv = Conversation.create("user-1", "新对话");
            when(conversationRepository.findById(1L)).thenReturn(Optional.of(conv));
            assertThat(service.generateTitle("user-1", 1L, msgs -> "不会用")).isEmpty();
        }
    }
}
