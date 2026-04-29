package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.MemoryRepository;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
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
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 记忆管理 Agent 服务
 *
 * 在对话结束后异步执行（由调用方通过 workerExecutor 调度），分析对话内容并更新用户记忆。
 * 使用 ReActAgent 自主调用 MemoryTools。
 * 对应 Python: app/application/agents/memory/agent.py
 */
@Service
public class MemoryAgentService {

    private static final Logger log = LoggerFactory.getLogger(MemoryAgentService.class);

    private final MemoryApplicationService memoryService;
    private final MemoryRepository memoryRepository;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final OnnxEmbeddingAdapter embeddingAdapter;
    private final MemoryTools memoryTools;
    private final PromptLoader promptLoader;
    private final LLMProperties llmProperties;

    // Cached stateless resources
    private final OpenAIChatModel cachedModel;
    private final Toolkit cachedToolkit;

    public MemoryAgentService(MemoryApplicationService memoryService,
                               MemoryRepository memoryRepository,
                               SessionSummaryRepository sessionSummaryRepository,
                               OnnxEmbeddingAdapter embeddingAdapter,
                               MemoryTools memoryTools,
                               PromptLoader promptLoader,
                               LLMProperties llmProperties) {
        this.memoryService = memoryService;
        this.memoryRepository = memoryRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.embeddingAdapter = embeddingAdapter;
        this.memoryTools = memoryTools;
        this.promptLoader = promptLoader;
        this.llmProperties = llmProperties;

        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.cachedModel = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(false)
            .build();

        this.cachedToolkit = new Toolkit(ToolkitConfig.defaultConfig());
        this.cachedToolkit.registerTool(memoryTools);
    }

    /**
     * 异步执行记忆提取
     *
     * 对话结束后 fire-and-forget 调用，不阻塞主流程。
     * 使用 workerExecutor 线程池执行，避免阻塞 HTTP 线程。
     */
    public void extractMemories(String userId, Long conversationId, List<Message> messages) {
        try {
            log.info("Memory extraction started for conversation {}", conversationId);

            String currentPreferences = memoryService.getPreferences(userId);
            String currentBehaviors = memoryService.getBehaviors(userId);
            List<SessionSummary> recentSummaries = sessionSummaryRepository.findTopKByImportance(userId, 10);

            String memoryContext = formatSessionSummaries(recentSummaries);
            String formattedNew = formatMessages(messages);

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

        } catch (Exception e) {
            log.error("Memory extraction failed for conversation {}: {}", conversationId, e.getMessage(), e);
        }
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
