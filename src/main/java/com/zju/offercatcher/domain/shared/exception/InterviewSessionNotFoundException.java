package com.zju.offercatcher.domain.shared.exception;

/**
 * 面试会话未找到异常
 */
public class InterviewSessionNotFoundException extends DomainException {

    private final Long sessionId;

    public InterviewSessionNotFoundException(Long sessionId) {
        super("面试会话不存在: " + sessionId, "INTERVIEW_SESSION_NOT_FOUND");
        this.sessionId = sessionId;
    }

    public Long getSessionId() {
        return sessionId;
    }
}
