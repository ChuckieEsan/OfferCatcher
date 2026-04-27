package com.zju.offercatcher.domain.chat.aggregates;

import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.shared.enums.ConversationStatus;
import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.domain.shared.exception.InvalidStateException;
import com.zju.offercatcher.infrastructure.common.SnowflakeIdGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 对话聚合根
 *
 * Conversation 是对话领域的聚合根，管理：
 * - 对话元数据（标题、状态）
 * - 聚合内的消息列表
 *
 * 聚合边界规则：
 * - 所有消息操作必须通过 Conversation 方法
 * - 消息创建后不可修改/删除
 * - 对话结束时状态变为 ENDED
 * - 支持用户隔离（userId 字段）
 */
public class Conversation {

    private final Long conversationId;
    private final String userId;
    private String title;
    private ConversationStatus status;
    private final List<Message> messages;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建新对话（工厂方法）
     */
    public static Conversation create(String userId, String title) {
        validateUserId(userId);
        Long conversationId = SnowflakeIdGenerator.generate();
        LocalDateTime now = LocalDateTime.now();
        return new Conversation(conversationId, userId, title, ConversationStatus.ACTIVE,
            new ArrayList<>(), now, now);
    }

    /**
     * 创建新对话（指定 ID）
     */
    public static Conversation createWithId(Long conversationId, String userId, String title) {
        validateUserId(userId);
        validateConversationId(conversationId);
        LocalDateTime now = LocalDateTime.now();
        return new Conversation(conversationId, userId, title, ConversationStatus.ACTIVE,
            new ArrayList<>(), now, now);
    }

    /**
     * 从持久化存储重建对话（用于 Repository 实现）
     */
    public static Conversation rebuild(Long conversationId, String userId, String title,
                                        ConversationStatus status, List<Message> messages,
                                        LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Conversation(conversationId, userId, title, status,
            new ArrayList<>(messages), createdAt, updatedAt);
    }

    // ==================== 业务方法 ====================

    /**
     * 追加消息（聚合内操作）
     */
    public Message addMessage(Long messageId, MessageRole role, String content) {
        if (status == ConversationStatus.ENDED) {
            throw new InvalidStateException("对话已结束，无法添加消息", "CONVERSATION_ENDED");
        }
        Message message = Message.create(messageId, role, content);
        this.messages.add(message);
        this.updatedAt = LocalDateTime.now();
        return message;
    }

    /**
     * 追加消息（自动生成 ID）
     */
    public Message addMessage(MessageRole role, String content) {
        Long messageId = SnowflakeIdGenerator.generate();
        return addMessage(messageId, role, content);
    }

    /**
     * 更新标题
     */
    public void updateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new DomainException("标题不能为空", "INVALID_TITLE");
        }
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 结束对话
     */
    public void end() {
        if (status == ConversationStatus.ENDED) {
            throw new InvalidStateException("对话已经结束", "ALREADY_ENDED");
        }
        this.status = ConversationStatus.ENDED;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isOwnedBy(String requestingUserId) {
        return this.userId.equals(requestingUserId);
    }

    public boolean isActive() {
        return status == ConversationStatus.ACTIVE;
    }

    public boolean isEnded() {
        return status == ConversationStatus.ENDED;
    }

    // ==================== 查询方法 ====================

    public Optional<Message> getLastMessage() {
        if (messages.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(messages.get(messages.size() - 1));
    }

    public List<Message> getUserMessages() {
        return messages.stream()
            .filter(Message::isUserMessage)
            .toList();
    }

    public List<Message> getAssistantMessages() {
        return messages.stream()
            .filter(Message::isAssistantMessage)
            .toList();
    }

    public int messageCount() {
        return messages.size();
    }

    // ==================== Getter 方法 ====================

    public Long getConversationId() {
        return conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public String getTitle() {
        return title;
    }

    public ConversationStatus getStatus() {
        return status;
    }

    public List<Message> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // ==================== 构造函数 ====================

    private Conversation(Long conversationId, String userId, String title,
                         ConversationStatus status, List<Message> messages,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.title = title != null && !title.isBlank() ? title : "新对话";
        this.status = status;
        this.messages = messages;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== 校验方法 ====================

    private static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId 不能为空", "INVALID_USER_ID");
        }
    }

    private static void validateConversationId(Long conversationId) {
        if (conversationId == null) {
            throw new DomainException("conversationId 不能为空", "INVALID_CONVERSATION_ID");
        }
    }
}
