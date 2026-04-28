package com.zju.offercatcher.domain.question.valueobjects;

import com.zju.offercatcher.domain.question.aggregates.Question;

/**
 * 检索结果值对象
 * 包含题目和相似度分数
 */
public record QuestionWithScore(Question question, float score) {

    /**
     * 创建检索结果
     * @param question 题目实体
     * @param score 相似度分数（0-1之间）
     */
    public QuestionWithScore {
        if (question == null) {
            throw new IllegalArgumentException("Question cannot be null");
        }
        if (score < 0 || score > 1) {
            throw new IllegalArgumentException("Score must be between 0 and 1");
        }
    }

    /**
     * 获取题目 ID
     */
    public String getQuestionHash() {
        return question.getQuestionHash();
    }

    /**
     * 获取题目文本
     */
    public String getQuestionText() {
        return question.getQuestionText();
    }
}