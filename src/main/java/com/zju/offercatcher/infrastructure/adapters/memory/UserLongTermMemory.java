package com.zju.offercatcher.infrastructure.adapters.memory;

import com.zju.offercatcher.application.agent.MemoryExtractionAgent;
import com.zju.offercatcher.application.agent.MemoryRetrievalAgent;
import com.zju.offercatcher.application.service.ChatApplicationService;
import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.message.Msg;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 用户长期记忆实现（对接自有 Postgres + Qdrant + MEMORY.md 体系）。
 * <p>
 * 不依赖 Mem0/ReMe 等外部服务，通过 AgentScope 的 LongTermMemory 接口标准接入。
 * 使用 STATIC_CONTROL 模式——框架自动在 PreCall 调用 retrieve()、PostCall 调用 record()。
 * <p>
 * retrieve(): 注入 MEMORY.md 概要 + Short-term Memory 上下文
 * - Redis 缓存命中 → Agent 精炼的上下文（~1ms）
 * - 缓存 miss → 降级 importance top-K 查询（<10ms）
 * <p>
 * record(): 异步触发记忆检索预热 + 记忆提取
 * - MemoryRetrievalAgent：基于最后一条消息检索，预热 STM 缓存
 * - MemoryExtractionAgent：分析对话提取偏好/行为/摘要
 */
public class UserLongTermMemory implements LongTermMemory {

    private static final Logger log = LoggerFactory.getLogger(UserLongTermMemory.class);

    private final MemoryApplicationService memoryService;
    private final MemoryRetrievalAgent retrievalAgent;
    private final MemoryExtractionAgent extractionAgent;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final ChatApplicationService chatService;
    private final Executor workerExecutor;
    private final String userId;
    private final Long conversationId;

    public UserLongTermMemory(MemoryApplicationService memoryService,
                              MemoryRetrievalAgent retrievalAgent,
                              MemoryExtractionAgent extractionAgent,
                              SessionSummaryRepository sessionSummaryRepository,
                              ChatApplicationService chatService,
                              Executor workerExecutor,
                              String userId, Long conversationId) {
        this.memoryService = memoryService;
        this.retrievalAgent = retrievalAgent;
        this.extractionAgent = extractionAgent;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.chatService = chatService;
        this.workerExecutor = workerExecutor;
        this.userId = userId;
        this.conversationId = conversationId;
    }

    // ==================== retrieve（PreCall — 同步路径） ====================

    @Override
    public Mono<String> retrieve(Msg msg) {
        return Mono.fromCallable(this::buildMemoryContext);
    }

    private String buildMemoryContext() {
        StringBuilder sb = new StringBuilder();

        // 1. MEMORY.md 概要（始终注入，~400 tokens）
        String memoryMd = memoryService.getMemoryContent(userId);
        if (memoryMd != null && !memoryMd.isBlank()) {
            sb.append(memoryMd);
        }

        // 2. Short-term Memory
        //    命中（~1ms）：上一轮 MemoryRetrievalAgent 精炼的上下文
        //    miss → 降级（<10ms）：SELECT * ORDER BY importance_score LIMIT 5
        String shortTerm = retrievalAgent.getCachedContext(userId, conversationId);
        if (shortTerm == null || shortTerm.isBlank()) {
            shortTerm = buildDegradedContext();
        }
        if (shortTerm != null && !shortTerm.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n---\n\n## 相关历史会话\n");
            }
            sb.append(shortTerm);
        }

        return sb.toString();
    }

    private String buildDegradedContext() {
        List<SessionSummary> topK = sessionSummaryRepository.findTopKByImportance(userId, 5);
        if (topK.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (SessionSummary s : topK) {
            sb.append("- ").append(s.getSummary()).append("\n");
        }
        log.debug("UserLongTermMemory using degraded context: {} summaries", topK.size());
        return sb.toString();
    }

    // ==================== record（PostCall — 异步 fire-and-forget） ====================

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 启动检索 Agent 预热 Short-term Memory
                String lastQuery = extractLastUserQuery(msgs);
                Conversation conv = chatService.getConversation(userId, conversationId).orElse(null);
                List<Message> recentMessages = conv != null ? conv.getMessages() : List.of();
                if (lastQuery != null && !lastQuery.isBlank()) {
                    retrievalAgent.retrieveAsync(userId, conversationId, lastQuery, recentMessages);
                }

                // 2. 启动记忆提取 Agent 更新 Long-term Memory
                if (conv != null && !conv.getMessages().isEmpty()) {
                    extractionAgent.extractMemories(userId, conversationId, conv.getMessages());
                }
            } catch (Exception e) {
                log.error("Memory post-processing failed for conversation {}: {}",
                        conversationId, e.getMessage(), e);
            }
        }, workerExecutor);

        return Mono.empty();
    }

    private String extractLastUserQuery(List<Msg> msgs) {
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Msg m = msgs.get(i);
            if (m.getRole() == io.agentscope.core.message.MsgRole.USER) {
                String text = m.getTextContent();
                if (text != null && !text.isBlank()) return text;
            }
        }
        return null;
    }
}
