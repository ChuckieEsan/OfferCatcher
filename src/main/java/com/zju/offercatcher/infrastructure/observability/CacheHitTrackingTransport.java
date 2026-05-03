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

import java.util.concurrent.atomic.AtomicReference;

/**
 * HttpTransport 装饰器，拦截 DeepSeek API 响应提取缓存命中数据。
 *
 * 非流式调用在 execute() 的 response body 中直接解析，
 * 流式调用在 stream() 的最后一个 SSE data chunk 中解析。
 * 解析失败不抛异常，不影响 Agent 主流程。
 */
public class CacheHitTrackingTransport implements HttpTransport {

    private static final Logger log = LoggerFactory.getLogger(CacheHitTrackingTransport.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final HttpTransport delegate;
    private final CacheHitMetrics metrics;
    private final String modelName;

    public CacheHitTrackingTransport(HttpTransport delegate, CacheHitMetrics metrics, String modelName) {
        this.delegate = delegate;
        this.metrics = metrics;
        this.modelName = modelName;
    }

    @Override
    public HttpResponse execute(HttpRequest request) throws HttpTransportException {
        HttpResponse response = delegate.execute(request);
        if (response.isSuccessful() && response.getBody() != null) {
            parseAndRecord(response.getBody());
        }
        return response;
    }

    @Override
    public Flux<String> stream(HttpRequest request) {
        AtomicReference<String> lastDataLine = new AtomicReference<>();

        return delegate.stream(request)
            .doOnNext(line -> {
                if (line.startsWith("data: ")) {
                    String data = line.substring(6).trim();
                    if (!"[DONE]".equals(data)) {
                        lastDataLine.set(data);
                    }
                }
            })
            .doOnComplete(() -> {
                String last = lastDataLine.get();
                if (last != null) {
                    parseAndRecord(last);
                }
            });
    }

    @Override
    public void close() {
        delegate.close();
    }

    private void parseAndRecord(String responseBody) {
        try {
            JsonNode root = mapper.readTree(responseBody);
            JsonNode usage = root.get("usage");
            if (usage == null) {
                return;
            }

            int promptTokens = usage.path("prompt_tokens").asInt(0);
            int cachedTokens = usage.at("/prompt_tokens_details/cached_tokens").asInt(0);

            if (promptTokens > 0) {
                metrics.record(modelName, promptTokens, cachedTokens);
                log.debug("LLM cache: model={}, prompt_tokens={}, cached_tokens={}, hit_rate={}%",
                    modelName, promptTokens, cachedTokens,
                    String.format("%.1f", 100.0 * cachedTokens / promptTokens));
            }
        } catch (Exception e) {
            log.debug("Failed to extract cache hit data: {}", e.getMessage());
        }
    }
}
