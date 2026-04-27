package com.zju.offercatcher.domain.memory.repositories;

import com.zju.offercatcher.domain.memory.aggregates.Memory;

import java.util.Optional;

/**
 * 记忆仓储接口
 *
 * 设计原则：
 * - 定义在 Domain 层，实现由 Infrastructure 层提供
 * - 一个用户只有一个 Memory 实例
 * - 所有操作需验证用户权限
 */
public interface MemoryRepository {

    /**
     * 查询用户的记忆
     *
     * @param userId 用户 ID
     * @return 用户的 Memory（Optional）
     */
    Optional<Memory> findByUserId(String userId);

    /**
     * 保存记忆
     *
     * @param memory 记忆实体
     */
    void save(Memory memory);

    /**
     * 删除用户的记忆
     *
     * @param userId 用户 ID
     */
    void deleteByUserId(String userId);

    /**
     * 检查用户是否存在记忆
     *
     * @param userId 用户 ID
     * @return true 如果存在
     */
    boolean existsByUserId(String userId);
}