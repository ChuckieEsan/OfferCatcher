package com.zju.offercatcher.domain.question.aggregates;

/**
 * 提取任务状态值对象。
 * <p>
 * 状态流转：PENDING -> PROCESSING -> COMPLETED -> CONFIRMED
 * 异常路径：PENDING/PROCESSING -> CANCELLED
 */
public enum ExtractTaskStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    CONFIRMED,
    CANCELLED
}
