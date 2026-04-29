package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.telemetry")
public class TelemetryProperties {

    private boolean enabled = false;
    private String otlpEndpoint = "http://localhost:4317";

    /** Langfuse OTLP endpoint (cloud or self-hosted). */
    private String langfuseEndpoint = "https://cloud.langfuse.com/api/public/otel/v1/traces";
    private String langfusePublicKey;
    private String langfuseSecretKey;

    public String getLangfuseAuthHeader() {
        if (langfusePublicKey == null || langfuseSecretKey == null
            || langfusePublicKey.isBlank() || langfuseSecretKey.isBlank()) {
            return null;
        }
        return java.util.Base64.getEncoder()
            .encodeToString((langfusePublicKey + ":" + langfuseSecretKey).getBytes());
    }
}
