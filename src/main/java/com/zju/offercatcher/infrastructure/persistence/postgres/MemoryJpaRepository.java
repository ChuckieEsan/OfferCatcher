package com.zju.offercatcher.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Memory JPA Repository
 *
 * 用于 PostgreSQL 记忆存储。
 */
@Repository
public interface MemoryJpaRepository extends JpaRepository<MemoryJpaEntity, String> {

    /**
     * 根据用户 ID 查找记忆
     */
    @Query("SELECT m FROM MemoryJpaEntity m WHERE m.userId = :userId")
    Optional<MemoryJpaEntity> findByUserId(@Param("userId") String userId);

    /**
     * 检查用户是否存在记忆
     */
    @Query("SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END FROM MemoryJpaEntity m WHERE m.userId = :userId")
    boolean existsByUserId(@Param("userId") String userId);

    /**
     * 删除用户记忆
     */
    @Modifying
    @Query("DELETE FROM MemoryJpaEntity m WHERE m.userId = :userId")
    int deleteByUserId(@Param("userId") String userId);
}