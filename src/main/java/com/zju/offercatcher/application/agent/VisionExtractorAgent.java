package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.domain.question.valueobjects.ExtractedQuestionItem;
import com.zju.offercatcher.application.agent.dto.VisionExtractorOutput;
import com.zju.offercatcher.infrastructure.common.PromptLoader;
import com.zju.offercatcher.infrastructure.common.StructuredOutputUtil;
import com.zju.offercatcher.domain.question.services.QuestionHashGenerator;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.infrastructure.adapters.ocr.OcrAdapter;
import com.zju.offercatcher.infrastructure.config.LLMModelFactory;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 面经提取 Agent
 *
 * 从文本或图片中提取面经题目信息，返回结构化数据。
 * 对应 Python: app/application/agents/vision_extractor/agent.py
 */
@Service
public class VisionExtractorAgent {

    private static final Logger log = LoggerFactory.getLogger(VisionExtractorAgent.class);

    private static final VisionExtractorOutput DEFAULT_OUTPUT = VisionExtractorOutput.DEFAULT;

    private static final GenerateOptions OPTIONS = GenerateOptions.builder()
        .temperature(0.1)
        .build();

    private final OpenAIChatModel llm;
    private final PromptLoader promptLoader;
    private final OcrAdapter ocrAdapter;

    public VisionExtractorAgent(LLMModelFactory modelFactory, PromptLoader promptLoader,
                                OcrAdapter ocrAdapter) {
        this.promptLoader = promptLoader;
        this.ocrAdapter = ocrAdapter;
        this.llm = modelFactory.createComplex("deepseek", false);
    }

    public ExtractedQuestionItem extract(String text) {
        log.info("VisionExtractor: extracting from text ({} chars)", text.length());
        return doExtract(text);
    }

    public ExtractedQuestionItem extractFromImages(List<String> imageSources) {
        log.info("VisionExtractor: extracting from {} images", imageSources.size());

        String ocrText = ocrAdapter.recognizeBatch(imageSources);
        if (ocrText == null || ocrText.isBlank()) {
            throw new RuntimeException("OCR 未能识别出文字");
        }

        log.info("OCR result ({} chars), forwarding to LLM extraction", ocrText.length());
        return doExtract(ocrText);
    }

    private ExtractedQuestionItem doExtract(String text) {
        String prompt = promptLoader.render("vision_extractor.md", "text", text);
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(prompt).build();

        VisionExtractorOutput output = StructuredOutputUtil.callWithFallback(
            llm, "vision-extractor", null, OPTIONS,
            List.of(userMsg), VisionExtractorOutput.class, DEFAULT_OUTPUT, log);

        List<ExtractedQuestionItem.QuestionItem> questions = output.questions().stream()
            .map(q -> {
                String hash = QuestionHashGenerator.generateSystemQuestionHash(
                    output.company(), q.questionText());
                QuestionType type;
                try {
                    type = QuestionType.fromValue(q.questionType());
                } catch (Exception e) {
                    type = QuestionType.KNOWLEDGE;
                }
                return new ExtractedQuestionItem.QuestionItem(
                    hash, q.questionText(), type.getValue(),
                    q.coreEntities() != null ? q.coreEntities() : List.of(),
                    q.metadata() != null ? q.metadata() : Map.of());
            })
            .toList();

        return new ExtractedQuestionItem(
            output.company() != null ? output.company() : "未知",
            output.position() != null ? output.position() : "未知",
            questions);
    }
}
