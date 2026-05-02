package com.zju.offercatcher.domain.interview.repositories;

import com.zju.offercatcher.domain.interview.aggregates.JobDescription;

import java.util.List;
import java.util.Optional;

public interface JobDescriptionRepository {
    Optional<JobDescription> findById(Long id);
    List<JobDescription> findByUserId(String userId);
    void save(JobDescription jd);
    void deleteById(Long id, String userId);
}
