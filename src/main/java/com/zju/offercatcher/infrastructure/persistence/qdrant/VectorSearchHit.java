package com.zju.offercatcher.infrastructure.persistence.qdrant;

/**
 * 向量搜索结果值对象
 *
 * 表示从 Qdrant 返回的单个搜索结果。
 */
public record VectorSearchHit(String questionId, float score) {

    /**
     * 创建向量搜索结果
     *
     * @param questionId 题目 ID
     * @param score 相似度分数（0-1）
     */
    public VectorSearchHit {
        if (questionId == null || questionId.isBlank()) {
            throw new IllegalArgumentException("questionId cannot be null or blank");
        }
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be between 0 and 1, got: " + score);
        }
    }
}