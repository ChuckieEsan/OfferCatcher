package com.zju.offercatcher.domain.memory.repositories;

import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.shared.enums.MemoryLayer;

import java.util.List;
import java.util.Optional;

/**
 * 会话摘要仓储接口
 */
public interface SessionSummaryRepository {

    List<SessionSummary> findByUserId(String userId, int page, int size);

    List<SessionSummary> findByUserIdAndMemoryLayer(String userId, MemoryLayer memoryLayer, int page, int size);

    List<SessionSummary> findByConversationId(Long conversationId);

    Optional<SessionSummary> findById(Long id);

    void save(SessionSummary summary);

    void saveAll(List<SessionSummary> summaries);

    void deleteById(Long id, String userId);

    List<SessionSummary> findTopKByImportance(String userId, int k);

    List<SessionSummary> findMarkedForDeletion(String userId);

    List<SessionSummary> searchByVector(String userId, float[] queryVector, int limit);

    long countByUserId(String userId);

    long countByUserIdAndMemoryLayer(String userId, MemoryLayer memoryLayer);
}
