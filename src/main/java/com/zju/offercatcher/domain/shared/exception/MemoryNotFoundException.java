package com.zju.offercatcher.domain.shared.exception;

/**
 * 记忆未找到异常
 *
 * 当尝试访问不存在的记忆时抛出。
 */
public class MemoryNotFoundException extends DomainException {

    private final String userId;

    public MemoryNotFoundException(String userId) {
        super("用户记忆不存在: " + userId, "MEMORY_NOT_FOUND");
        this.userId = userId;
    }

    public String getUserId() {
        return userId;
    }
}