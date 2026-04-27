package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.telemetry")
public class TelemetryProperties {

    private String otlpEndpoint = "http://localhost:4317";
    private boolean enabled = false;

}
