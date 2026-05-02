package com.zju.offercatcher.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.agent.dto.ScoreResult;
import com.zju.offercatcher.infrastructure.common.PromptLoader;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 答题评分 Agent
 *
 * 对用户提交的答案进行评分，生成反馈，更新熟练度等级。
 * 对应 Python: app/application/agents/scorer/agent.py
 */
@Service
public class ScorerAgent {

    private static final Logger log = LoggerFactory.getLogger(ScorerAgent.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OpenAIChatModel llm;
    private final QuestionRepository questionRepository;
    private final PromptLoader promptLoader;

    public ScorerAgent(LLMProperties llmProperties,
                        QuestionRepository questionRepository,
                        PromptLoader promptLoader) {
        this.questionRepository = questionRepository;
        this.promptLoader = promptLoader;
        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.llm = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(false)
            .build();
    }

    public ScoreResult score(Long id, String userAnswer) {
        Question question = questionRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Question not found: " + id));

        log.info("Scoring answer for question: {}", id);

        String prompt = promptLoader.render("scorer.md",
            "question_text", question.getQuestionText(),
            "standard_answer", question.getAnswer() != null ? question.getAnswer() : "暂无标准答案",
            "user_answer", userAnswer,
            "current_level", question.getMasteryLevel().name(),
            "company", question.getCompany(),
            "position", question.getPosition()
        );

        ReActAgent agent = ReActAgent.builder()
            .name("scorer")
            .model(llm)
            .maxIters(0)
            .generateOptions(GenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(1024)
                .build())
            .build();

        try {
            Msg response = agent.call(List.of(
                Msg.builder().role(MsgRole.USER).textContent(prompt).build()
            )).block();

            String content = response != null ? response.getTextContent() : "";
            ScoreResult result = parseResponse(content, id, question.getQuestionText(),
                question.getAnswer(), userAnswer);

            MasteryLevel currentLevel = question.getMasteryLevel();
            MasteryLevel newLevel = calculateNewLevel(currentLevel, result.score());
            MasteryLevel llmLevel;
            try {
                llmLevel = MasteryLevel.valueOf(result.masteryLevel());
            } catch (IllegalArgumentException e) {
                llmLevel = MasteryLevel.LEVEL_0;
            }

            MasteryLevel finalLevel = newLevel != llmLevel ? newLevel : llmLevel;

            if (finalLevel != currentLevel) {
                question.updateMastery(finalLevel);
                questionRepository.save(question);
                log.info("Updated mastery: {} -> {} for question {}", currentLevel, finalLevel, id);
            }

            log.info("Scoring completed: score={}, level={}", result.score(), finalLevel);
            return new ScoreResult(id, question.getQuestionText(),
                question.getAnswer(), userAnswer,
                result.score(), finalLevel.name(),
                result.strengths(), result.improvements(), result.feedback());
        } catch (Exception e) {
            log.error("Scoring failed: {}", e.getMessage(), e);
            throw new RuntimeException("评分失败: " + e.getMessage(), e);
        }
    }

    private ScoreResult parseResponse(String content, Long id, String questionText,
                                       String standardAnswer, String userAnswer) {
        try {
            int jsonStart = content.indexOf("{");
            int jsonEnd = content.lastIndexOf("}") + 1;
            if (jsonStart == -1 || jsonEnd == 0) {
                throw new RuntimeException("No JSON found in response");
            }

            String jsonStr = content.substring(jsonStart, jsonEnd);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);

            Object scoreObj = data.get("score");
            int score = scoreObj instanceof Number n ? n.intValue() : 0;
            String masteryLevel = (String) data.getOrDefault("mastery_level", "LEVEL_0");

            @SuppressWarnings("unchecked")
            List<String> strengths = (List<String>) data.getOrDefault("strengths", List.of());
            @SuppressWarnings("unchecked")
            List<String> improvements = (List<String>) data.getOrDefault("improvements", List.of());
            String feedback = (String) data.getOrDefault("feedback", "");

            return new ScoreResult(id, questionText, standardAnswer, userAnswer,
                score, masteryLevel, strengths, improvements, feedback);
        } catch (Exception e) {
            log.error("Failed to parse scorer response: {}", e.getMessage());
            return new ScoreResult(id, questionText, standardAnswer, userAnswer,
                0, "LEVEL_0", List.of(), List.of(), "评分失败: " + e.getMessage());
        }
    }

    static MasteryLevel calculateNewLevel(MasteryLevel currentLevel, int score) {
        if (currentLevel.getLevel() == 0 && score >= 60) {
            return MasteryLevel.LEVEL_1;
        }
        if (currentLevel.getLevel() == 1 && score >= 85) {
            return MasteryLevel.LEVEL_2;
        }
        return currentLevel;
    }
}
