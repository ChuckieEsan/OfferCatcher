package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.agent.VisionExtractorAgent;
import com.zju.offercatcher.application.agent.dto.ExtractedQuestionItem;
import com.zju.offercatcher.application.service.ExtractTaskApplicationService;
import com.zju.offercatcher.application.service.IngestFlowService;
import com.zju.offercatcher.domain.question.aggregates.ExtractTask;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.ExtractDto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/extract")
public class ExtractController {

    private static final Logger log = LoggerFactory.getLogger(ExtractController.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ExtractTaskApplicationService taskService;
    private final VisionExtractorAgent visionExtractor;

    public ExtractController(ExtractTaskApplicationService taskService,
                              VisionExtractorAgent visionExtractor) {
        this.taskService = taskService;
        this.visionExtractor = visionExtractor;
    }

    // ==================== 同步提取 ====================

    @PostMapping("/text")
    public ResponseEntity<ExtractResponse> extractText(@Valid @RequestBody ExtractTextRequest request) {
        log.info("Extract from text ({} chars)", request.text().length());
        ExtractedQuestionItem result = visionExtractor.extract(request.text());
        return ResponseEntity.ok(toExtractResponse(result));
    }

    // ==================== 异步任务 ====================

    @PostMapping("/submit")
    public ResponseEntity<SubmitResponse> submit(@Valid @RequestBody SubmitRequest request,
                                                  @UserId String userId) {
        log.info("Submit extract task: user={}, type={}", userId, request.sourceType());
        ExtractTask task = taskService.submit(userId, request.sourceType(),
            request.sourceContent(), request.sourceImages());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(new SubmitResponse(task.getTaskId(), "任务已提交，请稍后查询结果"));
    }

    @GetMapping("/tasks")
    public ResponseEntity<TaskListResponse> listTasks(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @UserId String userId) {
        List<ExtractTask> tasks = taskService.list(userId, status, page, pageSize);
        int total = taskService.count(userId, status);

        List<TaskListItem> items = tasks.stream().map(t -> new TaskListItem(
            t.getTaskId(), t.getStatus().name().toLowerCase(), t.getSourceType(),
            t.getCompanyFromResult(), t.getPositionFromResult(),
            t.getQuestionCount(),
            t.getCreatedAt().format(DTF), t.getUpdatedAt().format(DTF)
        )).toList();

        return ResponseEntity.ok(new TaskListResponse(items, total, page, pageSize));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<TaskResponse> getTask(@PathVariable Long taskId, @UserId String userId) {
        ExtractTask task = taskService.get(taskId, userId);
        return ResponseEntity.ok(toTaskResponse(task));
    }

    @PutMapping("/tasks/{taskId}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable Long taskId,
                                                    @Valid @RequestBody UpdateRequest request,
                                                    @UserId String userId) {
        ExtractTask task = taskService.edit(taskId, userId,
            request.company() != null ? request.company() : "",
            request.position() != null ? request.position() : "",
            request.questions() != null ? request.questions() : List.of());
        return ResponseEntity.ok(toTaskResponse(task));
    }

    @PostMapping("/tasks/{taskId}/confirm")
    public ResponseEntity<ConfirmResponse> confirm(@PathVariable Long taskId,
                                                    @UserId String userId) {
        IngestFlowService.IngestResult result = taskService.confirm(taskId, userId);
        return ResponseEntity.ok(new ConfirmResponse(
            result.processed, result.failed, result.questionIds));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable Long taskId, @UserId String userId) {
        taskService.cancel(taskId, userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/tasks/{taskId}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long taskId, @UserId String userId) {
        boolean deleted = taskService.delete(taskId, userId);
        return deleted ? ResponseEntity.noContent().build()
            : ResponseEntity.notFound().build();
    }

    // ==================== Mappers ====================

    private ExtractResponse toExtractResponse(ExtractedQuestionItem item) {
        List<ExtractedQuestionDto> questions = item.questions().stream()
            .map(q -> new ExtractedQuestionDto(q.questionId(), q.questionText(),
                q.questionType(), q.coreEntities(), q.metadata()))
            .toList();
        return new ExtractResponse(item.company(), item.position(), questions);
    }

    private TaskResponse toTaskResponse(ExtractTask task) {
        return new TaskResponse(
            task.getTaskId(), task.getUserId(), task.getSourceType(),
            task.getSourceContent(), task.getSourceImages(),
            task.getStatus().name().toLowerCase(), task.getExtractedInterview(),
            task.getCreatedAt().format(DTF), task.getUpdatedAt().format(DTF)
        );
    }
}
