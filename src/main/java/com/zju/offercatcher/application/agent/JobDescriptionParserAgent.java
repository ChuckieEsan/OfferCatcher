package com.zju.offercatcher.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.infrastructure.common.PromptLoader;
import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.repositories.JobDescriptionRepository;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JD 解析服务
 *
 * 使用 LLM 解析职位描述，提取结构化技能要求。
 * 单次 LLM 调用（maxIters=0），不在热路径上（创建面试时调用一次）。
 */
@Service
public class JobDescriptionParserAgent {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionParserAgent.class);
    private static final int MIN_JD_LENGTH = 30;

    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final OpenAIChatModel model;
    private final JobDescriptionRepository jdRepository;

    public JobDescriptionParserAgent(PromptLoader promptLoader,
                                       ObjectMapper objectMapper,
                                       LLMProperties llmProperties,
                                       JobDescriptionRepository jdRepository) {
        this.promptLoader = promptLoader;
        this.objectMapper = objectMapper;
        this.jdRepository = jdRepository;

        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.model = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(false)
            .build();
    }

    /**
     * 解析 JD 文本并持久化。
     *
     * @param userId  用户 ID
     * @param rawText 原始 JD 文本
     * @return 已解析并持久化的 JobDescription
     */
    @Transactional
    public JobDescription parseAndSave(String userId, String rawText) {
        if (rawText == null || rawText.strip().length() < MIN_JD_LENGTH) {
            throw new IllegalArgumentException("JD 文本太短（至少" + MIN_JD_LENGTH + "字）");
        }

        log.info("Parsing JD for user {}, length={}", userId, rawText.length());

        // 1. 创建 JD 实体
        JobDescription jd = JobDescription.create(userId, rawText);

        // 2. LLM 解析
        String prompt = promptLoader.render("jd_parser.md", "jd_text", rawText);

        ReActAgent agent = ReActAgent.builder()
            .name("jd-parser")
            .sysPrompt("你是职位描述解析专家，只输出 JSON。")
            .model(model)
            .maxIters(0)
            .generateOptions(GenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(2048)
                .build())
            .build();

        try {
            Msg result = agent.call(List.of(
                Msg.builder().role(MsgRole.USER).textContent(prompt).build()
            )).block();

            String json = extractJson(result.getTextContent());
            parseAndUpdate(jd, json);

            // 3. 持久化
            jdRepository.save(jd);

            log.info("JD parsed successfully: id={}, company={}, position={}, requiredSkills={}",
                jd.getId(), jd.getCompany(), jd.getPosition(), jd.getRequiredSkills().size());
            return jd;

        } catch (Exception e) {
            log.error("JD parsing failed: {}", e.getMessage(), e);
            throw new RuntimeException("JD 解析失败：" + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private void parseAndUpdate(JobDescription jd, String json) throws JsonProcessingException {
        Map<String, Object> map = objectMapper.readValue(json, Map.class);

        String company = (String) map.get("company");
        String position = (String) map.get("position");
        String experienceReq = (String) map.get("experienceRequirement");

        List<SkillRequirement> required = parseSkills((List<Map<String, String>>) map.get("requiredSkills"));
        List<SkillRequirement> preferred = parseSkills((List<Map<String, String>>) map.get("preferredSkills"));
        List<String> soft = (List<String>) map.getOrDefault("softSkills", List.of());

        jd.updateParsedResult(required, preferred, soft, company, position, experienceReq);
    }

    private List<SkillRequirement> parseSkills(List<Map<String, String>> raw) {
        if (raw == null) return List.of();
        List<SkillRequirement> skills = new ArrayList<>();
        for (Map<String, String> item : raw) {
            String name = item.get("name");
            if (name == null || name.isBlank()) continue;
            skills.add(new SkillRequirement(
                name,
                item.getOrDefault("level", "familiar"),
                item.getOrDefault("evidence", "")
            ));
        }
        return skills;
    }

    private String extractJson(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }
}
