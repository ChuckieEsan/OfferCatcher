package com.zju.offercatcher.application.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.agent.dto.PositionMappingOutput;
import com.zju.offercatcher.infrastructure.common.StructuredOutputUtil;
import com.zju.offercatcher.infrastructure.config.LLMModelFactory;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaEntity;
import com.zju.offercatcher.infrastructure.persistence.postgres.QuestionJpaRepository;
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
 * 岗位归一化 Agent
 *
 * 实现岗位名称的完整生命周期管理：聚合 → LLM 归一化 → 迁移。
 * 对应 Python: app/application/services/position_normalization_service.py
 */
@Service
public class PositionNormalizationAgent {

    private static final Logger log = LoggerFactory.getLogger(PositionNormalizationAgent.class);
    private static final Path MAPPINGS_FILE = Path.of("config", "position_mappings.json");

    private static final PositionMappingOutput DEFAULT_OUTPUT = PositionMappingOutput.DEFAULT;

    private static final GenerateOptions OPTIONS = GenerateOptions.builder()
        .temperature(0.3)
        .maxTokens(2000)
        .build();

    private static final String NORMALIZATION_PROMPT = """
        你是一个岗位名称归一化专家。请将以下岗位名称归一化为最少数量的标准类别。

        岗位列表（按出现次数排序）：
        {position_list}

        **预定义的标准岗位类别**（优先归入这些类别）：
        - AI应用研发：所有 AI 应用层开发岗位，包括 AI 开发、AI Agent/智能体开发、AI 应用开发、AI 工程师等。这是最常见的 AI 岗位大类，只要涉及 AI + 业务应用的都应归入此类
        - 后端开发：传统后端开发岗位（Java/Python/Go 等不涉及 AI）
        - 大模型开发：涉及大模型训练、推理、LLM 底层框架开发的岗位
        - 大模型算法：涉及算法研究、模型优化、算法工程师的岗位
        - AI测试开发：涉及 AI 测试、评测、质量保障的岗位
        - 前端开发：前端相关岗位

        **归一化原则**：
        1. 大类优先：尽可能归入预定义类别，而非创建新类别
        2. 语义合并：语义相近的岗位必须合并。AI开发、AI Agent开发、AI应用开发等统一归入 AI应用研发
        3. 去除修饰：去掉"工程师"、"岗"、"应用"等冗余修饰词
        4. 统一命名：同一大类使用统一名称，不要保留变体（如不要同时存在 "AI开发" 和 "AI应用研发"）

        请返回 JSON 格式：{"mappings": {"原始岗位名": "标准岗位名", ...}}""";

    private final QuestionJpaRepository questionJpaRepo;
    private final QuestionRepository questionRepository;
    private final OpenAIChatModel llm;
    private Map<String, String> mappings = new LinkedHashMap<>();

    public PositionNormalizationAgent(QuestionJpaRepository questionJpaRepo,
                                         QuestionRepository questionRepository,
                                         LLMModelFactory modelFactory) {
        this.questionJpaRepo = questionJpaRepo;
        this.questionRepository = questionRepository;
        this.llm = modelFactory.createSimple("deepseek", false);
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

        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt).build();

        PositionMappingOutput output = StructuredOutputUtil.callWithFallback(
            llm, "position-normalizer", null, OPTIONS,
            List.of(userMsg), PositionMappingOutput.class, DEFAULT_OUTPUT, log);

        Map<String, String> newMappings = output.mappings();
        if (newMappings == null) newMappings = Map.of();

        mappings.putAll(newMappings);
        saveMappings();

        log.info("Generated {} new mappings", newMappings.size());
        return newMappings;
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
        List<Question> updatedQuestions = new ArrayList<>();

        for (QuestionJpaEntity q : all) {
            String original = q.getPosition();
            if (original == null || original.isBlank()) continue;

            String normalized = mappingsToApply.get(original);
            if (normalized == null || normalized.equals(original)) continue;

            q.setPosition(normalized);
            stats.merge(original, 1, Integer::sum);
            updatedQuestions.add(q.toDomain());
        }

        if (!updatedQuestions.isEmpty()) {
            questionJpaRepo.saveAll(all);
            // position 变更后 toContext() 输出变化，需重算 embedding 同步到 Qdrant
            questionRepository.resyncEmbeddings(updatedQuestions);
        }

        log.info("Migration completed: {} records updated for {} original positions", updatedQuestions.size(), stats.size());
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
                ObjectMapper mapper = new ObjectMapper();
                String json = Files.readString(MAPPINGS_FILE);
                mappings = mapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {});
                log.info("Loaded {} position mappings", mappings.size());
            }
        } catch (IOException e) {
            log.warn("Failed to load position mappings: {}", e.getMessage());
        }
    }

    private void saveMappings() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            Files.createDirectories(MAPPINGS_FILE.getParent());
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mappings);
            Files.writeString(MAPPINGS_FILE, json);
            log.info("Saved {} mappings to {}", mappings.size(), MAPPINGS_FILE);
        } catch (IOException e) {
            log.error("Failed to save position mappings: {}", e.getMessage());
        }
    }
}
