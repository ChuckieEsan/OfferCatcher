package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.agent.InterviewAgent;
import com.zju.offercatcher.application.service.InterviewApplicationService;
import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.InterviewDto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/interview")
public class InterviewController {

    private static final Logger log = LoggerFactory.getLogger(InterviewController.class);

    private final InterviewApplicationService interviewService;
    private final InterviewAgent interviewAgent;

    public InterviewController(InterviewApplicationService interviewService,
                               InterviewAgent interviewAgent) {
        this.interviewService = interviewService;
        this.interviewAgent = interviewAgent;
    }

    @PostMapping("/sessions")
    public ResponseEntity<SessionResponse> createSession(
        @UserId String userId, @Valid @RequestBody CreateSessionRequest req) {
        DifficultyLevel difficulty = DifficultyLevel.fromValue(req.difficulty());
        InterviewSession session = interviewAgent.createSession(
            userId, req.company(), req.position(), difficulty, req.totalQuestions(), req.jdId());
        return ResponseEntity.ok(toSessionResponse(session));
    }

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(
        @UserId String userId,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String status) {
        SessionStatus st = status != null ? SessionStatus.valueOf(status.toUpperCase()) : null;
        List<InterviewSession> sessions = interviewService.listSessions(userId, limit, st);
        return ResponseEntity.ok(sessions.stream().map(InterviewController::toSessionResponse).toList());
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<SessionResponse> getSession(@UserId String userId, @PathVariable Long id) {
        return interviewService.getSession(id, userId)
            .map(s -> ResponseEntity.ok(toSessionResponse(s)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/sessions/{id}/answer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> submitAnswer(
        @UserId String userId, @PathVariable Long id, @Valid @RequestBody SubmitAnswerRequest req) {
        return interviewAgent.processAnswerStream(id, userId, req.answer());
    }

    @PostMapping(value = "/sessions/{id}/hint", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getHint(@UserId String userId, @PathVariable Long id) {
        return interviewAgent.getHintStream(id, userId);
    }

    @PostMapping("/sessions/{id}/skip")
    public ResponseEntity<SessionResponse> skipQuestion(@UserId String userId, @PathVariable Long id) {
        InterviewSession session = interviewService.skipQuestion(id, userId);
        return ResponseEntity.ok(toSessionResponse(session));
    }

    @PostMapping("/sessions/{id}/pause")
    public ResponseEntity<SessionResponse> pauseSession(@UserId String userId, @PathVariable Long id) {
        InterviewSession session = interviewService.pauseSession(id, userId);
        return ResponseEntity.ok(toSessionResponse(session));
    }

    @PostMapping("/sessions/{id}/resume")
    public ResponseEntity<SessionResponse> resumeSession(@UserId String userId, @PathVariable Long id) {
        InterviewSession session = interviewService.resumeSession(id, userId);
        return ResponseEntity.ok(toSessionResponse(session));
    }

    @PostMapping("/sessions/{id}/end")
    public ResponseEntity<SessionResponse> endSession(@UserId String userId, @PathVariable Long id) {
        InterviewSession session = interviewService.completeSession(id, userId);
        return ResponseEntity.ok(toSessionResponse(session));
    }

    @GetMapping("/sessions/{id}/report")
    public ResponseEntity<ReportResponse> getReport(@UserId String userId, @PathVariable Long id) {
        return interviewService.getSession(id, userId)
            .map(s -> ResponseEntity.ok(toReportResponse(s)))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> deleteSession(@UserId String userId, @PathVariable Long id) {
        boolean deleted = interviewService.deleteSession(id, userId);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==================== Mappers ====================

    static SessionResponse toSessionResponse(InterviewSession s) {
        List<QuestionItemResponse> questions = s.getQuestions().stream()
            .map(InterviewController::toQuestionItem)
            .toList();
        return new SessionResponse(
            s.getSessionId(), s.getCompany(), s.getPosition(),
            s.getDifficulty().getValue(), s.getTotalQuestions(),
            s.getStatus().name().toLowerCase(),
            s.getCurrentQuestionIdx(), s.getCorrectCount(), s.getTotalScore(),
            s.getCreatedAt().toString(), s.getUpdatedAt().toString(),
            null, null, questions
        );
    }

    static QuestionItemResponse toQuestionItem(InterviewQuestion q) {
        return new QuestionItemResponse(
            q.getQuestionId(), q.getQuestionText(), q.getQuestionType(),
            q.getDifficulty().getValue(), q.getKnowledgePoints(),
            q.getUserAnswer(), q.getScore(), q.getFeedback(),
            q.getStatus().name().toLowerCase(), q.getFollowUps().size(),
            q.getPhase() != null ? q.getPhase().getValue() : null
        );
    }

    static ReportResponse toReportResponse(InterviewSession s) {
        List<QuestionItemResponse> questions = s.getQuestions().stream()
            .map(InterviewController::toQuestionItem)
            .toList();
        long answeredCount = s.getQuestions().stream().filter(InterviewQuestion::isAnswered).count();
        return new ReportResponse(
            s.getSessionId(), s.getCompany(), s.getPosition(),
            s.getDifficulty().getValue(), s.getStatus().name().toLowerCase(),
            s.getTotalQuestions(), (int) answeredCount, s.getCorrectCount(),
            s.getTotalScore(), s.calculateAverageScore(), s.calculateDurationMinutes(),
            questions
        );
    }
}
