package com.zju.offercatcher.infrastructure.tools;

import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.domain.memory.aggregates.Memory;
import com.zju.offercatcher.domain.memory.entities.MemoryReference;
import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 记忆相关工具
 * <p>
 * 对应 Python: app/infrastructure/tools/memory_tools.py
 */
@Component
public class MemoryTools {

    private static final Logger log = LoggerFactory.getLogger(MemoryTools.class);

    private final MemoryApplicationService memoryService;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final OnnxEmbeddingAdapter embeddingAdapter;

    public MemoryTools(MemoryApplicationService memoryService,
                       SessionSummaryRepository sessionSummaryRepository,
                       OnnxEmbeddingAdapter embeddingAdapter) {
        this.memoryService = memoryService;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.embeddingAdapter = embeddingAdapter;
    }

    @Tool(name = "load_memory_reference",
            description = "加载用户的记忆引用文件（如 preferences.md、behaviors.md）。"
                    + "当 MEMORY.md 中的概要信息不足以回答问题时使用。")
    public String loadMemoryReference(
            @ToolParam(name = "reference_name", required = true,
                    description = "引用文件名称，如 'preferences' 或 'behaviors'")
            String referenceName,
            ToolExecutionContext context
    ) {
        String userId = getUserId(context);
        log.debug("load_memory_reference: userId={}, referenceName={}", userId, referenceName);

        Memory memory = memoryService.getMemory(userId);
        return memory.getReference(referenceName)
                .map(MemoryReference::getContent)
                .orElse("未找到 reference: " + referenceName);
    }

    @Tool(name = "search_session_history",
            description = "搜索用户的历史会话摘要。使用向量语义搜索，找到与查询最相关的历史会话。")
    public String searchSessionHistory(
            @ToolParam(name = "query", required = true,
                    description = "搜索查询，如 'Java 多线程面试'")
            String query,
            @ToolParam(name = "top_k", required = false,
                    description = "返回结果数量，默认 3")
            int topK,
            ToolExecutionContext context
    ) {
        String userId = getUserId(context);
        int k = topK > 0 ? topK : 3;
        log.debug("search_session_history: userId={}, query={}, topK={}", userId, query, k);

        if (!embeddingAdapter.isInitialized()) {
            return "搜索服务不可用（Embedding 模型未加载）。";
        }

        float[] queryVector = embeddingAdapter.embed(query);
        List<SessionSummary> results = sessionSummaryRepository.searchByVector(
                userId, queryVector, k);

        if (results.isEmpty()) {
            return "未找到相关历史会话。";
        }

        StringBuilder sb = new StringBuilder("### 相关历史会话\n\n");
        for (SessionSummary s : results) {
            sb.append("#### ").append(truncate(s.getSummary(), 80)).append("\n");
            sb.append(s.getSummary()).append("\n");
            sb.append("重要性：").append(String.format("%.2f", s.getImportanceScore()));
            sb.append(" | 层级：").append(s.getMemoryLayer().name()).append("\n\n");
        }
        return sb.toString().trim();
    }

    @Tool(name = "update_preferences",
            description = "更新用户的偏好设置（preferences.md）。当用户明确表达偏好或反馈时调用。")
    public String updatePreferences(
            @ToolParam(name = "content", required = true,
                    description = "偏好设置内容（Markdown 格式）")
            String content,
            ToolExecutionContext context
    ) {
        String userId = getUserId(context);
        log.debug("update_preferences: userId={}", userId);

        memoryService.updatePreferences(userId, content);
        return "<memory_write>preferences</memory_write>\n偏好设置已更新。";
    }

    @Tool(name = "update_behaviors",
            description = "更新用户的行为模式记录（behaviors.md）。当观察到新的用户行为模式时调用。")
    public String updateBehaviors(
            @ToolParam(name = "content", required = true,
                    description = "行为模式内容（Markdown 格式）")
            String content,
            ToolExecutionContext context
    ) {
        String userId = getUserId(context);
        log.debug("update_behaviors: userId={}", userId);

        memoryService.updateBehaviors(userId, content);
        return "<memory_write>behaviors</memory_write>\n行为模式已更新。";
    }

    // ==================== Helpers ====================

    private static String getUserId(ToolExecutionContext context) {
        UserToolContext ctx = context.get(UserToolContext.KEY, UserToolContext.class);
        return ctx != null ? ctx.userId() : "anonymous";
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
