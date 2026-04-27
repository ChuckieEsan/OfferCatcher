package com.zju.offercatcher.domain.shared.enums;

/**
 * 题目可见性枚举
 * 用于用户隔离：私有题目仅所有者可见，公共题目所有用户可见
 */
public enum Visibility {
    /**
     * 公共题目：所有用户可见
     */
    PUBLIC("public"),

    /**
     * 私有题目：仅所有者可见
     */
    PRIVATE("private");

    private final String value;

    Visibility(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static Visibility fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Visibility value cannot be null");
        }
        for (Visibility visibility : values()) {
            if (visibility.value.equalsIgnoreCase(value)) {
                return visibility;
            }
        }
        throw new IllegalArgumentException("Unknown visibility value: " + value);
    }
}