package com.zju.offercatcher.infrastructure.persistence.qdrant;

/**
 * Qdrant Payload 字段定义
 * <p>
 * 设计原则：Payload 只存 userId 和 visibility，用于向量搜索时的预过滤。
 * 其他元数据存储在 PostgreSQL，更新时不需同步 Qdrant。
 */
public final class QdrantPayloadFields {

    /**
     * 用户归属字段（用于用户隔离过滤）
     */
    public static final String USER_ID = "user_id";

    /**
     * 可见性字段（用于公共/私有题目过滤）
     */
    public static final String VISIBILITY = "visibility";

    // 不存其他元数据（这些在 PostgreSQL）：
    // - questionText, company, position, answer, coreEntities, questionType
    // 更新这些字段时无需同步 Qdrant

    private QdrantPayloadFields() {
        // 常量类，禁止实例化
    }
}