package com.zju.offercatcher.application.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.agent.dto.ExtractedQuestionItem;
import com.zju.offercatcher.domain.question.services.QuestionIdGenerator;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 面经提取 Agent
 *
 * 从文本中提取面经题目信息，返回结构化数据。
 * 对应 Python: app/application/agents/vision_extractor/agent.py
 */
@Service
public class VisionExtractorAgent {

    private static final Logger log = LoggerFactory.getLogger(VisionExtractorAgent.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final OpenAIChatModel llm;
    private final PromptLoader promptLoader;

    public VisionExtractorAgent(LLMProperties llmProperties, PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.llm = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(false)
            .build();
    }

    public ExtractedQuestionItem extract(String text) {
        log.info("VisionExtractor: extracting from text ({} chars)", text.length());

        String prompt = promptLoader.render("vision_extractor.md", "text", text);

        ReActAgent agent = ReActAgent.builder()
            .name("vision-extractor")
            .model(llm)
            .maxIters(0)
            .generateOptions(GenerateOptions.builder().temperature(0.1).build())
            .build();

        try {
            Msg response = agent.call(List.of(
                Msg.builder().role(MsgRole.USER).textContent(prompt).build()
            )).block();

            String content = response != null ? response.getTextContent() : "";
            return parseResponse(content);
        } catch (Exception e) {
            log.error("Vision extraction failed: {}", e.getMessage(), e);
            throw new RuntimeException("面经提取失败: " + e.getMessage(), e);
        }
    }

    private ExtractedQuestionItem parseResponse(String content) {
        try {
            int jsonStart = content.indexOf("{");
            int jsonEnd = content.lastIndexOf("}") + 1;
            if (jsonStart == -1 || jsonEnd == 0) {
                throw new RuntimeException("Response contains no JSON");
            }
            String jsonStr = content.substring(jsonStart, jsonEnd);

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);

            String company = (String) data.getOrDefault("company", "未知");
            String position = (String) data.getOrDefault("position", "未知");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> questionsRaw = (List<Map<String, Object>>) data.get("questions");
            List<ExtractedQuestionItem.QuestionItem> questions = new ArrayList<>();

            if (questionsRaw != null) {
                for (Map<String, Object> q : questionsRaw) {
                    String questionText = (String) q.get("question_text");
                    String typeStr = (String) q.getOrDefault("question_type", "knowledge");
                    QuestionType questionType;
                    try {
                        questionType = QuestionType.fromValue(typeStr);
                    } catch (Exception e) {
                        questionType = QuestionType.KNOWLEDGE;
                    }

                    @SuppressWarnings("unchecked")
                    List<String> coreEntities = (List<String>) q.getOrDefault("core_entities", List.of());

                    @SuppressWarnings("unchecked")
                    Map<String, Object> metadata = (Map<String, Object>) q.getOrDefault("metadata", Map.of());

                    String questionId = QuestionIdGenerator.generateSystemId(company, questionText);

                    questions.add(new ExtractedQuestionItem.QuestionItem(
                        questionId, questionText, questionType.getValue(),
                        coreEntities != null ? coreEntities : List.of(),
                        metadata != null ? metadata : Map.of()
                    ));
                }
            }

            return new ExtractedQuestionItem(company, position, questions);
        } catch (Exception e) {
            log.error("Failed to parse VisionExtractor response: {}", e.getMessage());
            throw new RuntimeException("面经解析失败: " + e.getMessage(), e);
        }
    }
}
