package com.zju.offercatcher.application.agent.dto;

import java.util.List;

/**
 * 评分结果 DTO
 *
 * 对应 Python: app/domain/interview/aggregates.py ScoreResult
 */
public record ScoreResult(
    String questionId,
    String questionText,
    String standardAnswer,
    String userAnswer,
    int score,
    String masteryLevel,
    List<String> strengths,
    List<String> improvements,
    String feedback
) {}
