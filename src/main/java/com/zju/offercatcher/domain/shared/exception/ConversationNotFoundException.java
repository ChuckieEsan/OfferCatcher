package com.zju.offercatcher.domain.shared.exception;

/**
 * 对话未找到异常
 */
public class ConversationNotFoundException extends DomainException {

    private final Long conversationId;

    public ConversationNotFoundException(Long conversationId) {
        super("对话不存在: " + conversationId, "CONVERSATION_NOT_FOUND");
        this.conversationId = conversationId;
    }

    public Long getConversationId() {
        return conversationId;
    }
}
