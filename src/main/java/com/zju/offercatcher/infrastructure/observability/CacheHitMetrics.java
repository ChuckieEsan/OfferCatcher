package com.zju.offercatcher.infrastructure.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * LLM 调用指标记录器。
 * <p>
 * 记录每次 API 调用的 token 消耗、缓存命中、延迟和错误情况，
 * 通过 Micrometer MeterRegistry 暴露给 Prometheus / OTLP。
 */
public class CacheHitMetrics {

    private final MeterRegistry registry;

    private final Map<String, Counter> promptTokenCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> cachedTokenCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> requestCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> completionTokenCounters = new ConcurrentHashMap<>();
    private final Map<String, Counter> errorCounters = new ConcurrentHashMap<>();
    private final Map<String, Timer> durationTimers = new ConcurrentHashMap<>();

    public CacheHitMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(String model, int promptTokens, int cachedTokens,
                       int completionTokens, long durationNanos, boolean success) {
        promptTokenCounter(model).increment(promptTokens);
        cachedTokenCounter(model).increment(cachedTokens);
        completionTokenCounter(model).increment(completionTokens);
        requestCounter(model).increment();
        durationTimer(model).record(durationNanos, TimeUnit.NANOSECONDS);
        if (!success) {
            errorCounter(model).increment();
        }
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

    private Counter completionTokenCounter(String model) {
        return completionTokenCounters.computeIfAbsent(model,
                m -> Counter.builder("llm.call.completion_tokens_total")
                        .description("Completion token total")
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

    private Counter errorCounter(String model) {
        return errorCounters.computeIfAbsent(model,
                m -> Counter.builder("llm.call.errors_total")
                        .description("LLM call error total")
                        .tags(Tags.of("model", m))
                        .register(registry));
    }

    private Timer durationTimer(String model) {
        return durationTimers.computeIfAbsent(model,
                m -> Timer.builder("llm.call.duration")
                        .description("LLM call duration")
                        .publishPercentiles(0.5, 0.95, 0.99)
                        .tags(Tags.of("model", m))
                        .register(registry));
    }
}
