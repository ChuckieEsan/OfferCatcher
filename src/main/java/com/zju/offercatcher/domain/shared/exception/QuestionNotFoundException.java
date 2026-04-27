package com.zju.offercatcher.domain.shared.exception;

/**
 * 题目未找到异常
 */
public class QuestionNotFoundException extends DomainException {

    private final String questionId;

    public QuestionNotFoundException(String questionId) {
        super(String.format("题目不存在: %s", questionId), "QUESTION_NOT_FOUND");
        this.questionId = questionId;
    }

    public String getQuestionId() {
        return questionId;
    }
}