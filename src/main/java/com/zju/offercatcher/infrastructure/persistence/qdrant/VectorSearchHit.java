package com.zju.offercatcher.infrastructure.persistence.qdrant;

/**
 * 向量搜索结果值对象
 *
 * 表示从 Qdrant 返回的单个搜索结果，可用于题目和会话摘要。
 */
public record VectorSearchHit(String id, float score) {

    public VectorSearchHit {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id cannot be null or blank");
        }
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("score must be between 0 and 1, got: " + score);
        }
    }
}
