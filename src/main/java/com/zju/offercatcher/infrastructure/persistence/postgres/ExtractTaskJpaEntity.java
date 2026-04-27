package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.question.aggregates.ExtractTask;
import com.zju.offercatcher.domain.question.aggregates.ExtractTaskStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "extract_tasks")
@Getter
@Setter
@NoArgsConstructor
public class ExtractTaskJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long taskId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "source_content", columnDefinition = "TEXT")
    private String sourceContent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "source_images", columnDefinition = "jsonb")
    private List<String> sourceImages;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExtractTaskStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_interview", columnDefinition = "jsonb")
    private Map<String, Object> extractedInterview;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static ExtractTaskJpaEntity fromDomain(ExtractTask task) {
        ExtractTaskJpaEntity e = new ExtractTaskJpaEntity();
        e.taskId = task.getTaskId();
        e.userId = task.getUserId();
        e.sourceType = task.getSourceType();
        e.sourceContent = task.getSourceContent();
        e.sourceImages = task.getSourceImages();
        e.status = task.getStatus();
        e.extractedInterview = task.getExtractedInterview();
        e.createdAt = task.getCreatedAt();
        e.updatedAt = task.getUpdatedAt();
        return e;
    }

    public ExtractTask toDomain() {
        return ExtractTask.rebuild(taskId, userId, sourceType, sourceContent,
            sourceImages, status, extractedInterview, createdAt, updatedAt);
    }
}
