package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.infrastructure.adapters.websearch.TavilySearchAdapter;
import com.zju.offercatcher.infrastructure.common.PromptLoader;
import com.zju.offercatcher.infrastructure.config.LLMModelFactory;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 答案生成 Agent
 * <p>
 * 使用 Web Search 获取上下文，调用 LLM 生成标准答案。
 * 对应 Python: app/application/agents/answer_specialist/agent.py
 */
@Service
public class AnswerSpecialistAgent {

    private static final Logger log = LoggerFactory.getLogger(AnswerSpecialistAgent.class);

    private final OpenAIChatModel llm;
    private final TavilySearchAdapter webSearchAdapter;
    private final PromptLoader promptLoader;

    public AnswerSpecialistAgent(LLMModelFactory modelFactory,
                                 TavilySearchAdapter webSearchAdapter,
                                 PromptLoader promptLoader) {
        this.webSearchAdapter = webSearchAdapter;
        this.promptLoader = promptLoader;
        this.llm = modelFactory.createSimple("deepseek", false);
    }

    public String generateAnswer(Question question) {
        log.info("Generating answer for: {}",
                question.getQuestionText().substring(0, Math.min(50, question.getQuestionText().length())));

        String context = fetchContext(question.getQuestionText(), question.getCompany(), question.getPosition());
        String coreEntities = question.getCoreEntities().isEmpty()
                ? "无" : String.join(", ", question.getCoreEntities());

        return doGenerate(question.getQuestionText(), question.getCompany(), question.getPosition(),
                coreEntities, context);
    }

    private String fetchContext(String questionText, String company, String position) {
        try {
            String c = webSearchAdapter.searchForContext(questionText, company, position);
            log.info("Web search completed, context length: {}", c.length());
            return c;
        } catch (Exception e) {
            log.warn("Web search failed: {}, using empty context", e.getMessage());
            return "搜索失败，基于知识生成答案。";
        }
    }

    private String doGenerate(String questionText, String company, String position,
                              String coreEntities, String context) {
        String prompt = promptLoader.render("answer_specialist.md",
                "company", company,
                "position", position,
                "question", questionText,
                "core_entities", coreEntities,
                "context", context
        );

        ReActAgent agent = ReActAgent.builder()
                .name("answer-specialist")
                .model(llm)
                .maxIters(0)
                .generateOptions(GenerateOptions.builder()
                        .temperature(0.3)
                        .maxTokens(2048)
                        .build())
                .build();

        try {
            Msg response = agent.call(List.of(
                    Msg.builder().role(MsgRole.USER).textContent(prompt).build()
            )).block();

            String answer = response != null ? response.getTextContent() : "";
            log.info("Answer generated, length: {}", answer.length());
            return answer;
        } catch (Exception e) {
            log.error("LLM generation failed: {}", e.getMessage(), e);
            throw new RuntimeException("答案生成失败: " + e.getMessage(), e);
        }
    }
}
