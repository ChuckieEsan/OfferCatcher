package com.zju.offercatcher.infrastructure.persistence.qdrant;

import com.zju.offercatcher.domain.shared.enums.Visibility;

/**
 * Qdrant Filter 构建器
 *
 * 用于构建用户隔离预过滤条件，确保向量搜索时相似度计算在可见范围内进行。
 *
 * 注意：这是简化版本，使用 JSON 格式的 Filter。
 * 后续集成时使用 Qdrant Java Client 的正式 API。
 */
public final class QdrantFilterBuilder {

    /**
     * 构建用户可见性过滤条件的 JSON 字符串
     *
     * 逻辑：visibility = "public" OR (visibility = "private" AND user_id = userId)
     *
     * @param userId 用户 ID
     * @return Filter JSON 字符串
     */
    public static String buildUserVisibleFilterJson(String userId) {
        return String.format(
            "{\"should\": [" +
            "  {\"must\": [{\"key\": \"visibility\", \"match\": {\"value\": \"%s\"}}]}, " +
            "  {\"must\": [" +
            "    {\"key\": \"visibility\", \"match\": {\"value\": \"%s\"}}, " +
            "    {\"key\": \"user_id\", \"match\": {\"value\": \"%s\"}}" +
            "  ]}" +
            "]}",
            Visibility.PUBLIC.getValue(),
            Visibility.PRIVATE.getValue(),
            userId
        );
    }

    /**
     * 构建仅公共题目过滤条件的 JSON 字符串
     *
     * @return Filter JSON 字符串
     */
    public static String buildPublicOnlyFilterJson() {
        return String.format(
            "{\"must\": [{\"key\": \"visibility\", \"match\": {\"value\": \"%s\"}}]}",
            Visibility.PUBLIC.getValue()
        );
    }

    /**
     * 构建仅用户私有题目过滤条件的 JSON 字符串
     *
     * @param userId 用户 ID
     * @return Filter JSON 字符串
     */
    public static String buildPrivateOnlyFilterJson(String userId) {
        return String.format(
            "{\"must\": [" +
            "  {\"key\": \"visibility\", \"match\": {\"value\": \"%s\"}}, " +
            "  {\"key\": \"user_id\", \"match\": {\"value\": \"%s\"}}" +
            "]}",
            Visibility.PRIVATE.getValue(),
            userId
        );
    }

    private QdrantFilterBuilder() {
        // 工具类，禁止实例化
    }
}