package com.zju.offercatcher.domain.memory.entities;

import com.zju.offercatcher.domain.shared.exception.DomainException;

import java.time.LocalDateTime;

/**
 * 记忆引用实体
 *
 * MemoryReference 是 Memory 聚合内的实体，表示一个引用文件。
 * 包括 preferences.md、behaviors.md 和自定义 Skill。
 *
 * 设计原则：
 * - 通过 Memory.addReference() 创建
 * - 引用名称唯一（在同一 Memory 内）
 * - 支持内容更新
 */
public class MemoryReference {

    private final String referenceName;
    private String content;
    private LocalDateTime updatedAt;

    /**
     * 创建引用（工厂方法）
     *
     * @param referenceName 引用名称
     * @param content 文件内容（Markdown 格式）
     * @return 新创建的 MemoryReference 实体
     */
    public static MemoryReference create(String referenceName, String content) {
        validateReferenceName(referenceName);
        validateContent(content);
        return new MemoryReference(referenceName, content, LocalDateTime.now());
    }

    /**
     * 从持久化存储重建（用于 Repository 实现）
     *
     * @param referenceName 引用名称
     * @param content 文件内容
     * @param updatedAt 最后更新时间
     * @return 重建的 MemoryReference 实体
     */
    public static MemoryReference rebuild(String referenceName, String content, LocalDateTime updatedAt) {
        return new MemoryReference(referenceName, content, updatedAt);
    }

    // ==================== 业务方法 ====================

    /**
     * 更新内容
     *
     * @param newContent 新内容
     */
    public void updateContent(String newContent) {
        validateContent(newContent);
        this.content = newContent;
        this.updatedAt = LocalDateTime.now();
    }

    // ==================== Getter 方法 ====================

    public String getReferenceName() {
        return referenceName;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    // ==================== 构造函数 ====================

    private MemoryReference(String referenceName, String content, LocalDateTime updatedAt) {
        this.referenceName = referenceName;
        this.content = content;
        this.updatedAt = updatedAt;
    }

    // ==================== 校验方法 ====================

    private static void validateReferenceName(String referenceName) {
        if (referenceName == null || referenceName.isBlank()) {
            throw new DomainException("referenceName 不能为空", "INVALID_REFERENCE_NAME");
        }
    }

    private static void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new DomainException("content 不能为空", "INVALID_REFERENCE_CONTENT");
        }
    }
}