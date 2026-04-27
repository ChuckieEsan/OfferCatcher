package com.zju.offercatcher.domain.shared.enums;

/**
 * 熟练度等级枚举
 * 表示用户对题目的掌握程度
 */
public enum MasteryLevel {
    /**
     * 未学习：从未接触该题目
     */
    LEVEL_0(0, "未学习"),

    /**
     * 初步了解：看过答案但未深入理解
     */
    LEVEL_1(1, "初步了解"),

    /**
     * 基本掌握：能回答基本要点
     */
    LEVEL_2(2, "基本掌握"),

    /**
     * 熟练掌握：能完整回答并举例
     */
    LEVEL_3(3, "熟练掌握"),

    /**
     * 精通：能深入扩展、举一反三
     */
    LEVEL_4(4, "精通");

    private final int level;
    private final String description;

    MasteryLevel(int level, String description) {
        this.level = level;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public String getDescription() {
        return description;
    }

    public static MasteryLevel fromLevel(int level) {
        for (MasteryLevel mastery : values()) {
            if (mastery.level == level) {
                return mastery;
            }
        }
        throw new IllegalArgumentException("Unknown mastery level: " + level);
    }
}