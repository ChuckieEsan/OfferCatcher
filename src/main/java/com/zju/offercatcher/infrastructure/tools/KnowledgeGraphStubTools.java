package com.zju.offercatcher.infrastructure.tools;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 知识图谱工具（桩代码）
 *
 * Neo4j 尚未集成，返回提示信息。
 * 对应 Python:
 *   app/infrastructure/tools/get_knowledge_relations.py
 *   app/infrastructure/tools/get_company_hot_topics.py
 *   app/infrastructure/tools/get_cross_company_trends.py
 */
@Component
public class KnowledgeGraphStubTools {

    @Tool(name = "get_knowledge_relations",
          description = "查询某个知识点的关联知识点。当前知识图谱服务暂未开放。")
    public String getKnowledgeRelations(
        @ToolParam(name = "entity", required = true,
                   description = "知识点实体名称")
        String entity,
        @ToolParam(name = "limit", required = false,
                   description = "返回数量限制，默认 5")
        int limit,
        ToolExecutionContext context
    ) {
        return "知识图谱服务暂未开放。请使用 search_questions 工具搜索相关题目。";
    }

    @Tool(name = "get_company_hot_topics",
          description = "查询某公司的高频考点。当前知识图谱服务暂未开放。")
    public String getCompanyHotTopics(
        @ToolParam(name = "company", required = true,
                   description = "公司名称")
        String company,
        @ToolParam(name = "limit", required = false,
                   description = "返回数量限制，默认 10")
        int limit,
        ToolExecutionContext context
    ) {
        return "知识图谱服务暂未开放。请使用 search_questions 工具搜索 " + company + " 的相关题目。";
    }

    @Tool(name = "get_cross_company_trends",
          description = "查询跨公司的考点趋势。当前知识图谱服务暂未开放。")
    public String getCrossCompanyTrends(
        @ToolParam(name = "min_companies", required = false,
                   description = "最少覆盖公司数，默认 2")
        int minCompanies,
        @ToolParam(name = "limit", required = false,
                   description = "返回数量限制，默认 20")
        int limit,
        ToolExecutionContext context
    ) {
        return "知识图谱服务暂未开放。跨公司考点趋势功能将在 Neo4j 集成后提供。";
    }
}
