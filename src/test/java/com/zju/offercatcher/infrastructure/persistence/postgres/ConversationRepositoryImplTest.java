package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.shared.enums.ConversationStatus;
import com.zju.offercatcher.domain.shared.enums.MessageRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ConversationRepositoryImpl.class})
class ConversationRepositoryImplTest {

    @Autowired ConversationRepositoryImpl repo;
    @Autowired ConversationJpaRepository jpaRepo;

    @Test
    @DisplayName("save 应持久化对话及其消息")
    void save() {
        Conversation c = Conversation.create("user-1", "技术面试");
        c.addMessage(MessageRole.USER, "HashMap 的原理？");
        c.addMessage(MessageRole.ASSISTANT, "HashMap 基于数组+链表...");
        repo.save(c);

        Optional<ConversationJpaEntity> saved = jpaRepo.findById(c.getConversationId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getTitle()).isEqualTo("技术面试");
    }

    @Test
    @DisplayName("findById 应重建完整 Conversation")
    void findById() {
        Conversation c = Conversation.create("user-1", "测试对话");
        c.addMessage(MessageRole.USER, "你好");
        repo.save(c);

        Optional<Conversation> result = repo.findById(c.getConversationId());
        assertThat(result).isPresent();
        assertThat(result.get().getMessages()).hasSize(1);
        assertThat(result.get().getMessages().getFirst().getContent()).isEqualTo("你好");
    }

    @Test
    @DisplayName("findByUserId 应分页返回用户对话")
    void findByUserId() {
        repo.save(Conversation.create("user-1", "对话1"));
        repo.save(Conversation.create("user-1", "对话2"));
        repo.save(Conversation.create("user-2", "对话3"));

        List<Conversation> result = repo.findByUserId("user-1", 0, 10);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("deleteById 应删除且验证所有权")
    void deleteById() {
        Conversation c = Conversation.create("user-1", "待删除");
        repo.save(c);

        repo.deleteById(c.getConversationId(), "user-1");
        assertThat(jpaRepo.findById(c.getConversationId())).isEmpty();
    }
}
