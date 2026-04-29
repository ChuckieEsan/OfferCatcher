package com.zju.offercatcher.application.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.application.service.ChatApplicationService;
import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.application.service.MemoryRetrievalService;
import com.zju.offercatcher.domain.chat.aggregates.Conversation;
import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import com.zju.offercatcher.domain.shared.enums.MessageRole;
import com.zju.offercatcher.infrastructure.config.LLMProperties;
import com.zju.offercatcher.infrastructure.tools.*;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.Event;
import io.agentscope.core.agent.EventType;
import io.agentscope.core.agent.StreamOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ThinkingBlock;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
    private final MemoryRetrievalService memoryRetrieval;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final MemoryAgentService memoryAgent;
    private final PromptLoader promptLoader;
    private final LLMProperties llmProperties;
    private final Executor workerExecutor;
    private final ObjectMapper objectMapper;

    // Cached shared resources — OpenAIChatModel and Toolkit are stateless config, safe to reuse
    private final OpenAIChatModel cachedModel;
    private final Toolkit cachedToolkit;

    public ChatAgentService(ChatApplicationService chatService,
                             MemoryApplicationService memoryService,
                             MemoryRetrievalService memoryRetrieval,
                             SessionSummaryRepository sessionSummaryRepository,
                             MemoryAgentService memoryAgent,
                             PromptLoader promptLoader,
                             LLMProperties llmProperties,
                             SearchQuestionsTool searchQuestionsTool,
                             WebSearchTool webSearchTool,
                             KnowledgeGraphTools knowledgeGraphTools,
                             MemoryTools memoryTools,
                             @Qualifier("workerExecutor") Executor workerExecutor,
                             ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.memoryService = memoryService;
        this.memoryRetrieval = memoryRetrieval;
        this.sessionSummaryRepository = sessionSummaryRepository;
        this.memoryAgent = memoryAgent;
        this.promptLoader = promptLoader;
        this.llmProperties = llmProperties;
        this.workerExecutor = workerExecutor;
        this.objectMapper = objectMapper;

        // Pre-create shared model and toolkit (stateless, thread-safe config)
        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.cachedModel = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey())
            .modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl())
            .stream(true)
            .build();

        this.cachedToolkit = new Toolkit(ToolkitConfig.defaultConfig());
        this.cachedToolkit.registerTool(searchQuestionsTool);
        this.cachedToolkit.registerTool(webSearchTool);
        this.cachedToolkit.registerTool(knowledgeGraphTools);
        this.cachedToolkit.registerTool(memoryTools);

        log.info("ChatAgentService initialized with cached model={}, tools=4", cfg.getModel());
    }

    /**
     * 流式对话
     *
     * 返回 SSE 就绪的 Flux<String>，每个元素为一条 SSE data 帧。
     * 后处理（保存消息、生成标题、记忆提取）异步执行，不阻塞流式输出。
     */
    public Flux<String> chatStream(String message, Long conversationId, String userId) {
        log.info("ChatStream: conversation={}, user={}, message={}", conversationId, userId,
            message.substring(0, Math.min(50, message.length())));

        // 1. Ensure conversation exists and save user message
        Conversation conversation = chatService.getConversation(userId, conversationId).orElse(null);
        if (conversation == null) {
            conversation = chatService.createConversation(userId, null);
        }
        final Long finalConversationId = conversation.getConversationId();
        chatService.addMessage(userId, finalConversationId, MessageRole.USER, message);

        // 2. Fire-and-forget: trigger async memory retrieval for this turn's message.
        //    Results will be available for the NEXT turn via getCachedContext().
        memoryRetrieval.triggerRetrieval(userId, finalConversationId, message);

        // 3. Build history and memory context.
        //    Static memory (MEMORY.md) is read synchronously.
        //    Dynamic session summaries come from the PREVIOUS turn's async retrieval.
        Conversation refreshed = chatService.getConversation(userId, finalConversationId).orElse(conversation);
        List<Msg> historyMessages = buildHistoryMessages(refreshed);
        String asyncRetrievedContext = memoryRetrieval.getCachedContext(userId, finalConversationId);
        String memoryContext = buildMemoryContext(userId, asyncRetrievedContext);

        // 4. Create ReActAgent (model + toolkit are cached, only per-request parts are new)
        ReActAgent agent = createReActAgent(userId, memoryContext);
        List<Msg> input = new ArrayList<>(historyMessages);
        input.add(Msg.builder().role(MsgRole.USER).textContent(message).build());

        // 4. Stream with chunk events only (avoid duplicate output from result events)
        StreamOptions streamOptions = StreamOptions.builder()
            .includeReasoningChunk(true)
            .includeReasoningResult(false)   // 不输出完整思考结果，避免重复
            .includeActingChunk(true)
            .includeSummaryChunk(true)
            .includeSummaryResult(false)    // 不输出完整回答结果，避免重复
            .build();

        Sinks.Many<Event> sink = Sinks.many().unicast().onBackpressureBuffer();
        StringBuilder formalResponse = new StringBuilder();
        StringBuilder reasoningResponse = new StringBuilder();
        List<Map<String, Object>> toolCallRecords = new ArrayList<>();

        agent.stream(input, streamOptions)
            .doOnNext(event -> {
                sink.tryEmitNext(event);
                collectTextContent(event, formalResponse, reasoningResponse, toolCallRecords);
            })
            .doOnComplete(() -> {
                // Emit final event before completing
                sink.tryEmitNext(new Event(EventType.AGENT_RESULT,
                    Msg.builder().role(MsgRole.ASSISTANT).textContent("").build(), true));
                sink.tryEmitComplete();

                // Async post-processing — does NOT block stream completion
                CompletableFuture.runAsync(() -> postProcess(userId, finalConversationId,
                    formalResponse.toString(), reasoningResponse.toString(), toolCallRecords),
                    workerExecutor);
            })
            .doOnError(error -> {
                log.error("Chat stream error: {}", error.getMessage(), error);
                sink.tryEmitError(error);
            })
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe();

        return sink.asFlux()
            .map(event -> toSSEFrame(event))
            .filter(frame -> !frame.isEmpty());
    }

    // ==================== Event Text Collection ====================

    /**
     * 分离收集 thinking、正式回答和工具调用记录。
     *
     * 用 AgentScope 原生的 ContentBlock 类型来判断，而非 event.getType()——
     * 对于无工具调用的对话，AgentScope 的 reasoning() 阶段所有事件都是 REASONING 类型。
     *
     * ThinkingBlock → reasoningResponse
     * TextBlock      → formalResponse
     * ToolResultBlock → toolCallRecords
     */
    private void collectTextContent(Event event, StringBuilder formalResponse,
                                     StringBuilder reasoningResponse,
                                     List<Map<String, Object>> toolCallRecords) {
        Msg msg = event.getMessage();
        if (msg == null) return;

        // ThinkingBlock → reasoning
        List<ThinkingBlock> thinkingBlocks = msg.getContentBlocks(ThinkingBlock.class);
        if (!thinkingBlocks.isEmpty()) {
            String thinking = thinkingBlocks.get(0).getThinking();
            if (thinking != null && !thinking.isBlank()) {
                reasoningResponse.append(thinking);
            }
            return;
        }

        // ToolResultBlock → structured tool call records
        if (!msg.getContentBlocks(ToolResultBlock.class).isEmpty()) {
            recordToolCall(msg, toolCallRecords);
            return;
        }

        // TextBlock → formal response (skip synthetic result events, content already accumulated from chunks)
        if (event.getType() == EventType.AGENT_RESULT || event.getType() == EventType.HINT) {
            return;
        }
        String text = msg.getTextContent();
        if (text != null && !text.isBlank()) {
            formalResponse.append(text);
        }
    }

    /**
     * 利用 AgentScope 原生的 ToolResultBlock API 提取工具调用信息。
     */
    private void recordToolCall(Msg message, List<Map<String, Object>> toolCallRecords) {
        List<ToolResultBlock> results = message.getContentBlocks(ToolResultBlock.class);
        for (ToolResultBlock result : results) {
            String toolName = result.getName() != null ? result.getName() : "unknown";
            String resultText = result.getOutput() != null
                ? result.getOutput().stream()
                    .map(b -> b instanceof io.agentscope.core.message.TextBlock tb ? tb.getText() : "")
                    .reduce("", (a, b) -> a + b)
                : message.getTextContent();

            // Truncate result to avoid excessive DB storage
            String truncatedResult = resultText != null && resultText.length() > 500
                ? resultText.substring(0, 500) + "…" : resultText;

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("tool", toolName);
            record.put("result", truncatedResult != null ? truncatedResult : "");
            toolCallRecords.add(record);
        }

        // Fallback: if no ToolResultBlock found, record from text content
        if (results.isEmpty()) {
            String text = message.getTextContent();
            if (text != null && !text.isBlank()) {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("tool", "unknown");
                record.put("result", text.length() > 500 ? text.substring(0, 500) + "…" : text);
                toolCallRecords.add(record);
            }
        }
    }

    // ==================== SSE Serialization ====================

    /**
     * 将 Event 转为 SSE frame。
     *
     * 用 AgentScope 原生的 ContentBlock 类型来区分输出类型。
     * 注意：跳过 Result 类型事件中的 TextBlock（完整内容已通过 Chunk 累加输出，避免重复）。
     */
    private String toSSEFrame(Event event) {
        Msg msg = event.getMessage();
        if (msg == null) {
            return sseJson("unknown", "");
        }

        EventType eventType = event.getType();
        // AGENT_RESULT 和 HINT 是合成事件，包含已累加的完整内容，不应再输出
        boolean isSyntheticResult = eventType == EventType.AGENT_RESULT
            || eventType == EventType.HINT;

        // 优先根据 ContentBlock 类型判断实际输出类型
        if (!msg.getContentBlocks(ThinkingBlock.class).isEmpty()) {
            String thinking = msg.getContentBlocks(ThinkingBlock.class).get(0).getThinking();
            return sseJson("reasoning", thinking != null ? thinking : "");
        }

        if (!msg.getContentBlocks(TextBlock.class).isEmpty()) {
            // 跳过合成 Result 事件：完整内容已通过 Chunk 累加输出到前端
            if (isSyntheticResult) {
                return "";
            }
            String text = msg.getTextContent();
            return sseJson("text", text != null ? text : "");
        }

        if (!msg.getContentBlocks(ToolResultBlock.class).isEmpty()) {
            String text = msg.getTextContent();
            return sseJson("tool_result", text != null ? text : "");
        }

        // Fallback: use EventType name (for agent_result, hint, etc.)
        String type = event.getType() != null ? event.getType().name().toLowerCase() : "unknown";
        String content = msg.getTextContent();
        return sseJson(type, content != null ? content : "");
    }

    private String sseJson(String type, String content) {
        try {
            Map<String, String> frame = Map.of("type", type, "content", content);
            return objectMapper.writeValueAsString(frame) + "\n\n";
        } catch (JsonProcessingException e) {
            return "{\"type\":\"" + type + "\",\"content\":\"\"}\n\n";
        }
    }

    // ==================== Post-Processing (Async) ====================

    private void postProcess(String userId, Long conversationId, String formalResponse,
                               String reasoningResponse, List<Map<String, Object>> toolCallRecords) {
        try {
            if (formalResponse.isBlank() && reasoningResponse.isBlank()) return;

            String reasoning = reasoningResponse.isBlank() ? null : reasoningResponse.toString();
            String toolCallsJson = toolCallRecords.isEmpty() ? null
                : objectMapper.writeValueAsString(toolCallRecords);

            chatService.addMessage(userId, conversationId, MessageRole.ASSISTANT,
                formalResponse.toString(), reasoning, toolCallsJson);

            Conversation conv = chatService.getConversation(userId, conversationId).orElse(null);
            if (conv == null) return;

            // Auto-generate title
            if ("新对话".equals(conv.getTitle()) && conv.getMessages().size() >= 4) {
                try {
                    TitleGeneratorAgent titleGen = new TitleGeneratorAgent(llmProperties, promptLoader);
                    chatService.generateTitle(userId, conversationId,
                        msgs -> titleGen.generateTitle(msgs));
                } catch (Exception e) {
                    log.warn("Title generation failed: {}", e.getMessage());
                }
            }

            // Trigger background memory extraction
            memoryAgent.extractMemories(userId, conversationId, conv.getMessages());

        } catch (Exception e) {
            log.error("Post-processing failed for conversation {}: {}", conversationId, e.getMessage(), e);
        }
    }

    // ==================== ReActAgent 创建 ====================

    private ReActAgent createReActAgent(String userId, String memoryContext) {
        ToolExecutionContext toolContext = ToolExecutionContext.builder()
            .register("userContext", new UserToolContext(userId))
            .build();

        String systemPrompt = buildSystemPrompt(memoryContext);

        return ReActAgent.builder()
            .name("offer-catcher-chat")
            .description("AI 面试助手，帮助用户准备技术面试")
            .sysPrompt(systemPrompt)
            .model(cachedModel)
            .toolkit(cachedToolkit)
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

    /**
     * 构建记忆上下文。
     *
     * 包含两部分：
     * 1. 静态记忆（MEMORY.md）— 同步读取，包含用户偏好和行为模式概要
     * 2. 异步检索的会话摘要 — 来自上一轮对话的 fire-and-forget 检索结果，
     *    如果这是首批消息则可能为空
     */
    private String buildMemoryContext(String userId, String asyncRetrievedContext) {
        StringBuilder sb = new StringBuilder();

        // Static memory (MEMORY.md) — user preferences & behaviors summary
        String memoryContent = memoryService.getMemoryContent(userId);
        if (memoryContent != null && !memoryContent.isBlank()) {
            sb.append(memoryContent);
        }

        // Async-retrieved session summaries from previous turn
        if (asyncRetrievedContext != null && !asyncRetrievedContext.isBlank()) {
            if (!sb.isEmpty()) {
                sb.append("\n\n---\n\n## 相关历史会话\n");
            }
            sb.append(asyncRetrievedContext);
        } else {
            // Fallback: sync top-K query (fast path for first message)
            List<SessionSummary> summaries = sessionSummaryRepository.findTopKByImportance(userId, 3);
            if (!summaries.isEmpty()) {
                if (!sb.isEmpty()) {
                    sb.append("\n\n---\n\n## 相关历史会话\n");
                }
                for (SessionSummary s : summaries) {
                    sb.append("- ").append(s.getSummary()).append("\n");
                }
            }
        }

        return sb.toString();
    }
}
