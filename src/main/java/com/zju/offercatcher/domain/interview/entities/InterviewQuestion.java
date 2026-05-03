package com.zju.offercatcher.domain.interview.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.InterviewPhase;
import com.zju.offercatcher.domain.shared.enums.QuestionStatus;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.domain.shared.exception.InvalidStateException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 面试题目实体
 * <p>
 * InterviewQuestion 是 InterviewSession 聚合内的实体，表示面试中的单道题目。
 * <p>
 * 设计原则：
 * - 通过 InterviewSession 创建
 * - 包含答题状态、评分、反馈
 * - 支持追问和提示机制
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class InterviewQuestion {

    private final Long questionId;        // Snowflake PK，引用 questions.id
    private final String questionHash;    // MD5 业务去重键
    private String questionText;
    private final String questionType;
    private final DifficultyLevel difficulty;
    private final List<String> knowledgePoints;

    private InterviewPhase phase;

    private String userAnswer;
    private Integer score;
    private String feedback;
    private Integer masteryBefore;
    private Integer masteryAfter;

    private final List<String> followUps;
    private int currentFollowUpIdx;
    private final List<String> hintsGiven;
    private QuestionStatus status;
    private LocalDateTime answeredAt;

    /**
     * 创建面试题目（工厂方法）
     */
    public static InterviewQuestion create(Long questionId, String questionHash, String questionText,
                                           String questionType, DifficultyLevel difficulty,
                                           List<String> knowledgePoints) {
        validateQuestionId(questionId);
        validateQuestionHash(questionHash);
        validateQuestionText(questionText);
        return new InterviewQuestion(questionId, questionHash, questionText, questionType, difficulty,
                knowledgePoints, null, null, null, null, null,
                new ArrayList<>(), 0, new ArrayList<>(), QuestionStatus.PENDING, null, null);
    }

    /**
     * 从持久化存储重建（用于 Repository 实现）
     */
    public static InterviewQuestion rebuild(Long questionId, String questionHash, String questionText,
                                            String questionType, DifficultyLevel difficulty,
                                            List<String> knowledgePoints,
                                            String userAnswer, Integer score, String feedback,
                                            Integer masteryBefore, Integer masteryAfter,
                                            List<String> followUps, int currentFollowUpIdx,
                                            List<String> hintsGiven, QuestionStatus status,
                                            LocalDateTime answeredAt) {
        return rebuild(questionId, questionHash, questionText, questionType, difficulty,
                knowledgePoints, userAnswer, score, feedback, masteryBefore, masteryAfter,
                followUps, currentFollowUpIdx, hintsGiven, status, answeredAt, null);
    }

    public static InterviewQuestion rebuild(Long questionId, String questionHash, String questionText,
                                            String questionType, DifficultyLevel difficulty,
                                            List<String> knowledgePoints,
                                            String userAnswer, Integer score, String feedback,
                                            Integer masteryBefore, Integer masteryAfter,
                                            List<String> followUps, int currentFollowUpIdx,
                                            List<String> hintsGiven, QuestionStatus status,
                                            LocalDateTime answeredAt, InterviewPhase phase) {
        InterviewQuestion q = new InterviewQuestion(questionId, questionHash, questionText, questionType,
                difficulty, knowledgePoints, userAnswer, score, feedback, masteryBefore, masteryAfter,
                followUps, currentFollowUpIdx, hintsGiven, status, answeredAt, null);
        q.phase = phase;
        return q;
    }

    // ==================== 业务方法 ====================

    /**
     * 回答题目
     *
     * @param userAnswer 用户答案
     * @param score      评分 (0-100)
     * @param feedback   AI 反馈
     */
    public void answer(String userAnswer, int score, String feedback) {
        if (status.isAnswered()) {
            throw new InvalidStateException("题目已经回答", "QUESTION_ALREADY_ANSWERED");
        }
        validateScore(score);
        this.userAnswer = userAnswer;
        this.score = score;
        this.feedback = feedback;
        this.status = QuestionStatus.SCORED;
        this.answeredAt = LocalDateTime.now();
    }

    /**
     * 跳过题目
     */
    public void skip() {
        if (status.isAnswered()) {
            throw new InvalidStateException("题目已经回答", "QUESTION_ALREADY_ANSWERED");
        }
        this.status = QuestionStatus.SKIPPED;
        this.answeredAt = LocalDateTime.now();
    }

    /**
     * 添加提示
     *
     * @param hint 提示内容
     */
    public void addHint(String hint) {
        if (hint != null && !hint.isBlank()) {
            this.hintsGiven.add(hint);
        }
    }

    /**
     * 添加追问
     *
     * @param followUp 追问内容
     */
    public void addFollowUp(String followUp) {
        if (followUp != null && !followUp.isBlank()) {
            this.followUps.add(followUp);
        }
    }

    /**
     * 获取当前追问
     *
     * @return 当前追问（Optional）
     */
    public String getCurrentFollowUp() {
        if (currentFollowUpIdx < followUps.size()) {
            return followUps.get(currentFollowUpIdx);
        }
        return null;
    }

    /**
     * 进入下一个追问
     */
    public void nextFollowUp() {
        if (currentFollowUpIdx < followUps.size() - 1) {
            currentFollowUpIdx++;
        }
    }

    /**
     * 判断是否已回答
     */
    @JsonIgnore
    public boolean isAnswered() {
        return status.isAnswered();
    }

    /**
     * 判断是否得分通过（>= 60）
     */
    @JsonIgnore
    public boolean isPassed() {
        return score != null && score >= 60;
    }

    /**
     * 设置答题前掌握度
     */
    public void setMasteryBefore(int level) {
        if (level < 0 || level > 4) {
            throw new DomainException("掌握度等级必须在 0-4 之间", "INVALID_MASTERY_LEVEL");
        }
        this.masteryBefore = level;
    }

    /**
     * 设置答题后掌握度
     */
    public void setMasteryAfter(int level) {
        if (level < 0 || level > 4) {
            throw new DomainException("掌握度等级必须在 0-4 之间", "INVALID_MASTERY_LEVEL");
        }
        this.masteryAfter = level;
    }

    // ==================== Getter 方法 ====================

    public Long getQuestionId() {
        return questionId;
    }

    public String getQuestionHash() {
        return questionHash;
    }

    public String getQuestionText() {
        return questionText;
    }

    public String getQuestionType() {
        return questionType;
    }

    public DifficultyLevel getDifficulty() {
        return difficulty;
    }

    public List<String> getKnowledgePoints() {
        return Collections.unmodifiableList(knowledgePoints);
    }

    public String getUserAnswer() {
        return userAnswer;
    }

    public Integer getScore() {
        return score;
    }

    public String getFeedback() {
        return feedback;
    }

    public Integer getMasteryBefore() {
        return masteryBefore;
    }

    public Integer getMasteryAfter() {
        return masteryAfter;
    }

    public List<String> getFollowUps() {
        return Collections.unmodifiableList(followUps);
    }

    public int getCurrentFollowUpIdx() {
        return currentFollowUpIdx;
    }

    public List<String> getHintsGiven() {
        return Collections.unmodifiableList(hintsGiven);
    }

    public QuestionStatus getStatus() {
        return status;
    }

    public LocalDateTime getAnsweredAt() {
        return answeredAt;
    }

    public InterviewPhase getPhase() {
        return phase;
    }

    public void setPhase(InterviewPhase phase) {
        this.phase = phase;
    }

    // ==================== 构造函数 ====================

    @com.fasterxml.jackson.annotation.JsonCreator
    private InterviewQuestion(
            @com.fasterxml.jackson.annotation.JsonProperty("questionId") Long questionId,
            @com.fasterxml.jackson.annotation.JsonProperty("questionHash") String questionHash,
            @com.fasterxml.jackson.annotation.JsonProperty("questionText") String questionText,
            @com.fasterxml.jackson.annotation.JsonProperty("questionType") String questionType,
            @com.fasterxml.jackson.annotation.JsonProperty("difficulty") DifficultyLevel difficulty,
            @com.fasterxml.jackson.annotation.JsonProperty("knowledgePoints") List<String> knowledgePoints,
            @com.fasterxml.jackson.annotation.JsonProperty("userAnswer") String userAnswer,
            @com.fasterxml.jackson.annotation.JsonProperty("score") Integer score,
            @com.fasterxml.jackson.annotation.JsonProperty("feedback") String feedback,
            @com.fasterxml.jackson.annotation.JsonProperty("masteryBefore") Integer masteryBefore,
            @com.fasterxml.jackson.annotation.JsonProperty("masteryAfter") Integer masteryAfter,
            @com.fasterxml.jackson.annotation.JsonProperty("followUps") List<String> followUps,
            @com.fasterxml.jackson.annotation.JsonProperty("currentFollowUpIdx") int currentFollowUpIdx,
            @com.fasterxml.jackson.annotation.JsonProperty("hintsGiven") List<String> hintsGiven,
            @com.fasterxml.jackson.annotation.JsonProperty("status") QuestionStatus status,
            @com.fasterxml.jackson.annotation.JsonProperty("answeredAt") LocalDateTime answeredAt,
            @com.fasterxml.jackson.annotation.JsonProperty("phase") InterviewPhase phase) {
        this.questionId = questionId;
        this.questionHash = questionHash;
        this.questionText = questionText;
        this.questionType = questionType != null ? questionType : "knowledge";
        this.difficulty = difficulty != null ? difficulty : DifficultyLevel.MEDIUM;
        this.knowledgePoints = knowledgePoints != null ? new ArrayList<>(knowledgePoints) : new ArrayList<>();
        this.userAnswer = userAnswer;
        this.score = score;
        this.feedback = feedback;
        this.masteryBefore = masteryBefore;
        this.masteryAfter = masteryAfter;
        this.followUps = followUps != null ? new ArrayList<>(followUps) : new ArrayList<>();
        this.currentFollowUpIdx = currentFollowUpIdx;
        this.hintsGiven = hintsGiven != null ? new ArrayList<>(hintsGiven) : new ArrayList<>();
        this.status = status != null ? status : QuestionStatus.PENDING;
        this.answeredAt = answeredAt;
        this.phase = phase;
    }

    // ==================== 校验方法 ====================

    private static void validateQuestionId(Long questionId) {
        if (questionId == null) {
            throw new DomainException("questionId 不能为空", "INVALID_QUESTION_ID");
        }
    }

    private static void validateQuestionHash(String questionHash) {
        if (questionHash == null || questionHash.isBlank()) {
            throw new DomainException("questionHash 不能为空", "INVALID_QUESTION_HASH");
        }
    }

    private static void validateQuestionText(String questionText) {
        if (questionText == null || questionText.isBlank()) {
            throw new DomainException("questionText 不能为空", "INVALID_QUESTION_TEXT");
        }
    }

    private static void validateScore(int score) {
        if (score < 0 || score > 100) {
            throw new DomainException("评分必须在 0-100 之间", "INVALID_SCORE");
        }
    }
}
