package com.zju.offercatcher.domain.shared.exception;

/**
 * 无权限操作异常
 * 当用户尝试操作不属于自己的资源时抛出
 */
public class UnauthorizedOperationException extends DomainException {

    private final String userId;
    private final String resourceId;
    private final String operation;

    public UnauthorizedOperationException(String userId, String resourceId, String operation) {
        super(String.format("用户 %s 无权限对资源 %s 执行操作: %s", userId, resourceId, operation),
              "UNAUTHORIZED_OPERATION");
        this.userId = userId;
        this.resourceId = resourceId;
        this.operation = operation;
    }

    public String getUserId() {
        return userId;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getOperation() {
        return operation;
    }
}