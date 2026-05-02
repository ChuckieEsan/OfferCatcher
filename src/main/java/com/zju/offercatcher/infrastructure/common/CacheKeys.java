package com.zju.offercatcher.infrastructure.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Redis Key 命名规则
 *
 * 对应 Python: app/infrastructure/common/cache_keys.py
 */
public final class CacheKeys {

    private static final String PREFIX = "oc";

    private CacheKeys() {}

    // ==================== Stats Keys ====================

    public static String statsOverview() { return PREFIX + ":stats:overview"; }
    public static String statsClusters() { return PREFIX + ":stats:clusters"; }
    public static String statsCompanies() { return PREFIX + ":stats:companies"; }
    public static String statsEntities(String company, int limit) {
        return PREFIX + ":stats:entities:" + (company != null ? company : "all") + ":" + limit;
    }
    public static String statsPositions() { return PREFIX + ":stats:positions"; }
    public static String statsEntitiesPattern() { return PREFIX + ":stats:entities:*"; }
    public static String statsPattern() { return PREFIX + ":stats:*"; }

    // ==================== Questions Keys ====================

    public static String questionsList(String filterHash) {
        return PREFIX + ":questions:list:" + filterHash;
    }
    public static String questionsCount(String filterHash) {
        return PREFIX + ":questions:count:" + filterHash;
    }
    public static String questionsItem(Long id) {
        return PREFIX + ":questions:item:" + id;
    }
    public static String questionsListPattern() { return PREFIX + ":questions:list:*"; }
    public static String questionsCountPattern() { return PREFIX + ":questions:count:*"; }

    // ==================== Tool Cache Keys ====================

    public static String toolSearchQuestions(String queryHash) {
        return PREFIX + ":tool:search:" + queryHash;
    }
    public static String toolQueryGraph(String queryHash) {
        return PREFIX + ":tool:graph:" + queryHash;
    }
    public static String toolWebSearch(String queryHash) {
        return PREFIX + ":tool:web:" + queryHash;
    }
    public static String toolCompanyTopics(String company) {
        return PREFIX + ":tool:company_topics:" + company;
    }
    public static String toolKnowledgeRelations(String entity) {
        return PREFIX + ":tool:knowledge_relations:" + entity;
    }
    public static String toolCrossCompanyTrends(int minCompanies) {
        return PREFIX + ":tool:cross_company_trends:" + minCompanies;
    }
    public static String toolSearchPattern() { return PREFIX + ":tool:search:*"; }
    public static String toolGraphPattern() { return PREFIX + ":tool:graph:*"; }
    public static String toolWebPattern() { return PREFIX + ":tool:web:*"; }

    // ==================== Memory Retrieval Keys ====================

    /** 异步检索后存储的记忆上下文，Key: oc:memory:context:{userId}:{conversationId} */
    public static String memoryContext(String userId, Long conversationId) {
        return PREFIX + ":memory:context:" + userId + ":" + conversationId;
    }

    /** 检索锁，防止同一对话并发检索，Key: oc:memory:retrieval-lock:{userId}:{conversationId} */
    public static String memoryRetrievalLock(String userId, Long conversationId) {
        return PREFIX + ":memory:retrieval-lock:" + userId + ":" + conversationId;
    }

    /** 记忆提取游标，记录上次处理到的消息 ID，Key: oc:memory:cursor:{userId}:{conversationId} */
    public static String memoryCursor(String userId, Long conversationId) {
        return PREFIX + ":memory:cursor:" + userId + ":" + conversationId;
    }

    // ==================== Utility ====================

    /**
     * 生成参数哈希值（MD5 前 8 位）
     */
    public static String hashParams(Object... args) {
        StringBuilder sb = new StringBuilder();
        for (Object arg : args) {
            if (arg != null) {
                if (!sb.isEmpty()) sb.append(":");
                sb.append(arg);
            }
        }
        if (sb.isEmpty()) return "empty";

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
}
