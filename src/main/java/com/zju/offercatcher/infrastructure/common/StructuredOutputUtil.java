package com.zju.offercatcher.infrastructure.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.model.StructuredOutputReminder;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.tool.Toolkit;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;

/**
 * 结构化输出工具类。
 *
 * 3 级降级：
 * TOOL_CHOICE（json_schema，OpenAI/Qwen）→ FUNCTION_CALLING（DeepSeek 等）→ PROMPT（兜底）→ 默认值。
 */
public final class StructuredOutputUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private StructuredOutputUtil() {}

    /**
     * 带降级的结构化输出调用。
     */
    public static <T> T callWithFallback(
            OpenAIChatModel llm,
            String agentName,
            String sysPrompt,
            GenerateOptions options,
            List<Msg> messages,
            Class<T> schema,
            T defaultVal,
            Logger log) {

        // 1. TOOL_CHOICE（主力）
        try {
            T result = doStructuredCall(llm, agentName, sysPrompt, options,
                StructuredOutputReminder.TOOL_CHOICE, messages, schema);
            if (result != null) return result;
            log.warn("{}: TOOL_CHOICE returned null, falling back to FUNCTION_CALLING", agentName);
        } catch (Exception e) {
            log.warn("{}: TOOL_CHOICE failed ({}), falling back to FUNCTION_CALLING", agentName, e.getMessage());
        }

        // 2. FUNCTION_CALLING（DeepSeek 等，函数调用 arguments 天然纯 JSON）
        try {
            T result = callWithToolCall(llm, agentName, sysPrompt, options, messages, schema, log);
            if (result != null) return result;
            log.warn("{}: FUNCTION_CALLING returned null, falling back to PROMPT", agentName);
        } catch (Exception e) {
            log.warn("{}: FUNCTION_CALLING failed ({}), falling back to PROMPT", agentName, e.getMessage());
        }

        // 3. PROMPT（兜底）
        try {
            T result = doStructuredCall(llm, agentName, sysPrompt, options,
                StructuredOutputReminder.PROMPT, messages, schema);
            if (result != null) return result;
            log.error("{}: PROMPT returned null, returning default", agentName);
        } catch (Exception e) {
            log.error("{}: PROMPT failed ({}), returning default", agentName, e.getMessage());
        }

        return defaultVal;
    }

    // ==================== TOOL_CHOICE / PROMPT ====================

    private static <T> T doStructuredCall(OpenAIChatModel llm, String agentName, String sysPrompt,
                                          GenerateOptions options, StructuredOutputReminder mode,
                                          List<Msg> messages, Class<T> schema) {
        ReActAgent.Builder builder = ReActAgent.builder()
            .name(agentName).model(llm).maxIters(0).structuredOutputReminder(mode);
        if (sysPrompt != null && !sysPrompt.isBlank()) builder.sysPrompt(sysPrompt);
        if (options != null) builder.generateOptions(options);

        Msg response = builder.build().call(messages, schema).block();
        if (response != null && response.hasStructuredData()) {
            return response.getStructuredData(schema);
        }
        return null;
    }

    // ==================== FUNCTION_CALLING ====================

    /**
     * 通过 function calling 实现结构化输出。
     * 函数调用的 arguments 天然是纯 JSON，不受 markdown 包裹影响。
     */
    private static <T> T callWithToolCall(
            OpenAIChatModel llm, String agentName, String sysPrompt,
            GenerateOptions options, List<Msg> messages, Class<T> schema, Logger log) {

        // 1. 用 victools 生成 JSON Schema
        ObjectNode schemaNode;
        try {
            JacksonModule jacksonModule = new JacksonModule();
            SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                    MAPPER, OptionPreset.PLAIN_JSON)
                .with(jacksonModule);
            schemaNode = new SchemaGenerator(configBuilder.build()).generateSchema(schema);
        } catch (Exception e) {
            log.warn("{}: Failed to generate schema: {}", agentName, e.getMessage());
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = MAPPER.convertValue(schemaNode, Map.class);

        // 2. 注册 schema-only tool
        Toolkit toolkit = new Toolkit();
        toolkit.registerSchema(ToolSchema.builder()
            .name("output_extracted_data")
            .description("Output the structured data extracted from the input.")
            .parameters(parameters)
            .build());

        // 3. 构建 agent
        String fcSysPrompt = (sysPrompt != null && !sysPrompt.isBlank() ? sysPrompt + "\n\n" : "")
            + "You MUST call the output_extracted_data function to provide your result.";

        ReActAgent agent = ReActAgent.builder()
            .name(agentName).model(llm).toolkit(toolkit).maxIters(1)
            .sysPrompt(fcSysPrompt).generateOptions(options)
            .build();

        Msg response = agent.call(messages).block();
        if (response == null) return null;

        // 4. 提取 tool call arguments
        List<ToolUseBlock> toolCalls = response.getContentBlocks(ToolUseBlock.class);
        if (toolCalls == null || toolCalls.isEmpty()) return null;

        Map<String, Object> input = toolCalls.get(0).getInput();
        if (input == null) return null;

        try {
            return MAPPER.convertValue(input, schema);
        } catch (Exception e) {
            log.warn("{}: Failed to deserialize tool call args: {}", agentName, e.getMessage());
            return null;
        }
    }
}
