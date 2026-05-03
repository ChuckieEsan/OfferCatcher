package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 对话相关 DTO。
 */
public interface ChatDto {

    record ChatRequest(
            @NotBlank String message,
            Long conversationId
    ) {
    }

    record ConversationCreateRequest(
            String title
    ) {
    }

    record ConversationResponse(
            Long conversationId,
            String title,
            String status,
            int messageCount,
            String createdAt,
            String updatedAt,
            List<MessageResponse> messages
    ) {
    }

    record MessageResponse(
            Long messageId,
            String role,
            String content,
            String reasoning,
            String toolCalls,
            String createdAt
    ) {
    }

    record ConversationListResponse(
            List<ConversationResponse> conversations,
            int total,
            int page,
            int pageSize
    ) {
    }
}
