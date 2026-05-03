package com.zju.offercatcher.infrastructure.persistence.qdrant;

import com.zju.offercatcher.domain.question.aggregates.Question;
import io.qdrant.client.grpc.JsonWithInt.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * Qdrant Payload 转换器
 * <p>
 * 极简设计：只转换 userId 和 visibility 用于向量搜索预过滤。
 * 元数据从 PostgreSQL 读取，不从 Qdrant Payload 读取。
 */
public final class QdrantPayloadMapper {

    /**
     * 将 Question 转换为 Qdrant Payload
     * 只存 userId 和 visibility
     *
     * @param question 题目实体
     * @return Qdrant Payload Map
     */
    public static Map<String, Value> toPayload(Question question) {
        Map<String, Value> payload = new HashMap<>();

        // 用户归属（用于预过滤私有题目）
        payload.put(QdrantPayloadFields.USER_ID, Value.newBuilder()
                .setStringValue(question.getUserId())
                .build());

        // 可见性（用于预过滤公共/私有题目）
        payload.put(QdrantPayloadFields.VISIBILITY, Value.newBuilder()
                .setStringValue(question.getVisibility().getValue())
                .build());

        return payload;
    }

    /**
     * 不提供 fromPayload 方法
     * 元数据从 PostgreSQL 读取，不从 Qdrant Payload 读取
     */

    private QdrantPayloadMapper() {
        // 工具类，禁止实例化
    }
}