package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.application.service.ChatApplicationService;
import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
import com.zju.offercatcher.infrastructure.tools.*;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话 Agent 服务
 *
 * 核心 ReActAgent，处理用户消息，支持：
 * - 多工具调用（search_questions, search_web, knowledge graph, memory）
 * - SSE 流式输出
 * - 记忆上下文注入
 * - 用户隔离（通过 ToolExecutionContext）
 *
 * 对应 Python:
 *   app/application/agents/chat/agent.py
 *   app/application/agents/chat/workflow.py
 */
@Service
public class ChatAgentService {

    private static final Logger log = LoggerFactory.getLogger(ChatAgentService.class);
    private static final int MAX_HISTORY_MESSAGES = 20;

    private final ChatApplicationService chatService;
    private final MemoryApplicationService memoryService;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final MemoryAgentService memoryAgent;
    private final PromptLoader promptLoader;
    private final LLMProperties llmProperties;

    // Tools
    private final SearchQuestionsTool searchQuestionsTool;
    private final WebSearchTool webSearchTool;
    private final KnowledgeGraphTools knowledgeGraphTools;
    private final MemoryTools memoryTools;

    public ChatAgentService(ChatApplicationService chatService,
                             MemoryApplicationService memoryService,
                             SessionSummaryRepository sessionSummaryRepository,
                             MemoryAgentService memoryAgent,
                             PromptLoader promptLoader,
                             LLMProperties llmProperties,
                             SearchQuestionsTool searchQuestionsTool,
                             WebSearchTool webSearchTool,
                             KnowledgeGraphTools knowledgeGraphTools,
                             MemoryTools memoryTools) {
        this.chatService = chatService;
        this.memoryService = memoryService;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.memoryAgent = memoryAgent;
        this.promptLoader = promptLoader;
        this.llmProperties = llmProperties;
        this.searchQuestionsTool = searchQuestionsTool;
        this.webSearchTool = webSearchTool;
        this.knowledgeGraphTools = knowledgeGraphTools;
        this.memoryTools = memoryTools;
    }

    /**
     * 流式对话
     */
    public Flux<Event> chatStream(String message, Long conversationId, String userId) {
        log.info("ChatStream: conversation={}, user={}, message={}", conversationId, userId,
            message.substring(0, Math.min(50, message.length())));

        // 1. Ensure conversation exists and save user message
        Conversation conversation = chatService.getConversation(userId, conversationId).orElse(null);
        if (conversation == null) {
            conversation = chatService.createConversation(userId, null);
        }
        final Long finalConversationId = conversation.getConversationId();
        chatService.addMessage(userId, finalConversationId, MessageRole.USER, message);

        // 2. Build history and memory context
        Conversation refreshed = chatService.getConversation(userId, finalConversationId).orElse(conversation);
        List<Msg> historyMessages = buildHistoryMessages(refreshed);
        String memoryContext = buildMemoryContext(userId);

        // 3. Create ReActAgent
        ReActAgent agent = createReActAgent(userId, memoryContext);
        List<Msg> input = new ArrayList<>(historyMessages);
        input.add(Msg.builder().role(MsgRole.USER).textContent(message).build());

        // 4. Stream and collect full response
        StreamOptions streamOptions = StreamOptions.builder()
            .includeReasoningChunk(true)
            .includeActingChunk(true)
            .build();

        Sinks.Many<Event> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder fullResponse = new StringBuilder();

        agent.stream(input, streamOptions)
            .doOnNext(event -> {
                sink.tryEmitNext(event);
                if (event.getType() == io.agentscope.core.agent.EventType.REASONING
                    && event.getMessage() != null) {
                    String text = event.getMessage().getTextContent();
                    if (text != null) {
                        fullResponse.append(text);
                    }
                }
            })
            .doOnComplete(() -> {
                // 5. Save AI response
                String aiResponse = fullResponse.toString();
                if (!aiResponse.isBlank()) {
                    chatService.addMessage(userId, finalConversationId, MessageRole.ASSISTANT, aiResponse);

                    // 6. Auto-generate title if needed
                    Conversation conv = chatService.getConversation(userId, finalConversationId).orElse(null);
                    if (conv != null && "新对话".equals(conv.getTitle()) && conv.getMessages().size() >= 4) {
                        TitleGeneratorAgent titleGen = new TitleGeneratorAgent(llmProperties, promptLoader);
                        chatService.generateTitle(userId, finalConversationId,
                            msgs -> titleGen.generateTitle(msgs));
                    }

                    // 7. Trigger background memory extraction
                    if (conv != null) {
                        memoryAgent.extractMemories(userId, finalConversationId, conv.getMessages());
                    }
                }
                sink.tryEmitComplete();
            })
            .doOnError(error -> {
                log.error("Chat stream error: {}", error.getMessage(), error);
                sink.tryEmitError(error);
            })
            .subscribe();

        return sink.asFlux();
    }

    // ==================== ReActAgent 创建 ====================

    private ReActAgent createReActAgent(String userId, String memoryContext) {
        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        OpenAIChatModel model = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(true)
            .build();

        Toolkit toolkit = new Toolkit(ToolkitConfig.defaultConfig());
        toolkit.registerTool(searchQuestionsTool);
        toolkit.registerTool(webSearchTool);
        toolkit.registerTool(knowledgeGraphTools);
        toolkit.registerTool(memoryTools);

        ToolExecutionContext toolContext = ToolExecutionContext.builder()
            .register("userContext", new UserToolContext(userId))
            .build();

        String systemPrompt = buildSystemPrompt(memoryContext);

        return ReActAgent.builder()
            .name("offer-catcher-chat")
            .description("AI 面试助手，帮助用户准备技术面试")
            .sysPrompt(systemPrompt)
            .model(model)
            .toolkit(toolkit)
            .toolExecutionContext(toolContext)
            .maxIters(10)
            .generateOptions(GenerateOptions.builder()
                .temperature(0.3)
                .maxTokens(2048)
                .build())
            .build();
    }

    // ==================== System Prompt ====================

    private String buildSystemPrompt(String memoryContext) {
        String template = promptLoader.load("react_agent.md");
        template = template.replace("{{ skills_prompt }}", "");

        if (memoryContext != null && !memoryContext.isBlank()) {
            template += "\n\n<记忆上下文>\n" + memoryContext + "\n</记忆上下文>";
        }

        return template;
    }

    // ==================== History & Memory ====================

    private List<Msg> buildHistoryMessages(Conversation conversation) {
        List<Msg> msgs = new ArrayList<>();
        List<Message> messages = conversation.getMessages();

        int start = Math.max(0, messages.size() - MAX_HISTORY_MESSAGES);
        for (int i = start; i < messages.size(); i++) {
            Message m = messages.get(i);
            MsgRole role = m.isUserMessage() ? MsgRole.USER : MsgRole.ASSISTANT;
            msgs.add(Msg.builder().role(role).textContent(m.getContent()).build());
        }
        return msgs;
    }

    private String buildMemoryContext(String userId) {
        StringBuilder sb = new StringBuilder();

        String memoryContent = memoryService.getMemoryContent(userId);
        if (memoryContent != null && !memoryContent.isBlank()) {
            sb.append(memoryContent);
        }

        List<SessionSummary> summaries = sessionSummaryRepository.findTopKByImportance(userId, 3);
        if (!summaries.isEmpty()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n---\n\n## 相关历史会话\n");
            }
            for (SessionSummary s : summaries) {
                sb.append("- ").append(s.getSummary()).append("\n");
            }
        }

        return sb.toString();
    }
}
