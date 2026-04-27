package com.zju.offercatcher.domain.shared.enums;

/**
 * 面试题目状态枚举
 *
 * 用于管理面试过程中单道题目的状态流转。
 */
public enum QuestionStatus {
    /**
     * 待回答：题目已出，等待用户回答
     */
    PENDING("pending", "待回答"),

    /**
     * 正在回答：用户正在作答
     */
    ANSWERING("answering", "正在回答"),

    /**
     * 已评分：回答完成并已评分
     */
    SCORED("scored", "已评分"),

    /**
     * 已跳过：用户选择跳过该题
     */
    SKIPPED("skipped", "已跳过");

    private final String value;
    private final String description;

    QuestionStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static QuestionStatus fromValue(String value) {
        for (QuestionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown question status: " + value);
    }

    /**
     * 判断是否已回答（评分或跳过）
     */
    public boolean isAnswered() {
        return this == SCORED || this == SKIPPED;
    }
}