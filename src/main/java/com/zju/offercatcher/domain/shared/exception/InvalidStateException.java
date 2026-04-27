package com.zju.offercatcher.domain.shared.exception;

/**
 * 无效状态转换异常
 *
 * 当执行不允许的状态转换时抛出。
 */
public class InvalidStateException extends DomainException {

    public InvalidStateException(String message, String errorCode) {
        super(message, errorCode);
    }

    public InvalidStateException(String message) {
        super(message, "INVALID_STATE_TRANSITION");
    }
}