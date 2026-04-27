package com.zju.offercatcher.domain.shared.enums;

/**
 * 面试会话状态枚举
 *
 * 用于管理面试会话的生命周期状态。
 */
public enum SessionStatus {
    /**
     * 进行中：会话活跃
     */
    ACTIVE("active", "进行中"),

    /**
     * 已暂停：会话暂停
     */
    PAUSED("paused", "已暂停"),

    /**
     * 已完成：会话结束
     */
    COMPLETED("completed", "已完成");

    private final String value;
    private final String description;

    SessionStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static SessionStatus fromValue(String value) {
        for (SessionStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown session status: " + value);
    }
}