package com.zju.offercatcher.infrastructure.adapters.asr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.infrastructure.config.ASRProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 讯飞语音识别适配器
 * <p>
 * 封装讯飞语音听写 WebSocket API，提供实时语音转文字能力。
 * 对应 Python: app/infrastructure/adapters/asr_adapter.py XfyunASRAdapter
 * <p>
 * 支持：
 * - 流式识别（逐帧发送音频，逐句返回文本）
 * - 单次识别（完整音频一次性发送）
 * - HMAC-SHA256 签名认证
 */
@Service
public class XfyunASRAdapter {

    private static final Logger log = LoggerFactory.getLogger(XfyunASRAdapter.class);

    private static final String WS_URL = "wss://iat-api.xfyun.cn/v2/iat";
    private static final String HOST = "iat-api.xfyun.cn";
    private static final String DATE_PATTERN = "EEE, dd MMM yyyy HH:mm:ss 'GMT'";
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern(DATE_PATTERN, java.util.Locale.ENGLISH);

    private final String appId;
    private final String apiKey;
    private final String apiSecret;
    private final ObjectMapper objectMapper;

    public XfyunASRAdapter(ASRProperties properties, ObjectMapper objectMapper) {
        this.appId = properties.getAppId();
        this.apiKey = properties.getApiKey();
        this.apiSecret = properties.getApiSecret();
        this.objectMapper = objectMapper;

        if (appId.isBlank() || apiKey.isBlank() || apiSecret.isBlank()) {
            log.warn("Xfyun credentials not configured, ASR will not work");
        }
    }

    public boolean isConfigured() {
        return !appId.isBlank() && !apiKey.isBlank() && !apiSecret.isBlank();
    }

    // ==================== 单次识别 ====================

    /**
     * 单次语音识别
     *
     * @param audioData 完整音频数据（PCM 16kHz 16bit mono）
     * @return 识别文本
     */
    public String recognize(byte[] audioData) {
        return recognize(audioData, "zh_cn");
    }

    public String recognize(byte[] audioData, String language) {
        var result = new CompletableFuture<String>();
        var textBuilder = new StringBuilder();

        recognizeStream(audioData, language,
                text -> textBuilder.replace(0, textBuilder.length(), text),
                () -> result.complete(textBuilder.toString()),
                error -> result.completeExceptionally(new RuntimeException(error))
        );

        try {
            return result.get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("ASR recognition failed: {}", e.getMessage());
            throw new RuntimeException("ASR failed: " + e.getMessage(), e);
        }
    }

    // ==================== 流式识别 ====================

    /**
     * 流式语音识别
     *
     * @param audioData 完整音频数据
     * @param language  语言代码（zh_cn / en_us）
     * @param onPartial 中间结果回调
     * @param onFinal   最终结果回调
     * @param onError   错误回调
     */
    public void recognizeStream(byte[] audioData, String language,
                                Consumer<String> onPartial,
                                Runnable onFinal,
                                Consumer<String> onError) {
        if (!isConfigured()) {
            onError.accept("Xfyun credentials not configured");
            return;
        }

        try {
            String wsUrl = buildSignedUrl();
            HttpClient client = HttpClient.newHttpClient();

            class Session {
                Map<Integer, String> sentenceResults = new HashMap<>();
                String finalText = "";
            }
            Session session = new Session();

            WebSocket ws = client.newWebSocketBuilder()
                    .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                            try {
                                @SuppressWarnings("unchecked")
                                var msg = objectMapper.readValue(data.toString(), Map.class);
                                int code = msg.get("code") instanceof Number n ? n.intValue() : 0;

                                if (code != 0) {
                                    String errMsg = (String) msg.getOrDefault("message", "Unknown error");
                                    log.error("Xfyun ASR error: code={}, message={}", code, errMsg);
                                    onError.accept(errMsg);
                                    return null;
                                }

                                @SuppressWarnings("unchecked")
                                var resultData = (Map<String, Object>) msg.getOrDefault("data", Map.of());
                                int status = resultData.get("status") instanceof Number n ? n.intValue() : 2;

                                @SuppressWarnings("unchecked")
                                var result = (Map<String, Object>) resultData.getOrDefault("result", Map.of());

                                @SuppressWarnings("unchecked")
                                var wsList = (List<Map<String, Object>>) result.getOrDefault("ws", List.of());
                                int sn = result.get("sn") instanceof Number n ? n.intValue() : 1;
                                String pgs = (String) result.get("pgs");
                                var rg = (List<Integer>) result.get("rg");

                                // Handle replacement
                                if ("rpl".equals(pgs) && rg != null && rg.size() >= 2) {
                                    int start = rg.get(0), end = rg.get(1);
                                    for (int i = start; i <= end; i++) {
                                        session.sentenceResults.remove(i);
                                    }
                                }

                                // Collect current text
                                StringBuilder text = new StringBuilder();
                                for (var wsItem : wsList) {
                                    @SuppressWarnings("unchecked")
                                    var cwList = (List<Map<String, Object>>) wsItem.getOrDefault("cw", List.of());
                                    for (var cw : cwList) {
                                        text.append((String) cw.getOrDefault("w", ""));
                                    }
                                }
                                if (!text.isEmpty()) {
                                    session.sentenceResults.put(sn, text.toString());
                                }

                                // Reconstruct full text from ordered sentences
                                StringBuilder fullText = new StringBuilder();
                                session.sentenceResults.keySet().stream().sorted()
                                        .forEach(k -> fullText.append(session.sentenceResults.get(k)));

                                if (!fullText.isEmpty()) {
                                    onPartial.accept(fullText.toString());
                                }

                                if (status == 2) { // Final
                                    session.finalText = fullText.toString();
                                    onFinal.run();
                                }
                            } catch (Exception e) {
                                log.error("Error parsing ASR result: {}", e.getMessage());
                            }
                            return null;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            log.error("WebSocket error: {}", error.getMessage());
                            onError.accept(error.getMessage());
                        }
                    }).get(10, TimeUnit.SECONDS);

            // Send start frame
            Map<String, Object> business = new LinkedHashMap<>();
            business.put("language", language);
            business.put("domain", "iat");
            business.put("accent", "mandarin");
            business.put("vad_eos", 2000);
            business.put("dwa", "wpgs");
            business.put("ptt", 1);

            Map<String, Object> startFrame = new LinkedHashMap<>();
            startFrame.put("common", Map.of("app_id", appId));
            startFrame.put("business", business);
            startFrame.put("data", Map.of(
                    "status", 0,
                    "format", "audio/L16;rate=16000",
                    "encoding", "raw",
                    "audio", ""
            ));
            ws.sendText(objectMapper.writeValueAsString(startFrame), true);

            // Send audio chunks
            int chunkSize = 1280; // 40ms at 16kHz 16bit mono
            for (int i = 0; i < audioData.length; i += chunkSize) {
                int end = Math.min(i + chunkSize, audioData.length);
                byte[] chunk = Arrays.copyOfRange(audioData, i, end);
                String audioB64 = Base64.getEncoder().encodeToString(chunk);

                Map<String, Object> audioFrame = new LinkedHashMap<>();
                audioFrame.put("data", Map.of(
                        "status", 1,
                        "format", "audio/L16;rate=16000",
                        "encoding", "raw",
                        "audio", audioB64
                ));
                ws.sendText(objectMapper.writeValueAsString(audioFrame), true);
                Thread.sleep(40);
            }

            // Send end frame
            Map<String, Object> endFrame = new LinkedHashMap<>();
            endFrame.put("data", Map.of(
                    "status", 2,
                    "format", "audio/L16;rate=16000",
                    "encoding", "raw",
                    "audio", ""
            ));
            ws.sendText(objectMapper.writeValueAsString(endFrame), true);

            // Wait for completion
            Thread.sleep(2000);
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "");

        } catch (Exception e) {
            log.error("ASR stream error: {}", e.getMessage());
            onError.accept(e.getMessage());
        }
    }

    // ==================== 签名生成 ====================

    private String buildSignedUrl() throws Exception {
        String date = DATE_FORMATTER.format(ZonedDateTime.now(ZoneOffset.UTC));
        String signatureOrigin = "host: " + HOST + "\ndate: " + date + "\nGET /v2/iat HTTP/1.1";

        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signatureSha = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
        String signatureB64 = Base64.getEncoder().encodeToString(signatureSha);

        String authorizationOrigin = String.format(
                "api_key=\"%s\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"%s\"",
                apiKey, signatureB64
        );
        String authorization = Base64.getEncoder().encodeToString(
                authorizationOrigin.getBytes(StandardCharsets.UTF_8));

        return WS_URL + "?authorization=" + URLEncoder.encode(authorization, StandardCharsets.UTF_8)
                + "&date=" + URLEncoder.encode(date, StandardCharsets.UTF_8)
                + "&host=" + URLEncoder.encode(HOST, StandardCharsets.UTF_8);
    }
}
