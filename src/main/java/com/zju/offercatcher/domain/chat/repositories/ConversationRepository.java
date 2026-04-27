package com.zju.offercatcher.domain.chat.repositories;

import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.shared.enums.ConversationStatus;

import java.util.List;
import java.util.Optional;

/**
 * 对话仓储接口
 */
public interface ConversationRepository {

    List<Conversation> findByUserId(String userId, int page, int size);

    Optional<Conversation> findById(Long conversationId);

    List<Conversation> findByUserIdAndStatus(String userId, ConversationStatus status, int page, int size);

    void save(Conversation conversation);

    /**
     * @throws com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException 如果用户不是所有者
     */
    void deleteById(Long conversationId, String userId);

    long countByUserId(String userId);

    long countByUserIdAndStatus(String userId, ConversationStatus status);
}
