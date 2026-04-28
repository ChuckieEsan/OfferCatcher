package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.service.QuestionApplicationService;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.QuestionDto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/questions")
public class QuestionController {

    private static final Logger log = LoggerFactory.getLogger(QuestionController.class);

    private final QuestionApplicationService questionService;

    public QuestionController(QuestionApplicationService questionService) {
        this.questionService = questionService;
    }

    @PostMapping
    public ResponseEntity<Response> create(@UserId String userId, @Valid @RequestBody CreateRequest req) {
        QuestionType questionType = QuestionType.fromValue(req.questionType());
        List<String> entities = req.coreEntities() != null ? req.coreEntities() : List.of();
        Question q = questionService.createQuestion(userId, req.questionText(), req.company(),
            req.position(), questionType, entities);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(q));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Response> get(@PathVariable Long id) {
        return questionService.getQuestion(id)
            .map(q -> ResponseEntity.ok(toResponse(q)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<Response> update(@PathVariable Long id,
                                           @Valid @RequestBody UpdateRequest req) {
        MasteryLevel ml = req.masteryLevel() != null ? MasteryLevel.fromLevel(req.masteryLevel()) : null;
        return questionService.updateQuestion(id, req.answer(), ml,
                req.questionText(), req.coreEntities())
            .map(q -> ResponseEntity.ok(toResponse(q)))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @UserId String userId) {
        boolean deleted = questionService.deleteQuestion(id, userId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<ListResponse> list(
        @UserId String userId,
        @RequestParam(required = false) String company,
        @RequestParam(required = false) String position,
        @RequestParam(required = false) String questionType,
        @RequestParam(required = false) Integer masteryLevel,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) String clusterId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {

        QuestionType qt = questionType != null ? QuestionType.fromValue(questionType) : null;
        MasteryLevel ml = masteryLevel != null ? MasteryLevel.fromLevel(masteryLevel) : null;

        List<Question> questions = questionService.listQuestions(userId, company, position,
            qt, ml, keyword, clusterId, page, pageSize);
        List<Response> items = questions.stream().map(QuestionController::toResponse).toList();
        return ResponseEntity.ok(new ListResponse(items, items.size(), page, pageSize));
    }

    @PostMapping("/{id}/regenerate")
    public ResponseEntity<Response> regenerate(@PathVariable Long id) {
        log.info("Regenerate answer: {}", id);
        return questionService.regenerateAnswer(id)
            .map(q -> ResponseEntity.ok(toResponse(q)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<Response> publish(@UserId String userId, @PathVariable Long id) {
        log.info("Publish question to public: {}", id);
        return questionService.publishQuestion(id, userId)
            .map(q -> ResponseEntity.ok(toResponse(q)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/batch/answers")
    public ResponseEntity<BatchAnswersResponse> batchAnswers(@Valid @RequestBody BatchAnswersRequest req) {
        Map<Long, String> answers = questionService.getBatchAnswers(req.questionIds());
        return ResponseEntity.ok(new BatchAnswersResponse(answers));
    }

    // ==================== Mappers ====================

    static Response toResponse(Question q) {
        return new Response(
            q.getId(), q.getQuestionHash(), q.getQuestionText(), q.getCompany(), q.getPosition(),
            q.getQuestionType().getValue(), q.getMasteryLevel().getLevel(),
            new ArrayList<>(q.getCoreEntities()), q.getAnswer(),
            new ArrayList<>(q.getClusterIds()), q.getMetadata(),
            q.getVisibility().name(), q.getSourceType().name(),
            q.getCreatedAt().toString(), q.getUpdatedAt().toString()
        );
    }
}
