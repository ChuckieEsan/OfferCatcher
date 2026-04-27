package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.agent.ScorerAgent;
import com.zju.offercatcher.application.agent.dto.ScoreResult;
import com.zju.offercatcher.interfaces.dto.ScoreDto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1/score")
public class ScoreController {

    private static final Logger log = LoggerFactory.getLogger(ScoreController.class);

    private final ScorerAgent scorerAgent;

    public ScoreController(ScorerAgent scorerAgent) {
        this.scorerAgent = scorerAgent;
    }

    @PostMapping
    public ResponseEntity<ScoreResult> score(@Valid @RequestBody ScoreRequest request) {
        log.info("Score answer: questionId={}", request.questionId());
        try {
            ScoreResult result = scorerAgent.score(request.questionId(), request.userAnswer());
            return ResponseEntity.ok(result);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Scoring failed: {}", e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "评分失败: " + e.getMessage());
        }
    }
}
