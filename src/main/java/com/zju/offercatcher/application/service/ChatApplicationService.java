package com.zju.offercatcher.application.service;

import com.zju.offercatcher.application.agent.TitleGeneratorAgent;
import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.chat.repositories.ConversationRepository;
import com.zju.offercatcher.domain.shared.enums.ConversationStatus;
import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.domain.shared.exception.ConversationNotFoundException;
import com.zju.offercatcher.infrastructure.common.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * 对话应用服务
 *
 * 编排对话管理的用例：创建、查询、更新、删除对话，管理消息。
 * 对应 Python: app/application/services/chat_service.py
 */
@Service
public class ChatApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ChatApplicationService.class);

    private final ConversationRepository conversationRepository;
    private final TitleGeneratorAgent titleGenerator;

    public ChatApplicationService(ConversationRepository conversationRepository,
                                   TitleGeneratorAgent titleGenerator) {
        this.conversationRepository = conversationRepository;
        this.titleGenerator = titleGenerator;
    }

    @Transactional
    public Conversation createConversation(String userId, String title) {
        Conversation conversation = Conversation.create(userId, title != null ? title : "新对话");
        conversationRepository.save(conversation);
        log.info("Created conversation: {} for user={}", conversation.getConversationId(), userId);
        return conversation;
    }

    public List<Conversation> listConversations(String userId, int page, int pageSize) {
        return conversationRepository.findByUserId(userId, page, pageSize);
    }

    public long countConversations(String userId) {
        return conversationRepository.countByUserId(userId);
    }

    public Optional<Conversation> getConversation(String userId, Long conversationId) {
        return conversationRepository.findById(conversationId)
            .filter(c -> c.isOwnedBy(userId));
    }

    @Transactional
    public boolean updateTitle(String userId, Long conversationId, String title) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .filter(c -> c.isOwnedBy(userId))
            .orElse(null);
        if (conversation == null) {
            log.warn("Conversation not found: {}", conversationId);
            return false;
        }
        conversation.updateTitle(title);
        conversationRepository.save(conversation);
        log.info("Updated title for conversation: {}", conversationId);
        return true;
    }

    @Transactional
    public boolean deleteConversation(String userId, Long conversationId) {
        if (conversationRepository.findById(conversationId).isEmpty()) {
            return false;
        }
        conversationRepository.deleteById(conversationId, userId);
        log.info("Deleted conversation: {}", conversationId);
        return true;
    }

    @Transactional
    public Long addMessage(String userId, Long conversationId, MessageRole role, String content) {
        return addMessage(userId, conversationId, role, content, null, null);
    }

    @Transactional
    public Long addMessage(String userId, Long conversationId, MessageRole role, String content, String reasoning) {
        return addMessage(userId, conversationId, role, content, reasoning, null);
    }

    @Transactional
    public Long addMessage(String userId, Long conversationId, MessageRole role, String content, String reasoning, String toolCalls) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .filter(c -> c.isOwnedBy(userId))
            .orElseThrow(() -> new ConversationNotFoundException(conversationId));

        Message message = conversation.addMessage(
            SnowflakeIdGenerator.generate(), role, content, reasoning, toolCalls);
        conversationRepository.save(conversation);
        log.info("Added message {} to conversation: {}", message.getMessageId(), conversationId);
        return message.getMessageId();
    }

    public Optional<String> generateTitle(String userId, Long conversationId,
                                           Function<List<Message>, String> titleGenerator) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .filter(c -> c.isOwnedBy(userId))
            .orElse(null);

        if (conversation == null) {
            return Optional.empty();
        }
        if (!"新对话".equals(conversation.getTitle())) {
            return Optional.empty();
        }
        if (conversation.getMessages().size() < 4) {
            return Optional.empty();
        }

        String newTitle = titleGenerator.apply(conversation.getMessages());
        conversation.updateTitle(newTitle);
        conversationRepository.save(conversation);
        log.info("Title generated for conversation {}: {}", conversationId, newTitle);
        return Optional.of(newTitle);
    }

    @Transactional
    public Optional<Conversation> generateTitle(String userId, Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
            .filter(c -> c.isOwnedBy(userId))
            .orElse(null);

        if (conversation == null) {
            log.warn("Conversation not found: {}", conversationId);
            return Optional.empty();
        }
        if (conversation.getMessages().isEmpty()) {
            log.warn("Conversation {} has no messages", conversationId);
            return Optional.empty();
        }

        String newTitle = titleGenerator.generateTitle(conversation.getMessages());
        conversation.updateTitle(newTitle);
        conversationRepository.save(conversation);
        log.info("Title generated for conversation {}: {}", conversationId, newTitle);
        return Optional.of(conversation);
    }
}
