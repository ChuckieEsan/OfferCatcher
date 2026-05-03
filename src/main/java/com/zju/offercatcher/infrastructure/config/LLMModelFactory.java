package com.zju.offercatcher.infrastructure.config;

import com.zju.offercatcher.infrastructure.observability.CacheHitTrackingTransport;
import io.agentscope.core.model.OpenAIChatModel;
import org.springframework.stereotype.Component;

/**
 * 共享 LLM 模型工厂。
 *
 * 所有 Agent 通过此工厂创建 OpenAIChatModel，统一注入可观测 Transport。
 *
 * 三种精度预设：
 *   createModel(provider, stream)          — 默认 model（如 deepseek-chat）
 *   createSimple(provider, stream)         — 简单任务（deepseek-v4-flash / gpt-4o-mini）
 *   createComplex(provider, stream)        — 复杂任务（deepseek-v4-pro / gpt-4o / qwen-max）
 *   createModel(provider, stream, model)   — 手动指定 model 名
 */
@Component
public class LLMModelFactory {

    private final LLMProperties llmProperties;
    private final CacheHitTrackingTransport transport;

    public LLMModelFactory(LLMProperties llmProperties, CacheHitTrackingTransport transport) {
        this.llmProperties = llmProperties;
        this.transport = transport;
    }

    /** 默认 model。 */
    public OpenAIChatModel createModel(String providerKey, boolean stream) {
        return build(llmProperties.getProvider(providerKey), stream);
    }

    /** 简单任务（flash / mini）。 */
    public OpenAIChatModel createSimple(String providerKey, boolean stream) {
        return build(llmProperties.getSimple(providerKey), stream);
    }

    /** 复杂任务（pro / max）。 */
    public OpenAIChatModel createComplex(String providerKey, boolean stream) {
        return build(llmProperties.getComplex(providerKey), stream);
    }

    /** 手动指定 model 名，覆盖配置文件中的 model。 */
    public OpenAIChatModel createModel(String providerKey, boolean stream, String model) {
        return build(llmProperties.getProvider(providerKey, model), stream);
    }

    private OpenAIChatModel build(LLMProperties.Provider cfg, boolean stream) {
        return OpenAIChatModel.builder()
            .apiKey(cfg.apiKey())
            .modelName(cfg.model())
            .baseUrl(cfg.baseUrl())
            .stream(stream)
            .httpTransport(transport)
            .build();
    }
}
