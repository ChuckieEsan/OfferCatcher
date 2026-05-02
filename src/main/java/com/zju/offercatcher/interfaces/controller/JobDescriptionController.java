package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.agent.JobDescriptionParserAgent;
import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.repositories.JobDescriptionRepository;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.JobDescriptionDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/jd")
public class JobDescriptionController {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionController.class);

    private final JobDescriptionParserAgent parserAgent;
    private final JobDescriptionRepository jobDescriptionRepository;

    public JobDescriptionController(JobDescriptionParserAgent parserAgent,
                                    JobDescriptionRepository jobDescriptionRepository) {
        this.parserAgent = parserAgent;
        this.jobDescriptionRepository = jobDescriptionRepository;
    }

    @PostMapping("/parse")
    public ResponseEntity<JobDescriptionDto> parse(@UserId String userId,
                                                    @RequestBody JobDescriptionDto.ParseRequest request) {
        log.info("JD parse request: userId={}, length={}", userId,
            request.jdText() != null ? request.jdText().length() : 0);
        JobDescription jd = parserAgent.parseAndSave(userId, request.jdText());
        return ResponseEntity.ok(JobDescriptionDto.from(jd));
    }

    @GetMapping
    public ResponseEntity<List<JobDescriptionDto>> list(@UserId String userId) {
        List<JobDescription> jds = jobDescriptionRepository.findByUserId(userId);
        return ResponseEntity.ok(jds.stream().map(JobDescriptionDto::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDescriptionDto> get(@UserId String userId, @PathVariable Long id) {
        JobDescription jd = jobDescriptionRepository.findById(id)
            .filter(j -> j.isOwnedBy(userId))
            .orElseThrow(() -> new DomainException("JD not found: " + id, "JD_NOT_FOUND"));
        return ResponseEntity.ok(JobDescriptionDto.from(jd));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@UserId String userId, @PathVariable Long id) {
        jobDescriptionRepository.deleteById(id, userId);
        return ResponseEntity.noContent().build();
    }
}
