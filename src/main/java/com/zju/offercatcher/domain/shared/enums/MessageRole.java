package com.zju.offercatcher.domain.shared.enums;

/**
 * 消息角色枚举
 * <p>
 * 用于区分对话消息的角色类型。
 */
public enum MessageRole {
    /**
     * 用户消息：用户发送的内容
     */
    USER("user", "用户"),

    /**
     * AI 回复：助手返回的内容
     */
    ASSISTANT("assistant", "助手");

    private final String value;
    private final String description;

    MessageRole(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static MessageRole fromValue(String value) {
        for (MessageRole role : values()) {
            if (role.value.equals(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown message role: " + value);
    }
}