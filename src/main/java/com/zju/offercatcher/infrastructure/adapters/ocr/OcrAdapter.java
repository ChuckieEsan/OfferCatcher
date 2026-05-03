package com.zju.offercatcher.infrastructure.adapters.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.infrastructure.config.OcrProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * OCR 适配器 — 调用 EasyOCR 微服务的 HTTP API
 * <p>
 * 对应 Python: app/infrastructure/adapters/ocr_adapter.py OCRAdapter
 */
@Service
public class OcrAdapter {

    private static final Logger log = LoggerFactory.getLogger(OcrAdapter.class);

    private final String serviceUrl;
    private final int connectTimeout;
    private final int readTimeout;
    private final ObjectMapper objectMapper;

    public OcrAdapter(OcrProperties properties, ObjectMapper objectMapper) {
        this.serviceUrl = properties.getServiceUrl();
        this.connectTimeout = properties.getConnectTimeout();
        this.readTimeout = properties.getReadTimeout();
        this.objectMapper = objectMapper;
    }

    /**
     * 识别单张图片中的文字
     */
    public String recognize(String imageSource) {
        return recognizeBatch(List.of(imageSource));
    }

    /**
     * 识别多张图片并返回合并文本
     */
    public String recognizeBatch(List<String> imageSources) {
        try {
            List<Map<String, String>> images = imageSources.stream()
                    .map(src -> Map.of("source", src))
                    .toList();
            Map<String, Object> body = Map.of("images", images);
            String json = objectMapper.writeValueAsString(body);

            URI uri = URI.create(serviceUrl + "/ocr");
            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setConnectTimeout(connectTimeout * 1000);
            conn.setReadTimeout(readTimeout * 1000);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            conn.getOutputStream().close();

            if (conn.getResponseCode() != 200) {
                String error = new String(conn.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                log.error("OCR service error: {}", error);
                throw new RuntimeException("OCR service returned error: " + error);
            }

            String responseBody = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Map<String, Object> response = objectMapper.readValue(responseBody, Map.class);
            String text = (String) response.get("text");
            int lineCount = response.get("line_count") instanceof Number n ? n.intValue() : 0;

            log.info("OCR recognized {} lines from {} images", lineCount, imageSources.size());
            return text != null ? text : "";

        } catch (Exception e) {
            log.error("OCR recognition failed: {}", e.getMessage());
            throw new RuntimeException("OCR failed: " + e.getMessage(), e);
        }
    }
}
