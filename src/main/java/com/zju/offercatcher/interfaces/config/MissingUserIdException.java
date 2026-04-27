package com.zju.offercatcher.interfaces.config;

/**
 * 缺少 X-User-Id 请求头时抛出。
 */
public class MissingUserIdException extends RuntimeException {

    private final String headerName;

    public MissingUserIdException(String headerName) {
        super("Missing required header: " + headerName);
        this.headerName = headerName;
    }

    public String getHeaderName() {
        return headerName;
    }
}
