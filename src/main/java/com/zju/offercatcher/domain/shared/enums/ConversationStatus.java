package com.zju.offercatcher.domain.shared.enums;

/**
 * 对话会话状态枚举
 *
 * 用于管理对话会话的生命周期状态。
 */
public enum ConversationStatus {
    /**
     * 进行中：对话活跃
     */
    ACTIVE("active", "进行中"),

    /**
     * 已结束：对话已终止
     */
    ENDED("ended", "已结束");

    private final String value;
    private final String description;

    ConversationStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static ConversationStatus fromValue(String value) {
        for (ConversationStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown conversation status: " + value);
    }
}