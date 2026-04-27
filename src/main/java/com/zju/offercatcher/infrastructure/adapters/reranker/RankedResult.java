package com.zju.offercatcher.infrastructure.adapters.reranker;

/**
 * Reranker 排序结果值对象
 */
public record RankedResult(int originalIndex, float score) {
}
