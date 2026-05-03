package com.zju.offercatcher.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 缓存命中指标记录器。
 *
 * 每次 LLM API 调用后记录 prompt token 消耗量和缓存命中量，
 * 在 Grafana 中通过 rate(cached_tokens) / rate(prompt_tokens) 计算命中率。
 */
public class CacheHitMetrics {

    private final MeterRegistry registry;

    private final Map<String, Counter> promptTokenCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> cachedTokenCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> requestCounters = new ConcurrentHashMap<>();

    public CacheHitMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String model, int promptTokens, int cachedTokens) {
        promptTokenCounter(model).increment(promptTokens);
        cachedTokenCounter(model).increment(cachedTokens);
        requestCounter(model).increment();
    }

    private Counter promptTokenCounter(String model) {
        return promptTokenCounters.computeIfAbsent(model,
            m -> Counter.builder("llm.cache.prompt_tokens_total")
                .description("Prompt token consumption total")
                .tags(Tags.of("model", m))
                .register(registry));
    }

    private Counter cachedTokenCounter(String model) {
        return cachedTokenCounters.computeIfAbsent(model,
            m -> Counter.builder("llm.cache.cached_tokens_total")
                .description("Cache hit token total")
                .tags(Tags.of("model", m))
                .register(registry));
    }

    private Counter requestCounter(String model) {
        return requestCounters.computeIfAbsent(model,
            m -> Counter.builder("llm.cache.requests_total")
                .description("LLM request total")
                .tags(Tags.of("model", m))
                .register(registry));
    }
}
