package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.infrastructure.common.CacheKeys;
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆检索 Agent（Short-term Memory 的核心）
 *
 * 不是 RAG 管道，而是一个独立的 ReActAgent。
 * 拥有 search_session_history 和 load_memory_reference 工具，
 * 自主分析用户意图、多角度检索、精炼输出。
 *
 * 异步执行（fire-and-forget），结果写入 Redis 供下一轮对话注入。
 *
 * 对应 Python: app/infrastructure/persistence/memory/memory_retrieval.py
 */
@Service
public class MemoryRetrievalAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetrievalAgent.class);
    private static final int MAX_ITERS = 5;
    private static final int CONTEXT_TTL_SECONDS = 3600;
    private static final int MAX_OUTPUT_TOKENS = 500;

    private final MemoryTools memoryTools;
    private final RedisTemplate<String, String> redisTemplate;
    private final OpenAIChatModel retrievalModel;
    private final Toolkit cachedToolkit;

    public MemoryRetrievalAgent(MemoryTools memoryTools,
                                RedisTemplate<String, String> redisTemplate,
                                LLMModelFactory modelFactory) {
        this.memoryTools = memoryTools;
        this.redisTemplate = redisTemplate;

        this.retrievalModel = modelFactory.createSimple("deepseek", false);

        this.cachedToolkit = new Toolkit(ToolkitConfig.defaultConfig());
        this.cachedToolkit.registerTool(memoryTools);

        log.info("MemoryRetrievalAgent initialized");
    }

    /**
     * 异步检索并缓存。
     *
     * 基于传入的 query（上一轮消息）检索，结果写入 Redis 供下一轮注入。
     * fire-and-forget，不阻塞调用方。
     *
     * @param userId          用户 ID
     * @param conversationId  会话 ID
     * @param query           用户本轮消息
     * @param recentMessages  最近几轮对话
     */
    public void retrieveAsync(String userId, Long conversationId,
                               String query, List<Message> recentMessages) {
        CompletableFuture.runAsync(() -> {
            try {
                log.debug("MemoryRetrievalAgent started for conversation {}", conversationId);

                ReActAgent agent = createRetrievalAgent(userId);
                String taskPrompt = buildTaskPrompt(query, recentMessages);

                Msg result = agent.call(List.of(
                    Msg.builder().role(MsgRole.USER).textContent(taskPrompt).build()
                )).block();

                String context = result != null ? result.getTextContent() : null;
                if (context != null && !context.isBlank()) {
                    String cacheKey = CacheKeys.memoryContext(userId, conversationId);
                    redisTemplate.opsForValue().set(
                        cacheKey, context, Duration.ofSeconds(CONTEXT_TTL_SECONDS));
                    log.info("MemoryRetrievalAgent cached {} chars for conversation {}",
                        context.length(), conversationId);
                } else {
                    log.debug("MemoryRetrievalAgent produced empty context for conversation {}",
                        conversationId);
                }
            } catch (Exception e) {
                log.error("MemoryRetrievalAgent failed for conversation {}: {}",
                    conversationId, e.getMessage(), e);
            }
        });
    }

    /**
     * 读取异步检索的缓存结果。
     *
     * @return 缓存内容，null 表示未命中
     */
    public String getCachedContext(String userId, Long conversationId) {
        return redisTemplate.opsForValue()
            .get(CacheKeys.memoryContext(userId, conversationId));
    }

    // ==================== Agent 创建 ====================

    private ReActAgent createRetrievalAgent(String userId) {
        ToolExecutionContext toolContext = ToolExecutionContext.builder()
            .register("userContext", new UserToolContext(userId))
            .build();

        return ReActAgent.builder()
            .name("memory-retrieval")
            .sysPrompt("""
                你是记忆检索专家。你的任务：
                1. 分析用户问题，理解其核心意图
                2. 使用 search_session_history 从多个角度检索相关历史记忆
                3. 如果用户问题涉及偏好或行为规则，使用 load_memory_reference
                4. 整合搜索结果，输出一段精炼的上下文，供主 Agent 使用

                原则：
                - 从 2-3 个不同角度构造搜索查询，覆盖不同表述方式
                - 只提取与当前问题真正相关的内容，无关信息不要包含
                - 如果有用户偏好或行为模式与问题相关，务必引用
                - 输出控制在 500 tokens 以内
                - 如果搜索结果相关性低，尝试换个角度重新搜索
                """)
            .model(retrievalModel)
            .toolkit(cachedToolkit)
            .toolExecutionContext(toolContext)
            .maxIters(MAX_ITERS)
            .generateOptions(GenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .build())
            .build();
    }

    private String buildTaskPrompt(String query, List<Message> recentMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务\n");
        sb.append("检索与用户问题相关的历史记忆，"
            + "整合为一段精炼的上下文供主 Agent 使用。\n\n");

        sb.append("## 用户问题\n");
        sb.append(query).append("\n\n");

        if (recentMessages != null && !recentMessages.isEmpty()) {
            sb.append("## 最近对话\n");
            int start = Math.max(0, recentMessages.size() - 6);
            for (int i = start; i < recentMessages.size(); i++) {
                Message m = recentMessages.get(i);
                String role = m.isUserMessage() ? "用户" : "AI";
                sb.append(role).append(": ")
                    .append(truncate(m.getContent(), 200)).append("\n\n");
            }
        }

        sb.append("## 检索策略\n");
        sb.append("1. 分析核心意图，构造多个不同角度的搜索查询\n");
        sb.append("2. 涉及\"怎么设置\"、\"推荐\"等，同时检查 preferences\n");
        sb.append("3. 结果不足时换个角度重新搜索\n");
        sb.append("4. 只提取真正相关的内容，输出 ≤ 500 tokens\n");

        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
