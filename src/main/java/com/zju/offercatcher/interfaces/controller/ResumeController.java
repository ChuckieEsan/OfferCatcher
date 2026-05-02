package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.agent.ResumeAnalysisAgent;
import com.zju.offercatcher.application.agent.dto.ResumeAnalysisOutput;
import com.zju.offercatcher.infrastructure.file.ResumeParseService;
import com.zju.offercatcher.interfaces.dto.ResumeDto.AnalysisResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/resume")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    private final ResumeParseService parseService;
    private final ResumeAnalysisAgent analysisAgent;

    public ResumeController(ResumeParseService parseService,
                             ResumeAnalysisAgent analysisAgent) {
        this.parseService = parseService;
        this.analysisAgent = analysisAgent;
    }

    @PostMapping("/upload")
    public ResponseEntity<AnalysisResponse> upload(@RequestParam("file") MultipartFile file) {
        log.info("Resume upload: {}", file.getOriginalFilename());

        String text = parseService.parse(file);
        if (text.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ResumeAnalysisOutput output = analysisAgent.analyze(text);
        return ResponseEntity.ok(AnalysisResponse.from(output));
    }
}
