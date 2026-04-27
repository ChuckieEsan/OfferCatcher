package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * InterviewSession JPA 实体
 */
@Entity
@Table(name = "interview_sessions", indexes = {
    @Index(name = "idx_sessions_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_sessions_user_status", columnList = "user_id, status"),
    @Index(name = "idx_sessions_user_company", columnList = "user_id, company")
})
@Getter
@Setter
@NoArgsConstructor
public class InterviewSessionJpaEntity {

    @Id
    @Column(name = "session_id")
    private Long sessionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "company")
    private String company;

    @Column(name = "position")
    private String position;

    @Column(name = "difficulty", nullable = false)
    @Enumerated(EnumType.STRING)
    private DifficultyLevel difficulty;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private SessionStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "questions", columnDefinition = "jsonb")
    private List<InterviewQuestion> questions;

    @Column(name = "current_question_idx")
    private int currentQuestionIdx;

    @Column(name = "correct_count")
    private int correctCount;

    @Column(name = "total_score")
    private int totalScore;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static InterviewSessionJpaEntity fromDomain(com.zju.offercatcher.domain.interview.aggregates.InterviewSession session) {
        InterviewSessionJpaEntity entity = new InterviewSessionJpaEntity();
        entity.setSessionId(session.getSessionId());
        entity.setUserId(session.getUserId());
        entity.setCompany(session.getCompany());
        entity.setPosition(session.getPosition());
        entity.setDifficulty(session.getDifficulty());
        entity.setTotalQuestions(session.getTotalQuestions());
        entity.setStatus(session.getStatus());
        entity.setQuestions(new java.util.ArrayList<>(session.getQuestions()));
        entity.setCurrentQuestionIdx(session.getCurrentQuestionIdx());
        entity.setCorrectCount(session.getCorrectCount());
        entity.setTotalScore(session.getTotalScore());
        entity.setStartedAt(session.getStartedAt());
        entity.setEndedAt(session.getEndedAt());
        entity.setCreatedAt(session.getCreatedAt());
        entity.setUpdatedAt(session.getUpdatedAt());
        return entity;
    }

    public com.zju.offercatcher.domain.interview.aggregates.InterviewSession toDomain() {
        return com.zju.offercatcher.domain.interview.aggregates.InterviewSession.rebuild(
            sessionId, userId, company, position, difficulty, totalQuestions,
            status, questions, currentQuestionIdx, correctCount, totalScore,
            startedAt, endedAt, createdAt, updatedAt
        );
    }
}
