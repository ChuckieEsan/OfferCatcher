package com.zju.offercatcher.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobDescriptionJpaRepository extends JpaRepository<JobDescriptionJpaEntity, Long> {
    Optional<JobDescriptionJpaEntity> findByIdAndUserId(Long id, String userId);

    List<JobDescriptionJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId);
}
