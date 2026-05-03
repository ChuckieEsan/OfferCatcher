package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.telemetry")
public class TelemetryProperties {

    private boolean enabled = false;

    /** 独立的 OTLP metrics 开关。Jaeger 不支持 metrics 摄取，默认关闭。 */
    private boolean metricsEnabled = false;

    private String otlpEndpoint = "http://localhost:4318/v1/metrics";

    /** Langfuse OTLP endpoint (cloud or self-hosted). */
    private String langfuseEndpoint = "https://cloud.langfuse.com/api/public/otel/v1/traces";
    private String langfusePublicKey;
    private String langfuseSecretKey;

    /** 缓存命中追踪开关。默认开启，仅当 OTLP metrics 启用时才导出。 */
    private CacheHitTracking cacheHitTracking = new CacheHitTracking();

    @Setter
    @Getter
    public static class CacheHitTracking {
        private boolean enabled = true;
    }

    public String getLangfuseAuthHeader() {
        if (langfusePublicKey == null || langfuseSecretKey == null
            || langfusePublicKey.isBlank() || langfuseSecretKey.isBlank()) {
            return null;
        }
        return java.util.Base64.getEncoder()
            .encodeToString((langfusePublicKey + ":" + langfuseSecretKey).getBytes());
    }
}
