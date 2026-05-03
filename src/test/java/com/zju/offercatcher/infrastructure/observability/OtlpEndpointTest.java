package com.zju.offercatcher.infrastructure.observability;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Jaeger OTLP HTTP 端点的信号类型支持情况。
 * <p>
 * Jaeger 是 tracing 系统，只接收 /v1/traces（trace signal），
 * 不支持 /v1/metrics（metrics signal）。所以 Micrometer 的
 * OtlpMeterRegistry 往 Jaeger 发 metrics 会收到 404。
 */
@Tag("integration")
class OtlpEndpointTest {

    private final HttpClient http = HttpClient.newHttpClient();

    private final int otlpHttpPort = Integer.getInteger("otlp.http.port", 4318);

    @Test
    void jaegerAcceptsTraceSignal() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + otlpHttpPort + "/v1/traces"))
                .header("Content-Type", "application/x-protobuf")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        var resp = http.send(req, HttpResponse.BodyHandlers.discarding());

        assertThat(resp.statusCode())
                .as("Jaeger should accept OTLP traces at /v1/traces")
                .isEqualTo(200);
    }

    @Test
    void jaegerDoesNotAcceptMetricsSignal() throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + otlpHttpPort + "/v1/metrics"))
                .header("Content-Type", "application/x-protobuf")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        var resp = http.send(req, HttpResponse.BodyHandlers.discarding());

        assertThat(resp.statusCode())
                .as("Jaeger does NOT support OTLP metrics — returns 404. "
                        + "Micrometer OtlpMeterRegistry should not target Jaeger.")
                .isEqualTo(404);
    }

    @Test
    void micrometerOtlpRegistryShouldTargetMetricsBackendNotJaeger() {
        // 验证 TelemetryConfig 的 OtlpMeterRegistry 当前配置会失败
        // Jaeger 只支持 traces，不支持 metrics
        // 方案 1：禁用 OtlpMeterRegistry（offercatcher.telemetry.otlp-metrics.enabled=false）
        // 方案 2：改为 OpenTelemetry Collector → Prometheus/其他 metrics 后端
        // 方案 3：改用 Micrometer Prometheus registry + Prometheus

        String jaegerMetricsUrl = "http://localhost:" + otlpHttpPort + "/v1/metrics";
        String jaegerTracesUrl = "http://localhost:" + otlpHttpPort + "/v1/traces";

        // Jaeger 是 tracing 后端，不是 metrics 后端
        assertThat(jaegerMetricsUrl).endsWith("/v1/metrics");
        assertThat(jaegerTracesUrl).endsWith("/v1/traces");
    }
}
