package com.zju.offercatcher.domain.shared.enums;

/**
 * 难度等级枚举
 * <p>
 * 用于面试题目难度设置和评分标准调整。
 */
public enum DifficultyLevel {
    /**
     * 简单：评分标准宽松
     */
    EASY("easy", "简单"),

    /**
     * 中等：标准评分
     */
    MEDIUM("medium", "中等"),

    /**
     * 困难：评分标准严格
     */
    HARD("hard", "困难");

    private final String value;
    private final String description;

    DifficultyLevel(String value, String description) {
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
    public static DifficultyLevel fromValue(String value) {
        for (DifficultyLevel level : values()) {
            if (level.value.equals(value)) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown difficulty level: " + value);
    }
}