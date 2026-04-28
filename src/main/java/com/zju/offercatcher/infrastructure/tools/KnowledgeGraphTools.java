package com.zju.offercatcher.infrastructure.tools;

import com.zju.offercatcher.infrastructure.adapters.cache.CacheAdapter;
import com.zju.offercatcher.infrastructure.persistence.neo4j.Neo4jClient;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识图谱工具
 *
 * 对应 Python:
 *   app/infrastructure/tools/get_knowledge_relations.py
 *   app/infrastructure/tools/get_company_hot_topics.py
 *   app/infrastructure/tools/get_cross_company_trends.py
 */
@Component
public class KnowledgeGraphTools {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeGraphTools.class);
    private static final int KG_CACHE_TTL = 600;

    private final Neo4jClient neo4jClient;
    private final CacheAdapter cacheAdapter;

    public KnowledgeGraphTools(Neo4jClient neo4jClient, CacheAdapter cacheAdapter) {
        this.neo4jClient = neo4jClient;
        this.cacheAdapter = cacheAdapter;
    }

    @Tool(name = "get_knowledge_relations",
          description = "查询某个知识点的关联知识点。分析知识点之间的共现关系，帮助用户了解学习路径。")
    public String getKnowledgeRelations(
        @ToolParam(name = "entity", required = true,
                   description = "知识点实体名称")
        String entity,
        @ToolParam(name = "limit", required = false,
                   description = "返回数量限制，默认 5")
        int limit,
        ToolExecutionContext context
    ) {
        if (limit <= 0) limit = 5;
        final int finalLimit = limit;
        String cacheKey = "kg:relations:" + entity;

        return cacheAdapter.getWithLock(cacheKey, () -> {
            try {
                List<Map<String, Object>> related = neo4jClient.getRelatedEntities(entity, finalLimit);

                if (related.isEmpty()) {
                    return "暂无 '" + entity + "' 的关联知识点数据。\n可能该知识点尚未被录入，或 Neo4j 数据未初始化。";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("**与 '").append(entity).append("' 常一起考察的知识点:**\n\n");
                for (Map<String, Object> e : related) {
                    String relatedEntity = (String) e.get("related_entity");
                    Object count = e.get("co_occurrence_count");
                    sb.append("- **").append(relatedEntity)
                      .append("** (共现次数: ").append(count).append(")\n");
                }
                sb.append("\n建议在学习 '").append(entity).append("' 后，继续深入以上关联知识点。");
                return sb.toString();
            } catch (Exception e) {
                log.error("get_knowledge_relations failed: {}", e.getMessage());
                return "查询失败: " + e.getMessage();
            }
        }, KG_CACHE_TTL);
    }

    @Tool(name = "get_company_hot_topics",
          description = "查询某公司的高频考点。用于分析某公司的面试重点，帮助用户针对性准备。")
    public String getCompanyHotTopics(
        @ToolParam(name = "company", required = true,
                   description = "公司名称")
        String company,
        @ToolParam(name = "limit", required = false,
                   description = "返回数量限制，默认 10")
        int limit,
        ToolExecutionContext context
    ) {
        if (limit <= 0) limit = 10;
        final int finalLimit = limit;
        String cacheKey = "kg:company_topics:" + company;

        return cacheAdapter.getWithLock(cacheKey, () -> {
            try {
                List<Map<String, Object>> topEntities = neo4jClient.getTopEntities(company, finalLimit);

                if (topEntities.isEmpty()) {
                    return "暂无 " + company + " 的考点数据。可能该公司尚未录入题库，或 Neo4j 数据未初始化。";
                }

                StringBuilder sb = new StringBuilder();
                sb.append("**").append(company).append(" 高频考点 Top ").append(topEntities.size()).append(":**\n\n");
                int i = 1;
                for (Map<String, Object> e : topEntities) {
                    String entity = (String) e.get("entity");
                    Object count = e.get("count");
                    sb.append(i++).append(". **").append(entity)
                      .append("** (考察次数: ").append(count).append(")\n");
                }
                return sb.toString();
            } catch (Exception e) {
                log.error("get_company_hot_topics failed: {}", e.getMessage());
                return "查询失败: " + e.getMessage();
            }
        }, KG_CACHE_TTL);
    }

    @Tool(name = "get_cross_company_trends",
          description = "查询跨多家公司考察的热门考点。分析行业高频考点，帮助用户了解行业趋势和重点。")
    public String getCrossCompanyTrends(
        @ToolParam(name = "min_companies", required = false,
                   description = "最少覆盖公司数，默认 2")
        int minCompanies,
        @ToolParam(name = "limit", required = false,
                   description = "返回数量限制，默认 20")
        int limit,
        ToolExecutionContext context
    ) {
        if (minCompanies <= 0) minCompanies = 2;
        if (limit <= 0) limit = 20;
        final int finalMinCompanies = minCompanies;
        final int finalLimit = limit;
        String cacheKey = "kg:cross_company_trends:" + minCompanies;

        return cacheAdapter.getWithLock(cacheKey, () -> {
            try {
                List<Map<String, Object>> crossEntities =
                    neo4jClient.getCrossCompanyEntities(finalMinCompanies);

                if (crossEntities.isEmpty()) {
                    return "暂无跨 " + finalMinCompanies + "+ 家公司的考点数据。\n可能题库数据不足，或 Neo4j 数据未初始化。";
                }

                // Sort by total_count desc and limit
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> sorted = crossEntities.stream()
                    .sorted((a, b) -> {
                        long ca = ((Number) a.getOrDefault("total_count", 0L)).longValue();
                        long cb = ((Number) b.getOrDefault("total_count", 0L)).longValue();
                        return Long.compare(cb, ca);
                    })
                    .limit(finalLimit)
                    .toList();

                StringBuilder sb = new StringBuilder();
                sb.append("**跨 ").append(finalMinCompanies).append("+ 家公司的热门考点:**\n\n");
                for (Map<String, Object> e : sorted) {
                    String entity = (String) e.get("entity");
                    @SuppressWarnings("unchecked")
                    List<String> companies = (List<String>) e.get("companies");
                    Object totalCount = e.get("total_count");

                    String companiesStr;
                    if (companies.size() <= 5) {
                        companiesStr = String.join(", ", companies);
                    } else {
                        companiesStr = String.join(", ", companies.subList(0, 5))
                            + " 等" + companies.size() + "家";
                    }

                    sb.append("- **").append(entity).append("**: ")
                      .append(companiesStr).append(" (共 ").append(totalCount).append(" 次)\n");
                }
                sb.append("\n以上考点是行业高频热点，建议优先准备。");
                return sb.toString();
            } catch (Exception e) {
                log.error("get_cross_company_trends failed: {}", e.getMessage());
                return "查询失败: " + e.getMessage();
            }
        }, KG_CACHE_TTL);
    }
}
