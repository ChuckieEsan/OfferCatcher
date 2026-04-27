package com.zju.offercatcher.infrastructure.observability;

import com.zju.offercatcher.infrastructure.config.TelemetryProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
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
 * 当 offercatcher.telemetry.enabled=true 时，通过 OTLP 协议
 * 将 Micrometer 指标导出到 OpenTelemetry Collector。
 */
@Configuration
public class TelemetryConfig {

    private static final Logger log = LoggerFactory.getLogger(TelemetryConfig.class);

    private final TelemetryProperties telemetryProperties;

    public TelemetryConfig(TelemetryProperties telemetryProperties) {
        this.telemetryProperties = telemetryProperties;
    }

    @Bean
    @ConditionalOnProperty(name = "offercatcher.telemetry.enabled", havingValue = "true")
    public OtlpMeterRegistry otlpMeterRegistry() {
        String endpoint = telemetryProperties.getOtlpEndpoint();
        log.info("OTLP telemetry enabled, endpoint: {}", endpoint);

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
}
