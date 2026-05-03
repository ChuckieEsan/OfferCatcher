package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.MemoryRepository;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.common.CacheKeys;
import com.zju.offercatcher.infrastructure.common.PromptLoader;
import com.zju.offercatcher.infrastructure.config.LLMModelFactory;
import com.zju.offercatcher.infrastructure.tools.MemoryTools;
import com.zju.offercatcher.infrastructure.tools.UserToolContext;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆管理 Agent 服务
 *
 * 在对话结束后异步执行（由调用方通过 workerExecutor 调度），分析对话内容并更新用户记忆。
 * 使用 ReActAgent 自主调用 MemoryTools。
 * 对应 Python: app/application/agents/memory/agent.py
 */
@Service
public class MemoryExtractionAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryExtractionAgent.class);

    private final MemoryApplicationService memoryService;
    private final MemoryRepository memoryRepository;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final OnnxEmbeddingAdapter embeddingAdapter;
    private final MemoryTools memoryTools;
    private final PromptLoader promptLoader;
    private final RedisTemplate<String, String> redisTemplate;

    // Cached stateless resources
    private final OpenAIChatModel cachedModel;
    private final Toolkit cachedToolkit;

    public MemoryExtractionAgent(MemoryApplicationService memoryService,
                                 MemoryRepository memoryRepository,
                                 SessionSummaryRepository sessionSummaryRepository,
                                 OnnxEmbeddingAdapter embeddingAdapter,
                                 MemoryTools memoryTools,
                                 PromptLoader promptLoader,
                                 LLMModelFactory modelFactory,
                                 RedisTemplate<String, String> redisTemplate) {
        this.memoryService = memoryService;
        this.memoryRepository = memoryRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.embeddingAdapter = embeddingAdapter;
        this.memoryTools = memoryTools;
        this.promptLoader = promptLoader;
        this.redisTemplate = redisTemplate;

        this.cachedModel = modelFactory.createSimple("deepseek", false);

        this.cachedToolkit = new Toolkit(ToolkitConfig.defaultConfig());
        this.cachedToolkit.registerTool(memoryTools);
    }

    /**
     * 异步执行记忆提取
     *
     * 对话结束后 fire-and-forget 调用，不阻塞主流程。
     * 使用游标机制避免重复处理已提取过的消息。
     */
    public void extractMemories(String userId, Long conversationId, List<Message> messages) {
        try {
            // 1. 读取上次游标
            String cursorKey = CacheKeys.memoryCursor(userId, conversationId);
            String cursorVal = redisTemplate.opsForValue().get(cursorKey);
            Long lastCursor = cursorVal != null ? Long.parseLong(cursorVal) : null;

            // 2. 过滤游标后的新消息
            List<Message> newMessages = lastCursor == null
                ? messages
                : messages.stream()
                    .filter(m -> m.getMessageId() > lastCursor)
                    .collect(Collectors.toList());

            if (newMessages.isEmpty()) {
                log.debug("No new messages since cursor {} for conversation {}", lastCursor, conversationId);
                return;
            }

            // 3. 检查游标后是否有主 Agent 写入标记
            if (hasMemoryWriteMarker(newMessages)) {
                log.info("Memory update skipped: main agent already wrote in conversation {}", conversationId);
                updateCursor(userId, conversationId, getLatestMessageId(messages));
                return;
            }

            log.info("Memory extraction started for conversation {}, {} new messages (cursor: {})",
                conversationId, newMessages.size(), lastCursor);

            String currentPreferences = memoryService.getPreferences(userId);
            String currentBehaviors = memoryService.getBehaviors(userId);
            List<SessionSummary> recentSummaries = sessionSummaryRepository.findTopKByImportance(userId, 10);

            String memoryContext = formatSessionSummaries(recentSummaries);
            String formattedNew = formatMessages(newMessages);

            String prompt = promptLoader.render("memory_agent.md",
                "memory_context", memoryContext,
                "history_messages", "（无历史消息）",
                "new_messages", formattedNew,
                "current_preferences", currentPreferences != null ? currentPreferences : "",
                "current_behaviors", currentBehaviors != null ? currentBehaviors : "",
                "conversation_id", String.valueOf(conversationId),
                "user_id", userId
            );

            ReActAgent agent = createMemoryAgent(userId);
            List<Msg> input = List.of(
                Msg.builder().role(MsgRole.USER).textContent(prompt).build()
            );

            agent.call(input).block();
            log.info("Memory extraction completed for conversation {}", conversationId);

            // 4. 更新游标
            updateCursor(userId, conversationId, getLatestMessageId(messages));

        } catch (Exception e) {
            log.error("Memory extraction failed for conversation {}: {}", conversationId, e.getMessage(), e);
        }
    }

    // ==================== 游标管理 ====================

    private void updateCursor(String userId, Long conversationId, Long messageId) {
        redisTemplate.opsForValue().set(
            CacheKeys.memoryCursor(userId, conversationId),
            String.valueOf(messageId));
    }

    private Long getLatestMessageId(List<Message> messages) {
        return messages.stream()
            .mapToLong(Message::getMessageId)
            .max()
            .orElse(0L);
    }

    private boolean hasMemoryWriteMarker(List<Message> messages) {
        return messages.stream()
            .filter(m -> m.getRole() == MessageRole.ASSISTANT)
            .anyMatch(m -> m.getContent() != null
                && m.getContent().contains("<memory_write>"));
    }

    private ReActAgent createMemoryAgent(String userId) {
        ToolExecutionContext toolContext = ToolExecutionContext.builder()
            .register("userContext", new UserToolContext(userId))
            .build();

        return ReActAgent.builder()
            .name("memory-agent")
            .sysPrompt("""
                你是记忆管理 Agent，分析对话内容并更新用户记忆。
                目标是积累可复用的用户偏好和行为模式，而非记录所有对话内容。
                根据对话内容，调用合适的工具更新记忆。
                """)
            .model(cachedModel)
            .toolkit(cachedToolkit)
            .toolExecutionContext(toolContext)
            .maxIters(8)
            .generateOptions(GenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(2048)
                .build())
            .build();
    }

    // ==================== Helpers ====================

    private String formatMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "（无消息）";
        }
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            String roleLabel = "user".equals(msg.getRole().name().toLowerCase()) ? "用户" : "AI";
            sb.append(roleLabel).append(": ").append(msg.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    private String formatSessionSummaries(List<SessionSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return "（无历史摘要）";
        }
        StringBuilder sb = new StringBuilder();
        for (SessionSummary s : summaries) {
            String date = s.getCreatedAt() != null ? s.getCreatedAt().toString() : "unknown";
            sb.append("- ").append(s.getSummary())
                .append(" [").append(date)
                .append(", ").append(s.getMemoryLayer().name()).append("]\n");
        }
        return sb.toString();
    }
}
