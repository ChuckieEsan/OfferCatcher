package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.application.agent.dto.ScoreResult;
import com.zju.offercatcher.application.agent.dto.ScorerOutput;
import com.zju.offercatcher.infrastructure.common.PromptLoader;
import com.zju.offercatcher.infrastructure.common.StructuredOutputUtil;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
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

    private static final ScorerOutput DEFAULT_OUTPUT = ScorerOutput.DEFAULT;

    private static final GenerateOptions OPTIONS = GenerateOptions.builder()
        .temperature(0.1)
        .maxTokens(1024)
        .build();

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

        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt).build();

        ScorerOutput output = StructuredOutputUtil.callWithFallback(
            llm, "scorer", null, OPTIONS,
            List.of(userMsg), ScorerOutput.class, DEFAULT_OUTPUT, log);

        MasteryLevel currentLevel = question.getMasteryLevel();
        MasteryLevel newLevel = calculateNewLevel(currentLevel, output.score());
        MasteryLevel llmLevel;
        try {
            llmLevel = MasteryLevel.valueOf(output.masteryLevel());
        } catch (IllegalArgumentException e) {
            llmLevel = MasteryLevel.LEVEL_0;
        }

        MasteryLevel finalLevel = newLevel != llmLevel ? newLevel : llmLevel;

        if (finalLevel != currentLevel) {
            question.updateMastery(finalLevel);
            questionRepository.save(question);
            log.info("Updated mastery: {} -> {} for question {}", currentLevel, finalLevel, id);
        }

        log.info("Scoring completed: score={}, level={}", output.score(), finalLevel);
        return new ScoreResult(id, question.getQuestionText(),
            question.getAnswer(), userAnswer,
            output.score(), finalLevel.name(),
            output.strengths(), output.improvements(), output.feedback());
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
