package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.memory.aggregates.Memory;
import com.zju.offercatcher.domain.memory.entities.MemoryReference;
import com.zju.offercatcher.domain.shared.enums.MemoryStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Memory JPA 实体
 *
 * 存储用户记忆主文档和引用文件列表。
 */
@Entity
@Table(name = "memories")
@Getter
@Setter
@NoArgsConstructor
public class MemoryJpaEntity {

    @Id
    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private MemoryStatus status;

    @Column(name = "references", columnDefinition = "JSON")
    @Convert(converter = MemoryReferenceConverter.class)
    private List<MemoryReference> references;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 从领域模型转换
     */
    public static MemoryJpaEntity fromDomain(Memory memory) {
        MemoryJpaEntity entity = new MemoryJpaEntity();
        entity.setUserId(memory.getUserId());
        entity.setContent(memory.getContent());
        entity.setStatus(memory.getStatus());
        entity.setReferences(new java.util.ArrayList<>(memory.getReferences()));
        entity.setCreatedAt(memory.getCreatedAt());
        entity.setUpdatedAt(memory.getUpdatedAt());
        return entity;
    }

    /**
     * 转换为领域模型
     */
    public Memory toDomain() {
        return Memory.rebuild(userId, content, status, references, createdAt, updatedAt);
    }
}