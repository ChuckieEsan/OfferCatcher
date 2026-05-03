package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.application.agent.dto.JobDescriptionParserOutput;
import com.zju.offercatcher.infrastructure.common.PromptLoader;
import com.zju.offercatcher.infrastructure.common.StructuredOutputUtil;
import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.repositories.JobDescriptionRepository;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
import com.zju.offercatcher.infrastructure.config.LLMModelFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * JD 解析 Agent
 *
 * 使用 LLM 解析职位描述，提取结构化技能要求。
 * 单次 LLM 调用（maxIters=0），不在热路径上（创建面试时调用一次）。
 */
@Service
public class JobDescriptionParserAgent {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionParserAgent.class);
    private static final int MIN_JD_LENGTH = 30;

    private static final JobDescriptionParserOutput DEFAULT_OUTPUT = JobDescriptionParserOutput.DEFAULT;

    private static final GenerateOptions OPTIONS = GenerateOptions.builder()
        .temperature(0.1)
        .maxTokens(2048)
        .build();

    private final PromptLoader promptLoader;
    private final OpenAIChatModel model;
    private final JobDescriptionRepository jdRepository;

    public JobDescriptionParserAgent(PromptLoader promptLoader,
                                     LLMModelFactory modelFactory,
                                     JobDescriptionRepository jdRepository) {
        this.promptLoader = promptLoader;
        this.jdRepository = jdRepository;
        this.model = modelFactory.createSimple("deepseek", false);
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

        JobDescription jd = JobDescription.create(userId, rawText);

        String prompt = promptLoader.render("jd_parser.md", "jd_text", rawText);
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt).build();

        JobDescriptionParserOutput output = StructuredOutputUtil.callWithFallback(
            model, "jd-parser", "你是职位描述解析专家，只输出 JSON。", OPTIONS,
            List.of(userMsg), JobDescriptionParserOutput.class, DEFAULT_OUTPUT, log);

        List<SkillRequirement> required = toSkillRequirements(output.requiredSkills());
        List<SkillRequirement> preferred = toSkillRequirements(output.preferredSkills());
        List<String> soft = output.softSkills() != null ? output.softSkills() : List.of();

        jd.updateParsedResult(required, preferred, soft,
            output.company(), output.position(), output.experienceRequirement());

        jdRepository.save(jd);

        log.info("JD parsed successfully: id={}, company={}, position={}, requiredSkills={}",
            jd.getId(), jd.getCompany(), jd.getPosition(), jd.getRequiredSkills().size());
        return jd;
    }

    private static List<SkillRequirement> toSkillRequirements(List<JobDescriptionParserOutput.SkillItem> items) {
        if (items == null) return List.of();
        return items.stream()
            .filter(i -> i.name() != null && !i.name().isBlank())
            .map(i -> new SkillRequirement(
                i.name(),
                i.level() != null ? i.level() : "familiar",
                i.evidence() != null ? i.evidence() : ""))
            .toList();
    }
}
