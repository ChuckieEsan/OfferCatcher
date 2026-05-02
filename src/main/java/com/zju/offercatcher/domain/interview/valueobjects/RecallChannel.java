package com.zju.offercatcher.domain.interview.valueobjects;

/**
 * 召回通道枚举。
 *
 * 定义推荐系统中不同的候选题目召回来源，每个通道有独立的权重。
 * 权重影响排序层的 channelWeight 分量。
 */
public enum RecallChannel {
    /** pg_trgm 三元组相似度 — coreEntities 模糊匹配 */
    PG_TRGM("pg_trgm", 1.0),
    /** Qdrant 嵌入语义召回 */
    SEMANTIC("semantic", 0.7),
    /** 公司+岗位泛召回兜底 */
    GENERAL("general", 0.3);

    private final String label;
    private final double weight;

    RecallChannel(String label, double weight) {
        this.label = label;
        this.weight = weight;
    }

    public String getLabel() { return label; }
    public double getWeight() { return weight; }
}
