package com.zju.offercatcher.domain.shared.enums;

/**
 * 题目类型枚举
 */
public enum QuestionType {
    /**
     * 知识型：概念、原理、定义类问题
     */
    KNOWLEDGE("knowledge", false),

    /**
     * 项目型：项目经验、实际案例类问题，需要详细答案
     */
    PROJECT("project", true),

    /**
     * 行为型：行为面试、软技能类问题
     */
    BEHAVIORAL("behavioral", false),

    /**
     * 场景型：场景设计、系统设计类问题，需要详细答案
     */
    SCENARIO("scenario", true),

    /**
     * 算法型：代码实现、算法问题，需要详细答案
     */
    ALGORITHM("algorithm", true);

    private final String value;
    private final boolean requiresAsyncAnswer;

    QuestionType(String value, boolean requiresAsyncAnswer) {
        this.value = value;
        this.requiresAsyncAnswer = requiresAsyncAnswer;
    }

    public String getValue() {
        return value;
    }

    /**
     * 是否需要异步生成详细答案
     * 项目型、场景型、算法型题目通常需要联网搜索辅助生成
     */
    public boolean requiresAsyncAnswer() {
        return requiresAsyncAnswer;
    }

    public static QuestionType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("QuestionType value cannot be null");
        }
        for (QuestionType type : values()) {
            if (type.value.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown question type value: " + value);
    }
}