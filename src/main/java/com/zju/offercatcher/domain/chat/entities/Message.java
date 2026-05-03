package com.zju.offercatcher.domain.chat.entities;

import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.domain.shared.exception.DomainException;

import java.time.LocalDateTime;

/**
 * 消息实体
 * <p>
 * Message 是 Conversation 聚合内的实体，不可独立存在。
 * 消息创建后不可修改，对话是历史记录。
 */
public class Message {

    private final Long messageId;
    private final MessageRole role;
    private final String content;
    private final String reasoning;
    private final String toolCalls;
    private final LocalDateTime createdAt;

    /**
     * 创建消息（工厂方法）
     */
    public static Message create(Long messageId, MessageRole role, String content) {
        return create(messageId, role, content, null, null);
    }

    /**
     * 创建消息，带 reasoning（AI 思考过程）。reasoning 仅对 ASSISTANT 消息有意义。
     */
    public static Message create(Long messageId, MessageRole role, String content, String reasoning) {
        return create(messageId, role, content, reasoning, null);
    }

    /**
     * 创建消息（完整参数）。
     *
     * @param toolCalls JSON 数组，记录工具调用信息，仅对 ASSISTANT 消息有意义，可为 null
     */
    public static Message create(Long messageId, MessageRole role, String content, String reasoning, String toolCalls) {
        validateMessageId(messageId);
        validateRole(role);
        validateContent(content);
        return new Message(messageId, role, content, reasoning, toolCalls, LocalDateTime.now());
    }

    /**
     * 从持久化存储重建消息（用于 Repository 实现）
     */
    public static Message rebuild(Long messageId, MessageRole role, String content, String reasoning, String toolCalls, LocalDateTime createdAt) {
        return new Message(messageId, role, content, reasoning, toolCalls, createdAt);
    }

    public boolean isUserMessage() {
        return role == MessageRole.USER;
    }

    public boolean isAssistantMessage() {
        return role == MessageRole.ASSISTANT;
    }

    // ==================== Getter 方法 ====================

    public Long getMessageId() {
        return messageId;
    }

    public MessageRole getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getReasoning() {
        return reasoning;
    }

    public String getToolCalls() {
        return toolCalls;
    }

    public boolean hasReasoning() {
        return reasoning != null && !reasoning.isBlank();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ==================== 构造函数 ====================

    private Message(Long messageId, MessageRole role, String content, String reasoning, String toolCalls, LocalDateTime createdAt) {
        this.messageId = messageId;
        this.role = role;
        this.content = content;
        this.reasoning = reasoning;
        this.toolCalls = toolCalls;
        this.createdAt = createdAt;
    }

    // ==================== 校验方法 ====================

    private static void validateMessageId(Long messageId) {
        if (messageId == null) {
            throw new DomainException("messageId 不能为空", "INVALID_MESSAGE_ID");
        }
    }

    private static void validateRole(MessageRole role) {
        if (role == null) {
            throw new DomainException("role 不能为空", "INVALID_MESSAGE_ROLE");
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new DomainException("content 不能为空", "INVALID_MESSAGE_CONTENT");
        }
    }
}
