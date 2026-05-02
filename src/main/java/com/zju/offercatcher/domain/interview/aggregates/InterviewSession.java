package com.zju.offercatcher.domain.interview.aggregates;

import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.domain.shared.exception.InvalidStateException;
import com.zju.offercatcher.domain.shared.SnowflakeIdGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 面试会话聚合根
 *
 * InterviewSession 是面试领域的聚合根，管理：
 * - 面试配置（公司、岗位、难度）
 * - 面试题目列表
 * - 答题进度和评分统计
 * - 会话状态
 *
 * 聚合边界规则：
 * - 所有题目操作必须通过 InterviewSession 方法
 * - 支持用户隔离（userId 字段）
 */
public class InterviewSession {

    private final Long sessionId;
    private final String userId;
    private final String company;
    private final String position;
    private final DifficultyLevel difficulty;
    private final int totalQuestions;
    private String jdContext;  // JD 解析后的面试上下文（Phase 2 集成 JD 解析后由 JobDescriptionParserAgent 填充）

    private SessionStatus status;
    private final List<InterviewQuestion> questions;
    private int currentQuestionIdx;

    private int correctCount;
    private int totalScore;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建面试会话（工厂方法）
     */
    public static InterviewSession create(String userId, String company, String position,
                                           DifficultyLevel difficulty, int totalQuestions) {
        return create(userId, company, position, difficulty, totalQuestions, null);
    }

    public static InterviewSession create(String userId, String company, String position,
                                           DifficultyLevel difficulty, int totalQuestions,
                                           String jdContext) {
        validateUserId(userId);
        validateTotalQuestions(totalQuestions);
        Long sessionId = SnowflakeIdGenerator.generate();
        LocalDateTime now = LocalDateTime.now();
        InterviewSession session = new InterviewSession(sessionId, userId, company, position,
            difficulty, totalQuestions,
            SessionStatus.ACTIVE, new ArrayList<>(), 0, 0, 0, now, null, now, now);
        session.jdContext = jdContext;
        return session;
    }

    /**
     * 从持久化存储重建（用于 Repository 实现）
     */
    public static InterviewSession rebuild(Long sessionId, String userId, String company,
                                            String position, DifficultyLevel difficulty, int totalQuestions,
                                            SessionStatus status, List<InterviewQuestion> questions,
                                            int currentQuestionIdx, int correctCount, int totalScore,
                                            LocalDateTime startedAt, LocalDateTime endedAt,
                                            LocalDateTime createdAt, LocalDateTime updatedAt) {
        return rebuild(sessionId, userId, company, position, difficulty, totalQuestions,
            status, questions, currentQuestionIdx, correctCount, totalScore,
            startedAt, endedAt, createdAt, updatedAt, null);
    }

    public static InterviewSession rebuild(Long sessionId, String userId, String company,
                                            String position, DifficultyLevel difficulty, int totalQuestions,
                                            SessionStatus status, List<InterviewQuestion> questions,
                                            int currentQuestionIdx, int correctCount, int totalScore,
                                            LocalDateTime startedAt, LocalDateTime endedAt,
                                            LocalDateTime createdAt, LocalDateTime updatedAt,
                                            String jdContext) {
        InterviewSession session = new InterviewSession(sessionId, userId, company, position,
            difficulty, totalQuestions,
            status, questions, currentQuestionIdx, correctCount, totalScore,
            startedAt, endedAt, createdAt, updatedAt);
        session.jdContext = jdContext;
        return session;
    }

    // ==================== 业务方法 ====================

    public Optional<InterviewQuestion> getCurrentQuestion() {
        if (currentQuestionIdx >= 0 && currentQuestionIdx < questions.size()) {
            return Optional.of(questions.get(currentQuestionIdx));
        }
        return Optional.empty();
    }

    public Optional<InterviewQuestion> nextQuestion() {
        if (status == SessionStatus.COMPLETED) {
            throw new InvalidStateException("面试已结束", "SESSION_COMPLETED");
        }
        if (currentQuestionIdx < questions.size() - 1) {
            currentQuestionIdx++;
            this.updatedAt = LocalDateTime.now();
            return getCurrentQuestion();
        }
        return Optional.empty();
    }

    public void answerCurrentQuestion(String userAnswer, int score, String feedback) {
        if (status == SessionStatus.COMPLETED) {
            throw new InvalidStateException("面试已结束", "SESSION_COMPLETED");
        }
        InterviewQuestion current = getCurrentQuestion()
            .orElseThrow(() -> new InvalidStateException("没有当前题目", "NO_CURRENT_QUESTION"));

        current.answer(userAnswer, score, feedback);
        this.totalScore += score;
        if (score >= 60) {
            this.correctCount++;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void skipCurrentQuestion() {
        if (status == SessionStatus.COMPLETED) {
            throw new InvalidStateException("面试已结束", "SESSION_COMPLETED");
        }
        InterviewQuestion current = getCurrentQuestion()
            .orElseThrow(() -> new InvalidStateException("没有当前题目", "NO_CURRENT_QUESTION"));

        current.skip();
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        if (status == SessionStatus.COMPLETED) {
            throw new InvalidStateException("面试已经结束", "ALREADY_COMPLETED");
        }
        this.status = SessionStatus.COMPLETED;
        this.endedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void pause() {
        if (status != SessionStatus.ACTIVE) {
            throw new InvalidStateException("只能暂停进行中的面试", "CANNOT_PAUSE");
        }
        this.status = SessionStatus.PAUSED;
        this.updatedAt = LocalDateTime.now();
    }

    public void resume() {
        if (status != SessionStatus.PAUSED) {
            throw new InvalidStateException("只能恢复暂停的面试", "CANNOT_RESUME");
        }
        this.status = SessionStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    public void addQuestion(InterviewQuestion question) {
        if (questions.size() >= totalQuestions) {
            throw new DomainException("已达到题目总数上限", "MAX_QUESTIONS_REACHED");
        }
        this.questions.add(question);
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isCompleted() {
        return status == SessionStatus.COMPLETED || currentQuestionIdx >= totalQuestions;
    }

    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }

    public boolean isPaused() {
        return status == SessionStatus.PAUSED;
    }

    public double calculateAverageScore() {
        List<InterviewQuestion> scoredQuestions = questions.stream()
            .filter(q -> q.getScore() != null)
            .toList();
        if (scoredQuestions.isEmpty()) {
            return 0.0;
        }
        return scoredQuestions.stream()
            .mapToInt(InterviewQuestion::getScore)
            .average()
            .orElse(0.0);
    }

    public double calculateDurationMinutes() {
        LocalDateTime endTime = endedAt != null ? endedAt : LocalDateTime.now();
        java.time.Duration duration = java.time.Duration.between(startedAt, endTime);
        return duration.toMinutes();
    }

    public boolean isOwnedBy(String requestingUserId) {
        return this.userId.equals(requestingUserId);
    }

    // ==================== Getter 方法 ====================

    public Long getSessionId() {
        return sessionId;
    }

    public String getUserId() {
        return userId;
    }

    public String getCompany() {
        return company;
    }

    public String getPosition() {
        return position;
    }

    public DifficultyLevel getDifficulty() {
        return difficulty;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public List<InterviewQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public int getCurrentQuestionIdx() {
        return currentQuestionIdx;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getTotalScore() {
        return totalScore;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getJdContext() {
        return jdContext;
    }

    public void setJdContext(String jdContext) {
        this.jdContext = jdContext;
    }

    // ==================== 构造函数 ====================

    private InterviewSession(Long sessionId, String userId, String company, String position,
                              DifficultyLevel difficulty, int totalQuestions,
                              SessionStatus status, List<InterviewQuestion> questions,
                              int currentQuestionIdx, int correctCount, int totalScore,
                              LocalDateTime startedAt, LocalDateTime endedAt,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.sessionId = sessionId;
        this.userId = userId;
        this.company = company;
        this.position = position;
        this.difficulty = difficulty != null ? difficulty : DifficultyLevel.MEDIUM;
        this.totalQuestions = totalQuestions;
        this.status = status != null ? status : SessionStatus.ACTIVE;
        this.questions = questions != null ? new ArrayList<>(questions) : new ArrayList<>();
        this.currentQuestionIdx = currentQuestionIdx;
        this.correctCount = correctCount;
        this.totalScore = totalScore;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== 校验方法 ====================

    private static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId 不能为空", "INVALID_USER_ID");
        }
    }

    private static void validateTotalQuestions(int totalQuestions) {
        if (totalQuestions <= 0) {
            throw new DomainException("题目总数必须大于 0", "INVALID_TOTAL_QUESTIONS");
        }
    }
}
