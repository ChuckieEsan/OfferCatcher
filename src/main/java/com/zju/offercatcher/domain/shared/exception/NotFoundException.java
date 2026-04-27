package com.zju.offercatcher.domain.shared.exception;

/**
 * 通用资源未找到异常。
 */
public class NotFoundException extends DomainException {

    private final String resourceType;
    private final Object resourceId;

    public NotFoundException(String resourceType, Object resourceId) {
        super(String.format("%s 不存在: %s", resourceType, resourceId), "NOT_FOUND");
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    public String getResourceType() {
        return resourceType;
    }

    public Object getResourceId() {
        return resourceId;
    }
}
