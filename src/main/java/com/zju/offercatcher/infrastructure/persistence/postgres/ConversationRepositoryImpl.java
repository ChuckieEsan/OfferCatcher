package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.chat.repositories.ConversationRepository;
import com.zju.offercatcher.domain.shared.enums.ConversationStatus;
import com.zju.offercatcher.domain.shared.exception.ConversationNotFoundException;
import com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Conversation Repository 实现
 */
@Repository
public class ConversationRepositoryImpl implements ConversationRepository {

    private final ConversationJpaRepository conversationJpaRepository;
    private final MessageJpaRepository messageJpaRepository;

    public ConversationRepositoryImpl(ConversationJpaRepository conversationJpaRepository,
                                       MessageJpaRepository messageJpaRepository) {
        this.conversationJpaRepository = conversationJpaRepository;
        this.messageJpaRepository = messageJpaRepository;
    }

    @Override
    public List<Conversation> findByUserId(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<ConversationJpaEntity> entities = conversationJpaRepository.findByUserIdPaginated(userId, size, offset);
        return entities.stream()
            .map(this::loadConversationWithMessages)
            .toList();
    }

    @Override
    public Optional<Conversation> findById(Long conversationId) {
        return conversationJpaRepository.findByConversationId(conversationId)
            .map(this::loadConversationWithMessages);
    }

    @Override
    public List<Conversation> findByUserIdAndStatus(String userId, ConversationStatus status, int page, int size) {
        int offset = (page - 1) * size;
        List<ConversationJpaEntity> entities = conversationJpaRepository.findByUserIdAndStatusPaginated(userId, status, size, offset);
        return entities.stream()
            .map(this::loadConversationWithMessages)
            .toList();
    }

    @Override
    @Transactional
    public void save(Conversation conversation) {
        ConversationJpaEntity conversationEntity = ConversationJpaEntity.fromDomain(conversation);
        conversationJpaRepository.save(conversationEntity);

        Long conversationId = conversation.getConversationId();
        messageJpaRepository.deleteByConversationId(conversationId);

        List<MessageJpaEntity> messageEntities = conversation.getMessages()
            .stream()
            .map(m -> MessageJpaEntity.fromDomain(m, conversationId))
            .toList();
        messageJpaRepository.saveAll(messageEntities);
    }

    @Override
    @Transactional
    public void deleteById(Long conversationId, String userId) {
        Optional<ConversationJpaEntity> entity = conversationJpaRepository.findByConversationId(conversationId);
        if (entity.isEmpty()) {
            throw new ConversationNotFoundException(conversationId);
        }
        if (!entity.get().getUserId().equals(userId)) {
            throw new UnauthorizedOperationException(userId, conversationId.toString(), "delete conversation");
        }

        messageJpaRepository.deleteByConversationId(conversationId);
        conversationJpaRepository.delete(entity.get());
    }

    @Override
    public long countByUserId(String userId) {
        return conversationJpaRepository.countByUserId(userId);
    }

    @Override
    public long countByUserIdAndStatus(String userId, ConversationStatus status) {
        return conversationJpaRepository.countByUserIdAndStatus(userId, status);
    }

    private Conversation loadConversationWithMessages(ConversationJpaEntity entity) {
        List<MessageJpaEntity> messageEntities = messageJpaRepository.findByConversationId(entity.getConversationId());
        List<Message> messages = messageEntities.stream()
            .map(MessageJpaEntity::toDomain)
            .toList();
        return Conversation.rebuild(
            entity.getConversationId(),
            entity.getUserId(),
            entity.getTitle(),
            entity.getStatus(),
            messages,
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
