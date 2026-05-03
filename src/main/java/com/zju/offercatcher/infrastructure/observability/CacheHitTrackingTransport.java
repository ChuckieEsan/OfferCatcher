package com.zju.offercatcher.infrastructure.observability;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.model.transport.HttpRequest;
import io.agentscope.core.model.transport.HttpResponse;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * HttpTransport 装饰器，拦截 OpenAI 兼容 API 响应提取 LLM 调用指标。
 * <p>
 * 多 Provider 兼容：所有提供商（DeepSeek/OpenAI/SiliconFlow）遵循 OpenAI 协议，
 * model 名从请求 JSON 动态提取，无需硬编码。
 * <p>
 * 解析失败不抛异常，不影响 Agent 主流程。
 */
public class CacheHitTrackingTransport implements HttpTransport {

    private static final Logger log = LoggerFactory.getLogger(CacheHitTrackingTransport.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpTransport delegate;
    private final CacheHitMetrics metrics;

    public CacheHitTrackingTransport(HttpTransport delegate, CacheHitMetrics metrics) {
        this.delegate = delegate;
        this.metrics = metrics;
    }

    // ---- 非流式 ----

    @Override
    public HttpResponse execute(HttpRequest request) throws HttpTransportException {
        long start = System.nanoTime();
        String model = extractModelName(request.getBody());
        HttpResponse response;
        try {
            response = delegate.execute(request);
        } catch (HttpTransportException e) {
            metrics.record(model, 0, 0, 0, System.nanoTime() - start, false);
            throw e;
        }
        boolean success = response.isSuccessful();
        String body = response.getBody();
        int promptTokens = parsePromptTokens(body);
        int cachedTokens = parseCachedTokens(body);
        int completionTokens = parseCompletionTokens(body);
        long duration = System.nanoTime() - start;
        metrics.record(model, promptTokens, cachedTokens, completionTokens, duration, success);
        log.debug("LLM call: model={}, prompt={}, cached={}, completion={}, success={}, {}ms",
                model, promptTokens, cachedTokens, completionTokens, success, duration / 1_000_000);
        return response;
    }

    // ---- 流式 ----

    @Override
    public Flux<String> stream(HttpRequest request) {
        // 从请求 JSON 提取 model 名（一次），耗时从 subscribe 开始计算
        String model = extractModelName(request.getBody());
        AtomicLong startNanos = new AtomicLong();
        AtomicBoolean ok = new AtomicBoolean(true);
        AtomicReference<String> lastDataLine = new AtomicReference<>();

        return delegate.stream(request)
                .doOnSubscribe(s -> startNanos.set(System.nanoTime()))
                .doOnNext(line -> {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if (!"[DONE]".equals(data)) {
                            lastDataLine.set(data);
                        }
                    }
                })
                .doOnComplete(() -> {
                    long duration = System.nanoTime() - startNanos.get();
                    String last = lastDataLine.get();
                    int promptTokens = 0;
                    int cachedTokens = 0;
                    int completionTokens = 0;
                    if (last != null) {
                        promptTokens = parsePromptTokens(last);
                        cachedTokens = parseCachedTokens(last);
                        completionTokens = parseCompletionTokens(last);
                    }
                    metrics.record(model, promptTokens, cachedTokens, completionTokens, duration, ok.get());
                    log.debug("LLM stream done: model={}, prompt={}, cached={}, completion={}, {}ms",
                            model, promptTokens, cachedTokens, completionTokens, duration / 1_000_000);
                })
                .doOnError(e -> ok.set(false));
    }

    @Override
    public void close() {
        delegate.close();
    }

    // ---- 内部解析 ----

    /**
     * 从请求 JSON 提取 model 字段，用于指标 tag。
     */
    static String extractModelName(String body) {
        if (body == null || body.isBlank()) {
            return "unknown";
        }
        try {
            JsonNode node = mapper.readTree(body);
            JsonNode modelNode = node.get("model");
            return modelNode != null ? modelNode.asText("unknown") : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    static int parsePromptTokens(String body) {
        return parseField(body, "usage", "prompt_tokens");
    }

    static int parseCachedTokens(String body) {
        return parseField(body, "usage", "prompt_tokens_details", "cached_tokens");
    }

    static int parseCompletionTokens(String body) {
        return parseField(body, "usage", "completion_tokens");
    }

    private static int parseField(String body, String... path) {
        if (body == null || body.isBlank()) return 0;
        try {
            JsonNode node = mapper.readTree(body);
            for (String key : path) {
                node = node.get(key);
                if (node == null) return 0;
            }
            return node.asInt(0);
        } catch (Exception e) {
            return 0;
        }
    }
}
