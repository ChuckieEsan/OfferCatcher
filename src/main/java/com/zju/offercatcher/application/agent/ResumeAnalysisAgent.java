package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.application.agent.dto.ResumeAnalysisOutput;
import com.zju.offercatcher.infrastructure.common.StructuredOutputUtil;
import com.zju.offercatcher.infrastructure.config.LLMModelFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 简历分析 Agent。
 *
 * 从简历纯文本中提取项目经历、技术栈等结构化信息。
 * 单次 LLM 调用（maxIters=0）。
 */
@Component
public class ResumeAnalysisAgent {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalysisAgent.class);
    private static final ResumeAnalysisOutput DEFAULT_OUTPUT = new ResumeAnalysisOutput(
        List.of(), List.of(), null, null);

    private final OpenAIChatModel model;
    private final GenerateOptions options;

    public ResumeAnalysisAgent(LLMModelFactory modelFactory) {
        this.model = modelFactory.createSimple("deepseek", false);
        this.options = GenerateOptions.builder()
            .temperature(0.1)
            .maxTokens(2048)
            .build();
    }

    /**
     * 分析简历文本，提取结构化信息。
     */
    public ResumeAnalysisOutput analyze(String resumeText) {
        log.info("Analyzing resume: {} chars", resumeText.length());

        String systemPrompt = """
            你是资深技术招聘专家。从候选人简历中提取结构化信息。
            项目经历写 2-5 个最相关的，techStack 列出所有提到的技术。
            """;

        String userPrompt = "请分析以下简历：\n\n" + resumeText;

        ResumeAnalysisOutput result = StructuredOutputUtil.callWithFallback(
            model, "resume-analysis", systemPrompt, options,
            List.of(Msg.builder().role(MsgRole.USER).textContent(userPrompt).build()),
            ResumeAnalysisOutput.class, DEFAULT_OUTPUT, log
        );

        log.info("Resume analysis done: {} projects, {} techs",
            result.projects().size(), result.techStack().size());
        return result;
    }
}
