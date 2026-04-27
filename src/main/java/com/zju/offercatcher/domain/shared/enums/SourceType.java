package com.zju.offercatcher.domain.shared.enums;

/**
 * 题目来源类型枚举
 * 区分用户上传和系统导入的题目
 */
public enum SourceType {
    /**
     * 用户上传：用户通过面经导入创建的题目
     */
    USER_UPLOAD("user_upload"),

    /**
     * 系统导入：初始题库或管理员导入的题目
     */
    SYSTEM_IMPORT("system_import");

    private final String value;

    SourceType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SourceType fromValue(String value) {
        if (value == null) {
            throw new IllegalArgumentException("SourceType value cannot be null");
        }
        for (SourceType sourceType : values()) {
            if (sourceType.value.equalsIgnoreCase(value)) {
                return sourceType;
            }
        }
        throw new IllegalArgumentException("Unknown source type value: " + value);
    }
}