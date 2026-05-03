package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
import com.zju.offercatcher.infrastructure.config.LLMModelFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * JD 技能关键词展开 Agent。
 *
 * 用 LLM 将 JD 的技能要求展开为多个可搜索关键词，
 * 弥补 JD skill name 与题库 coreEntities 之间的语义鸿沟。
 *
 * 单次 LLM 调用（maxIters=0），不在热路径上。
 */
@Component
public class SkillKeywordExpanderAgent {

    private static final Logger log = LoggerFactory.getLogger(SkillKeywordExpanderAgent.class);

    private final OpenAIChatModel model;

    public SkillKeywordExpanderAgent(LLMModelFactory modelFactory) {
        this.model = modelFactory.createSimple("deepseek", false);
    }

    /**
     * 展开一个 JD 技能为多个可搜索关键词。
     */
    public List<String> expand(SkillRequirement skill) {
        try {
            String prompt = buildPrompt(skill);

            ReActAgent agent = ReActAgent.builder()
                .name("skill-expander")
                .sysPrompt("你是资深技术面试出题专家。只输出关键词列表。")
                .model(model)
                .maxIters(0)
                .generateOptions(GenerateOptions.builder()
                    .temperature(0.1)
                    .maxTokens(256)
                    .build())
                .build();

            Msg result = agent.call(List.of(
                Msg.builder().role(MsgRole.USER).textContent(prompt).build()
            )).block();

            List<String> keywords = parseKeywords(result != null ? result.getTextContent() : null);
            log.debug("Skill '{}' expanded to {} keywords: {}", skill.name(), keywords.size(), keywords);
            return keywords;

        } catch (Exception e) {
            log.warn("Keyword expansion failed for '{}': {}", skill.name(), e.getMessage());
            return List.of(skill.name());
        }
    }

    private String buildPrompt(SkillRequirement skill) {
        return """
            你是资深技术面试出题专家。根据 JD 技能要求，生成用于检索面试题目的关键词列表。

            JD 技能:
            - 技能名称: %s
            - 要求等级: %s
            - JD 原文: %s

            请展开为 8-15 个可搜索的关键词或短语，覆盖：
            1. 该技能的同义表述和变体
            2. 该技能涉及的核心技术和框架
            3. 面试中考察该技能时常见的考点方向
            4. 相关的设计模式、架构概念

            输出要求：一行一个关键词，不要序号，不要解释。只输出关键词列表。
            """.formatted(skill.name(), skill.level(),
                skill.evidence() != null ? skill.evidence() : "无");
    }

    private List<String> parseKeywords(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split("[\\n,，、；;]+"))
            .map(String::strip)
            .filter(s -> !s.isBlank() && !s.startsWith("#") && !s.startsWith("//"))
            .filter(s -> s.length() >= 2)
            .limit(15)
            .toList();
    }
}
