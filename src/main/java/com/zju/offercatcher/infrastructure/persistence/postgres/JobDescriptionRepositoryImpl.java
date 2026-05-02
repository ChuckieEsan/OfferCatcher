package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.repositories.JobDescriptionRepository;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class JobDescriptionRepositoryImpl implements JobDescriptionRepository {

    private final JobDescriptionJpaRepository jpaRepository;

    public JobDescriptionRepositoryImpl(JobDescriptionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<JobDescription> findById(Long id) {
        return jpaRepository.findById(id).map(JobDescriptionJpaEntity::toDomain);
    }

    @Override
    public List<JobDescription> findByUserId(String userId) {
        return jpaRepository.findByUserIdOrderByCreatedAtDesc(userId)
            .stream()
            .map(JobDescriptionJpaEntity::toDomain)
            .toList();
    }

    @Override
    @Transactional
    public void save(JobDescription jd) {
        jpaRepository.save(JobDescriptionJpaEntity.fromDomain(jd));
    }

    @Override
    @Transactional
    public void deleteById(Long id, String userId) {
        JobDescriptionJpaEntity entity = jpaRepository.findById(id)
            .orElseThrow(() -> new DomainException("JD not found: " + id, "JD_NOT_FOUND"));
        if (!entity.getUserId().equals(userId)) {
            throw new UnauthorizedOperationException(userId, id.toString(), "delete JD");
        }
        jpaRepository.delete(entity);
    }
}
