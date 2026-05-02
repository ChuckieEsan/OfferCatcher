package com.zju.offercatcher.domain.interview.valueobjects;

import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;

import java.util.List;

/**
 * 推荐候选题目值对象。
 *
 * 承载召回通道产出的候选题目及其多维得分，用于排序和重排。
 *
 * @param questionId      题目 ID
 * @param questionText    题目文本
 * @param questionType    题目类型
 * @param difficulty      难度
 * @param coreEntities    核心考点
 * @param cosineScore     Qdrant 余弦相似度（0-1）
 * @param jaccardScore    JD skill 与 coreEntities 的 Jaccard 相似度（0-1）
 * @param channelWeight   召回通道权重（1.0/0.7/0.3）
 * @param recallChannel   来源通道标签
 * @param matchedSkillName  匹配到的 JD 技能名称
 * @param masteryLevel    用户当前掌握度（0-4），用于微调排序
 */
public record CandidateQuestion(
    Long questionId,
    String questionText,
    String questionType,
    DifficultyLevel difficulty,
    List<String> coreEntities,
    double cosineScore,
    double jaccardScore,
    double channelWeight,
    RecallChannel recallChannel,
    String matchedSkillName,
    int masteryLevel
) {
    /** 紧凑构造，默认 masteryLevel=0 */
    public CandidateQuestion(
        Long questionId, String questionText, String questionType,
        DifficultyLevel difficulty, List<String> coreEntities,
        double cosineScore, double jaccardScore, double channelWeight,
        RecallChannel recallChannel, String matchedSkillName
    ) {
        this(questionId, questionText, questionType, difficulty, coreEntities,
            cosineScore, jaccardScore, channelWeight, recallChannel, matchedSkillName, 0);
    }

    /**
     * 综合得分 = 语义 + 关键词 + 通道 + 掌握度加权。
     * 低掌握度题目获得轻微加分（LEVEL_0 → +0.10, LEVEL_4 → +0.00），
     * 保证多次面试不会反复出同一批高掌握度题目。
     */
    public double combinedScore() {
        double masteryBoost = (4.0 - masteryLevel) / 4.0 * 0.10;
        return 0.4 * cosineScore + 0.35 * jaccardScore + 0.25 * channelWeight + masteryBoost;
    }
}
