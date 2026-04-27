package com.zju.offercatcher.infrastructure.persistence.qdrant;

import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.shared.enums.Visibility;
import com.zju.offercatcher.infrastructure.config.QdrantConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * Qdrant 向量存储服务
 *
 * 负责与 Qdrant 的交互：向量上传、搜索、删除、payload 更新。
 *
 * 当前版本：接口定义，后续集成时实现真正的 Qdrant API 调用。
 */
@Service
public class QdrantVectorStore {

    private static final Logger log = LoggerFactory.getLogger(QdrantVectorStore.class);

    private final QdrantConfig.QdrantProperties properties;

    public QdrantVectorStore(QdrantConfig.QdrantProperties properties) {
        this.properties = properties;
        log.info("QdrantVectorStore initialized for collection: {}", properties.getCollection());
    }

    /**
     * 搜索用户可见的题目（公共 + 用户私有）
     *
     * @param queryVector 查询向量
     * @param userId 用户 ID
     * @param limit 返回数量
     * @return 搜索结果列表
     */
    public List<VectorSearchHit> search(float[] queryVector, String userId, int limit) {
        // TODO: 实现真正的 Qdrant API 调用
        // 当前返回空列表，后续集成时实现
        log.debug("Searching vectors for user: {}, limit: {}", userId, limit);
        return Collections.emptyList();
    }

    /**
     * 搜索公共题目
     *
     * @param queryVector 查询向量
     * @param limit 返回数量
     * @return 搜索结果列表
     */
    public List<VectorSearchHit> searchPublic(float[] queryVector, int limit) {
        log.debug("Searching public vectors, limit: {}", limit);
        return Collections.emptyList();
    }

    /**
     * 搜索用户私有题目
     *
     * @param userId 用户 ID
     * @param queryVector 查询向量
     * @param limit 返回数量
     * @return 搜索结果列表
     */
    public List<VectorSearchHit> searchPrivate(String userId, float[] queryVector, int limit) {
        log.debug("Searching private vectors for user: {}, limit: {}", userId, limit);
        return Collections.emptyList();
    }

    /**
     * 上传向量（带 payload）
     *
     * @param question 题目实体（用于生成 payload）
     * @param embedding 向量
     */
    public void upsert(Question question, float[] embedding) {
        // TODO: 实现真正的 Qdrant API 调用
        log.debug("Upserting vector for question: {}", question.getQuestionId());
    }

    /**
     * 删除向量
     *
     * @param questionId 题目 ID
     */
    public void delete(String questionId) {
        log.debug("Deleting vector for question: {}", questionId);
    }

    /**
     * 更新 payload 中的 visibility
     *
     * @param questionId 题目 ID
     * @param visibility 新可见性
     */
    public void updateVisibility(String questionId, Visibility visibility) {
        log.debug("Updating visibility for question: {} to {}", questionId, visibility);
    }
}