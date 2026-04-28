package com.zju.offercatcher.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaRepository;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 岗位归一化应用服务
 *
 * 实现岗位名称的完整生命周期管理：聚合 → LLM 归一化 → 迁移。
 * 对应 Python: app/application/services/position_normalization_service.py
 */
@Service
public class PositionNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(PositionNormalizationService.class);
    private static final Path MAPPINGS_FILE = Path.of("config", "position_mappings.json");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String NORMALIZATION_PROMPT = """
        你是一个岗位名称归一化专家。请将以下岗位名称归一化为最少数量的标准类别。

        岗位列表（按出现次数排序）：
        {position_list}

        **预定义的标准岗位类别**（优先归入这些类别）：
        - AI Agent开发：所有涉及 Agent、智能体、Agent应用开发的岗位
        - AI开发：涉及 AI 应用开发、AI工程师、但不明确涉及 Agent 的岗位
        - 后端开发：传统后端开发岗位（Java/Python/Go 等不涉及 AI）
        - 大模型开发：涉及大模型、LLM 应用开发的岗位
        - 大模型算法：涉及算法、算法工程师的岗位
        - AI测试开发：涉及 AI 测试、评测的岗位
        - 前端开发：前端相关岗位

        **归一化原则**：
        1. 大类优先：尽可能归入预定义类别，而非创建新类别
        2. 语义合并：语义相近的岗位必须合并
        3. 去除修饰：去掉"工程师"、"岗"、"应用"等冗余修饰词
        4. 统一命名：同一大类使用统一名称，不要保留变体

        请返回 JSON 格式：{"mappings": {"原始岗位名": "标准岗位名", ...}}""";

    private final QuestionJpaRepository questionJpaRepo;
    private final OpenAIChatModel llm;
    private Map<String, String> mappings = new LinkedHashMap<>();

    public PositionNormalizationService(QuestionJpaRepository questionJpaRepo,
                                         LLMProperties llmProperties) {
        this.questionJpaRepo = questionJpaRepo;
        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.llm = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(false)
            .build();
        loadMappings();
    }

    // ==================== Public API ====================

    public Map<String, Integer> aggregate() {
        log.info("Aggregating positions from PostgreSQL...");
        List<QuestionJpaEntity> all = questionJpaRepo.findAll();

        Map<String, Integer> positionCounts = new LinkedHashMap<>();
        for (QuestionJpaEntity q : all) {
            String position = q.getPosition();
            if (position != null && !position.isBlank()) {
                positionCounts.merge(position, 1, Integer::sum);
            }
        }

        // Sort by count descending
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(positionCounts.entrySet());
        sorted.sort(Map.Entry.<String, Integer>comparingByValue().reversed());

        Map<String, Integer> result = new LinkedHashMap<>();
        for (var entry : sorted) {
            result.put(entry.getKey(), entry.getValue());
        }

        log.info("Aggregated {} unique positions from {} records", result.size(), all.size());
        return result;
    }

    public Map<String, String> normalizeBatch(Map<String, Integer> positions) {
        if (positions.isEmpty()) {
            log.warn("No positions to normalize");
            return Map.of();
        }

        // Skip already-mapped positions
        List<String> unmapped = positions.keySet().stream()
            .filter(p -> !mappings.containsKey(p))
            .toList();

        if (unmapped.isEmpty()) {
            log.info("All {} positions already mapped", positions.size());
            return Map.of();
        }

        String positionList = unmapped.stream()
            .limit(50)
            .collect(Collectors.joining("、"));

        String prompt = NORMALIZATION_PROMPT.replace("{position_list}", positionList);

        log.info("Normalizing {} unmapped positions with LLM...", unmapped.size());

        try {
            ReActAgent agent = ReActAgent.builder()
                .name("position-normalizer")
                .model(llm)
                .maxIters(0)
                .generateOptions(GenerateOptions.builder().temperature(0.3).maxTokens(2000).build())
                .build();

            Msg response = agent.call(List.of(
                Msg.builder().role(MsgRole.USER).textContent(prompt).build()
            )).block();

            String content = response != null ? response.getTextContent() : "{}";
            Map<String, Map<String, String>> result = parseJsonResponse(content);
            Map<String, String> newMappings = result.getOrDefault("mappings", Map.of());

            mappings.putAll(newMappings);
            saveMappings();

            log.info("Generated {} new mappings", newMappings.size());
            return newMappings;
        } catch (Exception e) {
            log.error("Position normalization failed: {}", e.getMessage(), e);
            return Map.of();
        }
    }

    @Transactional
    public Map<String, Integer> migrate(Map<String, String> mappingsToApply) {
        if (mappingsToApply == null || mappingsToApply.isEmpty()) {
            log.warn("No mappings to migrate");
            return Map.of();
        }

        log.info("Migrating positions in PostgreSQL...");
        Map<String, Integer> stats = new LinkedHashMap<>();

        List<QuestionJpaEntity> all = questionJpaRepo.findAll();
        int updated = 0;

        for (QuestionJpaEntity q : all) {
            String original = q.getPosition();
            if (original == null || original.isBlank()) continue;

            String normalized = mappingsToApply.get(original);
            if (normalized == null || normalized.equals(original)) continue;

            q.setPosition(normalized);
            stats.merge(original, 1, Integer::sum);
            updated++;
        }

        if (updated > 0) {
            questionJpaRepo.saveAll(all);
        }

        log.info("Migration completed: {} records updated for {} original positions", updated, stats.size());
        return stats;
    }

    public String getNormalized(String position) {
        return mappings.getOrDefault(position, position);
    }

    public Map<String, Integer> runPipeline() {
        log.info("Starting full position normalization pipeline...");

        Map<String, Integer> positions = aggregate();
        Map<String, String> newMappings = normalizeBatch(positions);
        Map<String, Integer> stats = migrate(newMappings);

        log.info("Position normalization pipeline completed");
        return stats;
    }

    // ==================== Persistence ====================

    private void loadMappings() {
        try {
            if (Files.exists(MAPPINGS_FILE)) {
                String json = Files.readString(MAPPINGS_FILE);
                mappings = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
                log.info("Loaded {} position mappings", mappings.size());
            }
        } catch (IOException e) {
            log.warn("Failed to load position mappings: {}", e.getMessage());
        }
    }

    private void saveMappings() {
        try {
            Files.createDirectories(MAPPINGS_FILE.getParent());
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappings);
            Files.writeString(MAPPINGS_FILE, json);
            log.info("Saved {} mappings to {}", mappings.size(), MAPPINGS_FILE);
        } catch (IOException e) {
            log.error("Failed to save position mappings: {}", e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> parseJsonResponse(String content) {
        try {
            int jsonStart = content.indexOf("{");
            int jsonEnd = content.lastIndexOf("}") + 1;
            if (jsonStart == -1 || jsonEnd == 0) return Map.of();
            String jsonStr = content.substring(jsonStart, jsonEnd);
            return objectMapper.readValue(jsonStr, Map.class);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", e.getMessage());
            return Map.of();
        }
    }
}
