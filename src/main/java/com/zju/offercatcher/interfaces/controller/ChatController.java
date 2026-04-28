package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.agent.ChatAgentService;
import com.zju.offercatcher.application.service.ChatApplicationService;
import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.ChatDto.*;
import io.agentscope.core.agent.Event;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final ChatApplicationService chatService;
    private final ChatAgentService chatAgent;

    public ChatController(ChatApplicationService chatService, ChatAgentService chatAgent) {
        this.chatService = chatService;
        this.chatAgent = chatAgent;
    }

    @PostMapping(value = "/api/v1/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@UserId String userId, @Valid @RequestBody ChatRequest req) {
        return chatAgent.chatStream(req.message(), req.conversationId(), userId)
            .map(event -> {
                String type = event.getType() != null ? event.getType().name().toLowerCase() : "unknown";
                String content = event.getMessage() != null ? event.getMessage().getTextContent() : "";
                if (content == null) content = "";
                return "data: {\"type\":\"" + type + "\",\"content\":\"" + escapeJson(content) + "\"}\n\n";
            })
            .concatWithValues("data: [DONE]\n\n");
    }

    @GetMapping("/api/v1/conversations")
    public ResponseEntity<ConversationListResponse> listConversations(
        @UserId String userId,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        List<Conversation> conversations = chatService.listConversations(userId, page, pageSize);
        List<ConversationResponse> items = conversations.stream()
            .map(ChatController::toConversationResponse)
            .toList();
        int total = (int) chatService.countConversations(userId);
        return ResponseEntity.ok(new ConversationListResponse(items, total, page, pageSize));
    }

    @PostMapping("/api/v1/conversations")
    public ResponseEntity<ConversationResponse> createConversation(
        @UserId String userId,
        @Valid @RequestBody ConversationCreateRequest req) {
        Conversation c = chatService.createConversation(userId, req.title());
        return ResponseEntity.ok(toConversationResponse(c));
    }

    @GetMapping("/api/v1/conversations/{id}")
    public ResponseEntity<ConversationResponse> getConversation(
        @UserId String userId, @PathVariable Long id) {
        return chatService.getConversation(userId, id)
            .map(c -> ResponseEntity.ok(toConversationResponse(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/api/v1/conversations/{id}/title")
    public ResponseEntity<Void> updateTitle(
        @UserId String userId, @PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        boolean updated = chatService.updateTitle(userId, id, title);
        return updated ? ResponseEntity.ok().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/api/v1/conversations/{id}/generate-title")
    public ResponseEntity<ConversationResponse> generateTitle(
        @UserId String userId, @PathVariable Long id) {
        log.info("Generate title for conversation: {}", id);
        return chatService.generateTitle(userId, id)
            .map(c -> ResponseEntity.ok(toConversationResponse(c)))
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/api/v1/conversations/{id}")
    public ResponseEntity<Void> deleteConversation(@UserId String userId, @PathVariable Long id) {
        boolean deleted = chatService.deleteConversation(userId, id);
        return deleted ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    // ==================== Mappers ====================

    static ConversationResponse toConversationResponse(Conversation c) {
        List<MessageResponse> msgs = c.getMessages().stream()
            .map(m -> new MessageResponse(
                m.getMessageId(), m.getRole().name().toLowerCase(),
                m.getContent(), m.getCreatedAt().toString()))
            .toList();
        return new ConversationResponse(
            c.getConversationId(), c.getTitle(),
            c.getStatus().name().toLowerCase(), c.messageCount(),
            c.getCreatedAt().toString(), c.getUpdatedAt().toString(), msgs
        );
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
