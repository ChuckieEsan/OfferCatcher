package com.zju.offercatcher.infrastructure.tools;

import com.zju.offercatcher.application.service.CacheApplicationService;
import com.zju.offercatcher.application.service.RetrievalApplicationService;
import com.zju.offercatcher.infrastructure.common.CacheKeys;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 搜题工具
 * <p>
 * 两阶段检索：向量召回 + Rerank 精排，带缓存。
 * 对应 Python: app/infrastructure/tools/search_questions.py
 */
@Component
public class SearchQuestionsTool {

    private static final Logger log = LoggerFactory.getLogger(SearchQuestionsTool.class);

    private final RetrievalApplicationService retrievalService;
    private final CacheApplicationService cacheService;

    public SearchQuestionsTool(RetrievalApplicationService retrievalService,
                               CacheApplicationService cacheService) {
        this.retrievalService = retrievalService;
        this.cacheService = cacheService;
    }

    @Tool(name = "search_questions",
            description = "从本地向量数据库搜索面试题。使用两阶段检索：向量召回 + Rerank 精排。"
                    + "支持按公司、岗位过滤。返回格式化的题目列表（含题目、答案、公司、岗位）。")
    public String searchQuestions(
            @ToolParam(name = "query", required = true,
                    description = "搜索查询关键词，如 'Redis 缓存雪崩'")
            String query,
            @ToolParam(name = "company", required = false,
                    description = "公司名称过滤，如 '阿里巴巴'")
            String company,
            @ToolParam(name = "position", required = false,
                    description = "岗位名称过滤，如 '后端开发'")
            String position,
            @ToolParam(name = "k", required = false,
                    description = "返回结果数量，默认 5")
            int k,
            ToolExecutionContext context
    ) {
        String userId = getUserId(context);
        int topK = k > 0 ? k : 5;
        String cacheKey = CacheKeys.toolSearchQuestions(
                CacheKeys.hashParams(query, company, position, topK));

        log.debug("search_questions: userId={}, query={}, company={}, position={}, k={}",
                userId, query, company, position, topK);

        List<RetrievalApplicationService.SearchResult> results =
                retrievalService.searchWithRerank(userId, query, company, position, topK, 3);

        if (results.isEmpty()) {
            return "未找到相关题目。";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            var r = results.get(i);
            sb.append("**").append(i + 1).append(". ").append(r.company()).append(" | ")
                    .append(r.position()).append("**\n");
            sb.append("题目：").append(truncate(r.questionText(), 200)).append("\n");
            if (r.questionAnswer() != null && !r.questionAnswer().isBlank()) {
                sb.append("答案：").append(truncate(r.questionAnswer(), 300)).append("\n");
            }
            sb.append("相似度：").append(String.format("%.2f", r.score())).append("\n");
            sb.append("---\n");
        }
        return sb.toString().trim();
    }

    private static String getUserId(ToolExecutionContext context) {
        UserToolContext ctx = context.get(UserToolContext.KEY, UserToolContext.class);
        return ctx != null ? ctx.userId() : "anonymous";
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
