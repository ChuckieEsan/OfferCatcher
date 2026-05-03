package com.zju.offercatcher.domain.memory.aggregates;

import com.zju.offercatcher.domain.memory.entities.MemoryReference;
import com.zju.offercatcher.domain.shared.enums.MemoryStatus;
import com.zju.offercatcher.domain.shared.exception.DomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * 记忆聚合根
 * <p>
 * Memory 是记忆领域的聚合根，管理：
 * - 用户记忆主文档（MEMORY.md）
 * - 聚合内的引用文件列表
 * <p>
 * 聚合边界规则：
 * - MEMORY.md 始终加载，提供概要信息
 * - references 按需加载
 * - 一个用户只有一个 Memory 实例
 * - 支持用户隔离（userId 字段）
 */
public class Memory {

    private final String userId;
    private String content;
    private MemoryStatus status;
    private List<MemoryReference> references;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /**
     * 创建记忆（工厂方法）
     *
     * @param userId  用户唯一标识
     * @param content MEMORY.md 内容
     * @return 新创建的 Memory 聚合根
     */
    public static Memory create(String userId, String content) {
        validateUserId(userId);
        validateContent(content);
        LocalDateTime now = LocalDateTime.now();
        return new Memory(userId, content, MemoryStatus.ACTIVE, new ArrayList<>(), now, now);
    }

    /**
     * 从持久化存储重建（用于 Repository 实现）
     */
    public static Memory rebuild(String userId, String content, MemoryStatus status,
                                 List<MemoryReference> references, LocalDateTime createdAt, LocalDateTime updatedAt) {
        return new Memory(userId, content, status,
                references != null ? new ArrayList<>(references) : new ArrayList<>(),
                createdAt, updatedAt);
    }

    // ==================== 业务方法 ====================

    /**
     * 更新主文档内容
     *
     * @param newContent 新内容
     */
    public void updateContent(String newContent) {
        validateContent(newContent);
        this.content = newContent;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 添加引用文件（聚合内操作）
     * <p>
     * 如果同名引用已存在，则更新内容；否则添加新引用。
     *
     * @param reference 引用实体
     */
    public void addReference(MemoryReference reference) {
        if (reference == null) {
            throw new DomainException("reference 不能为空", "INVALID_REFERENCE");
        }
        // 检查是否已存在同名引用
        Optional<MemoryReference> existing = getReference(reference.getReferenceName());
        if (existing.isPresent()) {
            // 更新现有引用
            existing.get().updateContent(reference.getContent());
        } else {
            // 添加新引用
            this.references.add(reference);
        }
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 获取引用文件
     *
     * @param referenceName 引用名称
     * @return 引用实体（Optional）
     */
    public Optional<MemoryReference> getReference(String referenceName) {
        return references.stream()
                .filter(r -> r.getReferenceName().equals(referenceName))
                .findFirst();
    }

    /**
     * 移除引用文件
     *
     * @param referenceName 引用名称
     * @return true 如果成功移除
     */
    public boolean removeReference(String referenceName) {
        for (int i = 0; i < references.size(); i++) {
            if (references.get(i).getReferenceName().equals(referenceName)) {
                references.remove(i);
                this.updatedAt = LocalDateTime.now();
                return true;
            }
        }
        return false;
    }

    /**
     * 归档记忆
     */
    public void archive() {
        this.status = MemoryStatus.ARCHIVED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 恢复记忆
     */
    public void restore() {
        this.status = MemoryStatus.ACTIVE;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 判断是否为用户所有
     *
     * @param requestingUserId 请求用户 ID
     * @return true 如果是所有者
     */
    public boolean isOwnedBy(String requestingUserId) {
        return this.userId.equals(requestingUserId);
    }

    /**
     * 判断是否活跃
     */
    public boolean isActive() {
        return status == MemoryStatus.ACTIVE;
    }

    /**
     * 判断是否归档
     */
    public boolean isArchived() {
        return status == MemoryStatus.ARCHIVED;
    }

    // ==================== Getter 方法 ====================

    public String getUserId() {
        return userId;
    }

    public String getContent() {
        return content;
    }

    public MemoryStatus getStatus() {
        return status;
    }

    public List<MemoryReference> getReferences() {
        return Collections.unmodifiableList(references);
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // ==================== 构造函数 ====================

    private Memory(String userId, String content, MemoryStatus status, List<MemoryReference> references,
                   LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.userId = userId;
        this.content = content;
        this.status = status;
        this.references = references;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // ==================== 校验方法 ====================

    private static void validateUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId 不能为空", "INVALID_USER_ID");
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new DomainException("content 不能为空", "INVALID_MEMORY_CONTENT");
        }
    }
}