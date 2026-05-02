package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.infrastructure.common.PromptLoader;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
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
 * 对话标题生成 Agent
 *
 * 分析对话内容，生成简洁标题（不超过 20 字）。
 * 对应 Python: app/application/agents/title_generator/agent.py
 */
@Service
public class TitleGeneratorAgent {

    private static final Logger log = LoggerFactory.getLogger(TitleGeneratorAgent.class);

    private final OpenAIChatModel llm;
    private final PromptLoader promptLoader;

    public TitleGeneratorAgent(LLMProperties llmProperties, PromptLoader promptLoader) {
        this.promptLoader = promptLoader;
        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.llm = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(false)
            .build();
    }

    public String generateTitle(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "新对话";
        }

        String conversationContent = buildConversationContent(messages);
        String prompt = promptLoader.render("title_generator.md",
            "conversation_content", conversationContent);

        ReActAgent agent = ReActAgent.builder()
            .name("title-generator")
            .model(llm)
            .maxIters(0)
            .generateOptions(GenerateOptions.builder()
                .temperature(0.3)
                .maxTokens(50)
                .build())
            .build();

        try {
            Msg response = agent.call(List.of(
                Msg.builder().role(MsgRole.USER).textContent(prompt).build()
            )).block();

            String title = response != null ? response.getTextContent().trim() : "新对话";
            if (title.length() > 20) {
                title = title.substring(0, 20);
            }
            log.info("Generated title: {}", title);
            return title.isBlank() ? "新对话" : title;
        } catch (Exception e) {
            log.error("Title generation failed: {}", e.getMessage(), e);
            return "新对话";
        }
    }

    private String buildConversationContent(List<Message> messages) {
        StringBuilder sb = new StringBuilder();
        for (Message msg : messages) {
            String roleLabel = "user".equals(msg.getRole().name().toLowerCase()) ? "用户" : "AI";
            String content = msg.getContent();
            if (content != null && content.length() > 200) {
                content = content.substring(0, 200);
            }
            sb.append(roleLabel).append(": ").append(content != null ? content : "").append("\n");
        }
        return sb.toString();
    }
}
