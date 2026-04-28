package com.zju.offercatcher.domain.shared.exception;

/**
 * 题目未找到异常
 */
public class QuestionNotFoundException extends DomainException {

    private final Long questionId;

    public QuestionNotFoundException(Long questionId) {
        super(String.format("题目不存在: %d", questionId), "QUESTION_NOT_FOUND");
        this.questionId = questionId;
    }

    public Long getQuestionId() {
        return questionId;
    }
}
