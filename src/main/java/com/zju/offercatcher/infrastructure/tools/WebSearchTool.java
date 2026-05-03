package com.zju.offercatcher.infrastructure.tools;

import com.zju.offercatcher.infrastructure.adapters.websearch.TavilySearchAdapter;
import com.zju.offercatcher.infrastructure.adapters.websearch.WebSearchResult;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 网络搜索工具
 * <p>
 * 使用 Tavily Search API 进行网络搜索，获取实时信息。
 * 对应 Python: app/infrastructure/tools/search_web.py
 */
@Component
public class WebSearchTool {

    private static final Logger log = LoggerFactory.getLogger(WebSearchTool.class);

    private final TavilySearchAdapter searchAdapter;

    public WebSearchTool(TavilySearchAdapter searchAdapter) {
        this.searchAdapter = searchAdapter;
    }

    @Tool(name = "search_web",
            description = "使用 Tavily Search API 搜索网络获取实时信息。"
                    + "适合查找最新的面试题、公司信息、技术趋势等。返回格式化的搜索结果。")
    public String searchWeb(
            @ToolParam(name = "query", required = true,
                    description = "搜索查询关键词")
            String query,
            @ToolParam(name = "max_results", required = false,
                    description = "最大返回结果数，默认 3")
            int maxResults,
            ToolExecutionContext context
    ) {
        int limit = maxResults > 0 ? maxResults : 3;
        log.debug("search_web: query={}, maxResults={}", query, limit);

        List<WebSearchResult> results = searchAdapter.search(query, limit);

        if (results.isEmpty()) {
            return "未找到相关信息。";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            WebSearchResult r = results.get(i);
            sb.append("**").append(i + 1).append(". ").append(r.title()).append("**\n");
            sb.append("链接：").append(r.url()).append("\n");
            sb.append(truncate(r.snippet(), 300)).append("\n");
            sb.append("---\n");
        }
        return sb.toString().trim();
    }

    private static String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
