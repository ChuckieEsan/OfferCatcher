package com.zju.offercatcher.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.agent.dto.ScoreResult;
import com.zju.offercatcher.application.service.InterviewApplicationService;
import com.zju.offercatcher.application.service.QuestionApplicationService;
import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.question.valueobjects.QuestionWithScore;
import com.zju.offercatcher.domain.shared.enums.*;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import com.zju.offercatcher.infrastructure.config.InterviewProperties;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.*;

/**
 * 面试 Agent 服务
 *
 * 编排模拟面试流程：创建会话、出题、评分、追问、结束。
 * 对应 Python: app/application/agents/interview/agent.py
 */
@Service
public class InterviewAgentService {

    private static final Logger log = LoggerFactory.getLogger(InterviewAgentService.class);

    private static final Map<String, String> COMPANY_STYLES = Map.of(
        "字节跳动", "务实、注重细节和深度",
        "阿里巴巴", "注重价值观匹配和系统性思维",
        "腾讯", "温和但有深度，注重实际应用",
        "百度", "注重技术细节和底层原理",
        "美团", "务实、注重业务理解",
        "京东", "注重系统设计和稳定性",
        "快手", "注重创新和快速响应",
        "拼多多", "注重效率和成本意识",
        "小红书", "注重用户体验和创新"
    );

    private final InterviewApplicationService interviewService;
    private final QuestionApplicationService questionService;
    private final QuestionRepository questionRepository;
    private final OnnxEmbeddingAdapter embeddingAdapter;
    private final ScorerAgent scorerAgent;
    private final PromptLoader promptLoader;
    private final ObjectMapper objectMapper;
    private final OpenAIChatModel llm;
    private final int maxFollowUps;

    public InterviewAgentService(InterviewApplicationService interviewService,
                                  QuestionApplicationService questionService,
                                  QuestionRepository questionRepository,
                                  OnnxEmbeddingAdapter embeddingAdapter,
                                  ScorerAgent scorerAgent,
                                  PromptLoader promptLoader,
                                  ObjectMapper objectMapper,
                                  LLMProperties llmProperties,
                                  InterviewProperties interviewProperties) {
        this.interviewService = interviewService;
        this.questionService = questionService;
        this.questionRepository = questionRepository;
        this.embeddingAdapter = embeddingAdapter;
        this.scorerAgent = scorerAgent;
        this.promptLoader = promptLoader;
        this.objectMapper = objectMapper;
        this.maxFollowUps = interviewProperties.getMaxFollowUps();

        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.llm = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(true)
            .build();
    }

    public InterviewSession createSession(String userId, String company, String position,
                                           DifficultyLevel difficulty, int totalQuestions) {
        InterviewSession session = interviewService.createSession(
            userId, company, position, difficulty, totalQuestions);
        preloadQuestions(session);
        return session;
    }

    public Optional<InterviewSession> getSession(Long sessionId, String userId) {
        return interviewService.getSession(sessionId, userId);
    }

    /**
     * 流式处理用户回答
     */
    public Flux<String> processAnswerStream(Long sessionId, String userId, String answer) {
        InterviewSession session = interviewService.getSession(sessionId, userId).orElse(null);
        if (session == null) {
            return Flux.just(jsonError("Session not found"));
        }

        InterviewQuestion currentQuestion = session.getCurrentQuestion().orElse(null);
        if (currentQuestion == null) {
            return Flux.just(jsonError("No current question"));
        }

        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        try {
            ScoreResult result = scorerAgent.score(currentQuestion.getQuestionId(), answer);
            interviewService.answerQuestion(sessionId, userId, answer, result.score(), result.feedback());

            int score = result.score();
            boolean shouldContinue = score >= 70;

            sink.tryEmitNext(jsonScoreResult(result));

            if (shouldContinue) {
                if (session.isCompleted() || session.getCurrentQuestionIdx() >= session.getTotalQuestions() - 1) {
                    interviewService.completeSession(sessionId, userId);
                    sink.tryEmitNext(jsonCompleted(session));
                } else {
                    session.nextQuestion();
                    InterviewQuestion next = session.getCurrentQuestion().orElse(null);
                    sink.tryEmitNext(jsonNextQuestion(next, session.getCurrentQuestionIdx(), score));
                }
            } else {
                int followUpCount = currentQuestion.getFollowUps().size();
                if (followUpCount >= maxFollowUps) {
                    session.nextQuestion();
                    if (session.isCompleted()) {
                        interviewService.completeSession(sessionId, userId);
                        sink.tryEmitNext(jsonCompleted(session));
                    } else {
                        InterviewQuestion next = session.getCurrentQuestion().orElse(null);
                        sink.tryEmitNext(jsonForceNext(next, session.getCurrentQuestionIdx(), score));
                    }
                } else {
                    sink.tryEmitNext(jsonFollowUp(score, followUpCount + 1));
                }
            }
        } catch (Exception e) {
            log.error("Scoring failed: {}", e.getMessage(), e);
            sink.tryEmitNext(jsonError("Scoring failed: " + e.getMessage()));
        }

        sink.tryEmitComplete();
        return sink.asFlux();
    }

    /**
     * 流式获取提示
     */
    public Flux<String> getHintStream(Long sessionId, String userId) {
        InterviewSession session = interviewService.getSession(sessionId, userId).orElse(null);
        if (session == null) {
            return Flux.just(toSSEFrame(Map.of("type", "error", "message", "Session not found")));
        }

        InterviewQuestion currentQuestion = session.getCurrentQuestion().orElse(null);
        if (currentQuestion == null) {
            return Flux.just(toSSEFrame(Map.of("type", "error", "message", "No current question")));
        }

        String systemPrompt = getSystemPrompt(session);
        String userPrompt = promptLoader.render("interview_hint.md",
            "question_text", currentQuestion.getQuestionText());

        ReActAgent agent = ReActAgent.builder()
            .name("interview-hint")
            .sysPrompt(systemPrompt)
            .model(llm)
            .maxIters(0)
            .generateOptions(GenerateOptions.builder()
                .temperature(0.3)
                .maxTokens(512)
                .build())
            .build();

        StreamOptions streamOptions = StreamOptions.builder()
            .includeReasoningChunk(true)
            .includeSummaryChunk(true)
            .build();

        return agent.stream(
                List.of(Msg.builder().role(MsgRole.USER).textContent(userPrompt).build()),
                streamOptions)
            .filter(event -> {
                Msg msg = event.getMessage();
                if (msg == null) return false;
                // Filter events with actual content (ThinkingBlock or TextBlock)
                return !msg.getContentBlocks(ThinkingBlock.class).isEmpty()
                    || !msg.getContentBlocks(TextBlock.class).isEmpty()
                    || (msg.getTextContent() != null && !msg.getTextContent().isBlank());
            })
            .map(event -> {
                Msg msg = event.getMessage();
                if (msg == null) return toSSEFrame(Map.of("type", "text", "content", ""));

                // ThinkingBlock → reasoning type for frontend distinction
                if (!msg.getContentBlocks(ThinkingBlock.class).isEmpty()) {
                    String thinking = msg.getContentBlocks(ThinkingBlock.class).get(0).getThinking();
                    return toSSEFrame(Map.of("type", "reasoning", "content", thinking != null ? thinking : ""));
                }
                // TextBlock → text type (the actual answer)
                String text = msg.getTextContent();
                return toSSEFrame(Map.of("type", "text", "content", text != null ? text : ""));
            });
    }

    // ==================== Question Preloading ====================

    private void preloadQuestions(InterviewSession session) {
        String context = "公司：" + session.getCompany() + " | 岗位：" + session.getPosition() + " | 面试题";
        float[] queryVector = embeddingAdapter.embed(context);

        Map<MasteryLevel, Integer> weights = Map.of(
            MasteryLevel.LEVEL_0, 60,
            MasteryLevel.LEVEL_1, 30,
            MasteryLevel.LEVEL_2, 10
        );

        List<QuestionWithScore> allCandidates = new ArrayList<>();
        for (var entry : weights.entrySet()) {
            MasteryLevel level = entry.getKey();
            int weight = entry.getValue();
            int desired = session.getTotalQuestions() * weight / 100;
            if (desired > 0) {
                Map<String, Object> filters = new HashMap<>();
                filters.put("company", session.getCompany());
                filters.put("position", session.getPosition());
                filters.put("masteryLevel", level.getLevel());
                List<QuestionWithScore> candidates = questionRepository.searchUserVisible(
                    session.getUserId(), queryVector, filters, desired * 2);
                allCandidates.addAll(candidates);
            }
        }

        Collections.shuffle(allCandidates);
        List<QuestionWithScore> selected = allCandidates.stream()
            .limit(session.getTotalQuestions())
            .toList();

        for (QuestionWithScore qs : selected) {
            Question q = qs.question();
            InterviewQuestion iq = InterviewQuestion.create(
                q.getId(), q.getQuestionHash(), q.getQuestionText(),
                q.getQuestionType().getValue(), session.getDifficulty(),
                q.getCoreEntities()
            );
            session.addQuestion(iq);
        }

        log.info("Preloaded {} questions for session {}", selected.size(), session.getSessionId());
    }

    // ==================== System Prompt ====================

    private String getSystemPrompt(InterviewSession session) {
        String style = COMPANY_STYLES.getOrDefault(session.getCompany(), "专业、友好、有深度");
        return promptLoader.render("interviewer_system.md",
            "company", session.getCompany(),
            "position", session.getPosition(),
            "style", style
        );
    }

    // ==================== JSON / SSE Helpers ====================

    /**
     * 统一的 SSE frame 构建：使用 Jackson 序列化，与 ChatAgentService 保持一致。
     * Spring MVC Flux<String> + TEXT_EVENT_STREAM 会自动添加 "data:" 前缀，
     * 所以这里只返回 JSON 内容。
     */
    private String toSSEFrame(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data) + "\n\n";
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"serialization failed\"}\n\n";
        }
    }

    private String jsonError(String message) {
        return toSSEFrame(Map.of("type", "error", "message", message));
    }

    private String jsonScoreResult(ScoreResult result) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", "score_result");
        data.put("score", result.score());
        data.put("mastery_before", "LEVEL_0");
        data.put("mastery_after", result.masteryLevel());
        data.put("strengths", result.strengths() != null ? result.strengths() : List.of());
        data.put("improvements", result.improvements() != null ? result.improvements() : List.of());
        data.put("feedback", result.feedback() != null ? result.feedback() : "");
        return toSSEFrame(data);
    }

    private String jsonCompleted(InterviewSession session) {
        return toSSEFrame(Map.of(
            "type", "completed",
            "message", "面试已结束，感谢你的参与！",
            "session_id", session.getSessionId()
        ));
    }

    private String jsonNextQuestion(InterviewQuestion next, int idx, int score) {
        return toSSEFrame(Map.of(
            "type", "next_question_ready",
            "question_idx", idx,
            "next_question", next != null ? next.getQuestionText() : "",
            "score", score
        ));
    }

    private String jsonForceNext(InterviewQuestion next, int idx, int score) {
        return toSSEFrame(Map.of(
            "type", "force_next",
            "message", "该题目已追问 " + maxFollowUps + " 次，进入下一题",
            "question_idx", idx,
            "next_question", next != null ? next.getQuestionText() : "",
            "score", score
        ));
    }

    private String jsonFollowUp(int score, int followUpCount) {
        return toSSEFrame(Map.of(
            "type", "follow_up",
            "score", score,
            "follow_up_count", followUpCount,
            "max_follow_ups", maxFollowUps,
            "remaining_chances", maxFollowUps - followUpCount
        ));
    }
}
