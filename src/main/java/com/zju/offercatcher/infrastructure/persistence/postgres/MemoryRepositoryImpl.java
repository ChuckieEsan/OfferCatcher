package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.memory.aggregates.Memory;
import com.zju.offercatcher.domain.memory.repositories.MemoryRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Memory Repository 实现
 * <p>
 * 基于 PostgreSQL 的记忆存储实现。
 */
@Repository
public class MemoryRepositoryImpl implements MemoryRepository {

    private final MemoryJpaRepository jpaRepository;

    public MemoryRepositoryImpl(MemoryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<Memory> findByUserId(String userId) {
        return jpaRepository.findByUserId(userId)
                .map(MemoryJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(Memory memory) {
        MemoryJpaEntity entity = MemoryJpaEntity.fromDomain(memory);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void deleteByUserId(String userId) {
        jpaRepository.deleteByUserId(userId);
    }

    @Override
    public boolean existsByUserId(String userId) {
        return jpaRepository.existsByUserId(userId);
    }
}