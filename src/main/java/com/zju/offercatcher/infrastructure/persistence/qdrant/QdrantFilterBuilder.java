package com.zju.offercatcher.infrastructure.persistence.qdrant;

import com.zju.offercatcher.domain.shared.enums.Visibility;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.grpc.Common;

/**
 * Qdrant Filter 构建器
 *
 * 使用 Qdrant gRPC Java Client API 构建用户隔离预过滤条件。
 * Qdrant 中 should=OR, must=AND。
 */
public final class QdrantFilterBuilder {

    /**
     * 构建用户可见性过滤条件
     * 逻辑：visibility = "public" OR (visibility = "private" AND user_id = userId)
     */
    public static Common.Filter buildUserVisibleFilter(String userId) {
        // Condition 1: visibility = "public"
        Common.Condition publicCond = ConditionFactory.matchKeyword(
            QdrantPayloadFields.VISIBILITY, Visibility.PUBLIC.getValue());

        // Condition 2: visibility = "private" AND user_id = userId (nested filter)
        Common.Filter privateFilter = Common.Filter.newBuilder()
            .addMust(ConditionFactory.matchKeyword(
                QdrantPayloadFields.VISIBILITY, Visibility.PRIVATE.getValue()))
            .addMust(ConditionFactory.matchKeyword(
                QdrantPayloadFields.USER_ID, userId))
            .build();
        Common.Condition privateCond = ConditionFactory.filter(privateFilter);

        return Common.Filter.newBuilder()
            .addShould(publicCond)
            .addShould(privateCond)
            .build();
    }

    /**
     * 构建仅用户私有题目过滤条件
     */
    public static Common.Filter buildPrivateOnlyFilter(String userId) {
        return Common.Filter.newBuilder()
            .addMust(ConditionFactory.matchKeyword(
                QdrantPayloadFields.VISIBILITY, Visibility.PRIVATE.getValue()))
            .addMust(ConditionFactory.matchKeyword(
                QdrantPayloadFields.USER_ID, userId))
            .build();
    }

    /**
     * 构建 userId 过滤条件（用于 SessionSummary）
     */
    public static Common.Filter buildUserIdFilter(String userId) {
        return Common.Filter.newBuilder()
            .addMust(ConditionFactory.matchKeyword(QdrantPayloadFields.USER_ID, userId))
            .build();
    }

    private QdrantFilterBuilder() {
    }
}
