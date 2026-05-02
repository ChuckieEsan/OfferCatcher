package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.agent.JobDescriptionParserAgent;
import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.repositories.JobDescriptionRepository;
import com.zju.offercatcher.domain.shared.exception.DomainException;
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
    private static final String USER_ID = "user-1"; // TODO: auth integration

    private final JobDescriptionParserAgent jobDescriptionParserService;
    private final JobDescriptionRepository jdRepository;

    public JobDescriptionController(JobDescriptionParserAgent jobDescriptionParserService,
                                    JobDescriptionRepository jdRepository) {
        this.jobDescriptionParserService = jobDescriptionParserService;
        this.jdRepository = jdRepository;
    }

    @PostMapping("/parse")
    public ResponseEntity<JobDescriptionDto> parse(@RequestBody JobDescriptionDto.ParseRequest request) {
        log.info("JD parse request: length={}", request.jdText().length());
        JobDescription jd = jobDescriptionParserService.parseAndSave(USER_ID, request.jdText());
        return ResponseEntity.ok(JobDescriptionDto.from(jd));
    }

    @GetMapping
    public ResponseEntity<List<JobDescriptionDto>> list() {
        List<JobDescription> jds = jdRepository.findByUserId(USER_ID);
        return ResponseEntity.ok(jds.stream().map(JobDescriptionDto::from).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<JobDescriptionDto> get(@PathVariable Long id) {
        JobDescription jd = jdRepository.findById(id)
            .orElseThrow(() -> new DomainException("JD not found: " + id, "JD_NOT_FOUND"));
        return ResponseEntity.ok(JobDescriptionDto.from(jd));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jdRepository.deleteById(id, USER_ID);
        return ResponseEntity.noContent().build();
    }
}
