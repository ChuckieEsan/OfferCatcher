package com.zju.offercatcher.infrastructure.observability;

import com.zju.offercatcher.infrastructure.config.LLMProperties;
import com.zju.offercatcher.infrastructure.config.TelemetryProperties;
import io.agentscope.core.model.transport.HttpTransport;
import io.agentscope.core.model.transport.HttpTransportFactory;
import io.agentscope.core.tracing.TracerRegistry;
import io.agentscope.core.tracing.telemetry.TelemetryTracer;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenTelemetry 遥测配置。
 *
 * 两层可观测性：
 * 1. AgentScope TelemetryTracer — 全局自动追踪 Agent/Model/Tool 调用 → Langfuse
 * 2. Micrometer OTLP — 应用指标导出到 OpenTelemetry Collector
 *
 * TelemetryTracer 注册后自动捕获所有 Agent.call()、Model 调用、Tool 执行的 span，
 * 无需在 Agent 上单独配置 Hook。
 */
@Configuration
public class TelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConfig.class);

    private final TelemetryProperties properties;
    private final LLMProperties llmProperties;

    public TelemetryConfig(TelemetryProperties properties, LLMProperties llmProperties) {
        this.properties = properties;
        this.llmProperties = llmProperties;
    }

    /**
     * 注册 AgentScope TelemetryTracer，全局自动追踪。
     *
     * 对应官方 API：
     * TracerRegistry.register(
     *     TelemetryTracer.builder()
     *         .endpoint("https://...")
     *         .addHeader("Authorization", "Basic " + encoded)
     *         .addHeader("x-langfuse-ingestion-version", "4")
     *         .build()
     * );
     */
    @Bean
    @ConditionalOnProperty(name = "offercatcher.telemetry.enabled", havingValue = "true")
    public TelemetryTracer telemetryTracer() {
        String authHeader = properties.getLangfuseAuthHeader();
        String endpoint = properties.getLangfuseEndpoint();

        TelemetryTracer.Builder builder = TelemetryTracer.builder()
            .endpoint(endpoint);

        if (authHeader != null) {
            builder.addHeader("Authorization", "Basic " + authHeader);
            builder.addHeader("x-langfuse-ingestion-version", "4");
            log.info("Registering AgentScope TelemetryTracer → Langfuse: {}", endpoint);
        } else {
            log.info("Registering AgentScope TelemetryTracer → endpoint: {} (no auth)", endpoint);
        }

        TelemetryTracer tracer = builder.build();
        TracerRegistry.register(tracer);
        log.info("TelemetryTracer registered successfully");

        return tracer;
    }

    /**
     * Micrometer OTLP 指标导出。
     */
    @Bean
    @ConditionalOnProperty(name = "offercatcher.telemetry.metrics-enabled", havingValue = "true")
    public OtlpMeterRegistry otlpMeterRegistry() {
        String endpoint = properties.getOtlpEndpoint();
        log.info("OTLP metrics enabled, endpoint: {}", endpoint);

        OtlpConfig otlpConfig = new OtlpConfig() {
            @Override
            public String get(String key) {
                return null;
            }

            @Override
            public String url() {
                return endpoint;
            }
        };

        return new OtlpMeterRegistry(otlpConfig, io.micrometer.core.instrument.Clock.SYSTEM);
    }

    /**
     * 缓存命中指标记录器。
     *
     * 注入 MeterRegistry（Spring Boot Actuator 总会提供），
     * OTLP 启用时 OtlpMeterRegistry 会自动加入复合注册表。
     */
    @Bean
    @ConditionalOnProperty(name = "offercatcher.telemetry.cache-hit-tracking.enabled",
                        havingValue = "true", matchIfMissing = true)
    public CacheHitMetrics cacheHitMetrics(MeterRegistry meterRegistry) {
        return new CacheHitMetrics(meterRegistry);
    }

    /**
     * 缓存命中追踪 Transport，全局替换 HttpTransportFactory 默认实现。
     *
     * 所有 OpenAIChatModel 创建时通过 HttpTransportFactory.getDefault()
     * 自动获取此装饰器，13 个 Agent 无需修改。
     *
     * {@code @Configuration} 的 Bean 方法在 {@code @Service} 之前执行，
     * 确保 setDefault() 在 Agent 创建 OpenAIChatModel 之前生效。
     */
    @Bean
    @ConditionalOnProperty(name = "offercatcher.telemetry.cache-hit-tracking.enabled",
                        havingValue = "true", matchIfMissing = true)
    public CacheHitTrackingTransport cacheHitTrackingTransport(
            CacheHitMetrics cacheHitMetrics) {

        HttpTransport defaultTransport = HttpTransportFactory.getDefault();
        String modelName = llmProperties.getDeepseek().getModel();

        CacheHitTrackingTransport wrapped =
            new CacheHitTrackingTransport(defaultTransport, cacheHitMetrics, modelName);

        HttpTransportFactory.setDefault(wrapped);

        log.info("CacheHitTrackingTransport registered for model: {} (wrapping {})",
            modelName, defaultTransport.getClass().getSimpleName());
        return wrapped;
    }
}
