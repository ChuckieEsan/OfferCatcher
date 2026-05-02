package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.agent.VisionExtractorAgent;
import com.zju.offercatcher.application.agent.dto.ExtractedQuestionItem;
import com.zju.offercatcher.application.service.ExtractTaskApplicationService;
import com.zju.offercatcher.application.service.IngestFlowApplicationService;
import com.zju.offercatcher.domain.question.aggregates.ExtractTask;
import com.zju.offercatcher.infrastructure.adapters.ocr.OcrAdapter;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.ExtractDto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/extract")
public class ExtractController {

    private static final Logger log = LoggerFactory.getLogger(ExtractController.class);
    private static final DateTimeFormatter DTF = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ExtractTaskApplicationService taskService;
    private final VisionExtractorAgent visionExtractor;
    private final OcrAdapter ocrAdapter;

    public ExtractController(ExtractTaskApplicationService taskService,
                              VisionExtractorAgent visionExtractor,
                              OcrAdapter ocrAdapter) {
        this.taskService = taskService;
        this.visionExtractor = visionExtractor;
        this.ocrAdapter = ocrAdapter;
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
        IngestFlowApplicationService.IngestResult result = taskService.confirm(taskId, userId);
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

    // ==================== 图片提取 ====================

    @PostMapping("/image")
    public ResponseEntity<ExtractResponse> extractFromUpload(
        @RequestParam("images") List<MultipartFile> images) {
        log.info("Extract from {} uploaded images", images.size());

        List<String> tempPaths = new ArrayList<>();
        try {
            for (MultipartFile file : images) {
                File tempFile = File.createTempFile("extract_", "_" + UUID.randomUUID().toString().substring(0, 8));
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    fos.write(file.getBytes());
                }
                tempPaths.add(tempFile.getAbsolutePath());
                log.debug("Saved upload to temp: {}", tempFile.getAbsolutePath());
            }

            ExtractedQuestionItem result = visionExtractor.extractFromImages(tempPaths);
            return ResponseEntity.ok(toExtractResponse(result));
        } catch (IOException e) {
            log.error("Failed to process uploaded images: {}", e.getMessage(), e);
            throw new RuntimeException("图片处理失败: " + e.getMessage(), e);
        } finally {
            for (String path : tempPaths) {
                try {
                    Files.deleteIfExists(java.nio.file.Path.of(path));
                } catch (IOException e) {
                    log.warn("Failed to delete temp file: {}", path);
                }
            }
        }
    }

    @PostMapping("/image/base64")
    public ResponseEntity<ExtractResponse> extractFromBase64(
        @Valid @RequestBody ImageExtractRequest request) {
        log.info("Extract from {} base64/URL images", request.images().size());

        ExtractedQuestionItem result = visionExtractor.extractFromImages(request.images());
        return ResponseEntity.ok(toExtractResponse(result));
    }

    // ==================== Mappers ====================

    private ExtractResponse toExtractResponse(ExtractedQuestionItem item) {
        List<ExtractedQuestionDto> questions = item.questions().stream()
            .map(q -> new ExtractedQuestionDto(q.questionHash(), q.questionText(),
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
