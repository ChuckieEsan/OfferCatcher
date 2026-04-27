package com.zju.offercatcher.domain.shared.enums;

/**
 * 记忆状态枚举
 *
 * 用于标识记忆文档的状态。
 */
public enum MemoryStatus {
    /**
     * 活跃状态：记忆正常使用
     */
    ACTIVE("active", "活跃"),

    /**
     * 已归档：记忆已归档，不参与活跃检索
     */
    ARCHIVED("archived", "已归档");

    private final String value;
    private final String description;

    MemoryStatus(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static MemoryStatus fromValue(String value) {
        for (MemoryStatus status : values()) {
            if (status.value.equals(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown memory status: " + value);
    }
}