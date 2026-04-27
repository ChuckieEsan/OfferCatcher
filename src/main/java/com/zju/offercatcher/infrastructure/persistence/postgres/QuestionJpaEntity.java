package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.shared.enums.*;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Question JPA 实体
 *
 * 存储题目所有元数据，与 Qdrant 中的向量关联。
 * Qdrant 只存 userId 和 visibility，其他元数据都在此实体中。
 */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class QuestionJpaEntity {

    @Id
    @Column(name = "question_id", length = 32)
    private String questionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "question_text", columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false)
    private QuestionType questionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false)
    private Visibility visibility;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "mastery_level")
    @Enumerated(EnumType.STRING)
    private MasteryLevel masteryLevel = MasteryLevel.LEVEL_0;

    @Column(name = "company")
    private String company;

    @Column(name = "position")
    private String position;

    @Column(name = "answer", columnDefinition = "TEXT")
    private String answer;

    @ElementCollection
    @CollectionTable(name = "question_entities", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "entity")
    private List<String> coreEntities = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "question_clusters", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "cluster_id")
    private List<String> clusterIds = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 从领域模型转换
     */
    public static QuestionJpaEntity fromDomain(com.zju.offercatcher.domain.question.aggregates.Question question) {
        QuestionJpaEntity entity = new QuestionJpaEntity();
        entity.setQuestionId(question.getQuestionId());
        entity.setUserId(question.getUserId());
        entity.setQuestionText(question.getQuestionText());
        entity.setQuestionType(question.getQuestionType());
        entity.setVisibility(question.getVisibility());
        entity.setSourceType(question.getSourceType());
        entity.setMasteryLevel(question.getMasteryLevel());
        entity.setCompany(question.getCompany());
        entity.setPosition(question.getPosition());
        entity.setAnswer(question.getAnswer());
        entity.setCoreEntities(new ArrayList<>(question.getCoreEntities()));
        entity.setClusterIds(new ArrayList<>(question.getClusterIds()));
        entity.setCreatedAt(question.getCreatedAt());
        entity.setUpdatedAt(question.getUpdatedAt());
        return entity;
    }

    /**
     * 转换为领域模型
     */
    public com.zju.offercatcher.domain.question.aggregates.Question toDomain() {
        return com.zju.offercatcher.domain.question.aggregates.Question.rebuild(
            questionId,
            userId,
            questionText,
            questionType,
            company,
            position,
            coreEntities,
            answer,
            masteryLevel,
            clusterIds,
            new HashMap<>(),
            visibility,
            sourceType,
            createdAt,
            updatedAt
        );
    }

    /**
     * 更新实体（从领域模型）
     */
    public void updateFromDomain(com.zju.offercatcher.domain.question.aggregates.Question question) {
        this.questionText = question.getQuestionText();
        this.answer = question.getAnswer();
        this.masteryLevel = question.getMasteryLevel();
        this.visibility = question.getVisibility();
        this.coreEntities = new ArrayList<>(question.getCoreEntities());
        this.clusterIds = new ArrayList<>(question.getClusterIds());
        this.updatedAt = question.getUpdatedAt();
    }
}