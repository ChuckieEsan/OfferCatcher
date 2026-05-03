package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.MemoryDto.Response;
import com.zju.offercatcher.interfaces.dto.MemoryDto.UpdateBehaviorsRequest;
import com.zju.offercatcher.interfaces.dto.MemoryDto.UpdatePreferencesRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/memory")
public class MemoryController {

    private static final Logger log = LoggerFactory.getLogger(MemoryController.class);

    private final MemoryApplicationService memoryService;

    public MemoryController(MemoryApplicationService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping("/me")
    public ResponseEntity<Response> getMemory(@UserId String userId) {
        return ResponseEntity.ok(toResponse(userId));
    }

    @GetMapping("/me/content")
    public ResponseEntity<String> getContent(@UserId String userId) {
        return ResponseEntity.ok(memoryService.getMemoryContent(userId));
    }

    @GetMapping("/me/preferences")
    public ResponseEntity<String> getPreferences(@UserId String userId) {
        return ResponseEntity.ok(memoryService.getPreferences(userId));
    }

    @PutMapping("/me/preferences")
    public ResponseEntity<Void> updatePreferences(
            @UserId String userId, @Valid @RequestBody UpdatePreferencesRequest req) {
        memoryService.updatePreferences(userId, req.content());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me/behaviors")
    public ResponseEntity<String> getBehaviors(@UserId String userId) {
        return ResponseEntity.ok(memoryService.getBehaviors(userId));
    }

    @PutMapping("/me/behaviors")
    public ResponseEntity<Void> updateBehaviors(
            @UserId String userId, @Valid @RequestBody UpdateBehaviorsRequest req) {
        memoryService.updateBehaviors(userId, req.content());
        return ResponseEntity.ok().build();
    }

    // ==================== Mappers ====================

    private Response toResponse(String userId) {
        return new Response(userId,
                memoryService.getMemoryContent(userId),
                memoryService.getPreferences(userId),
                memoryService.getBehaviors(userId));
    }
}
