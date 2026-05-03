package com.zju.offercatcher.interfaces.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.infrastructure.adapters.asr.XfyunASRAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语音识别 WebSocket 端点
 * <p>
 * 接收前端音频数据，调用讯飞 ASR 进行实时语音转文字。
 * 对应 Python: app/api/routes/speech.py speech_websocket()
 * <p>
 * 消息格式：
 * - 接收: {"type": "start", "language": "zh_cn"}
 * - 接收: {"type": "audio", "data": "<base64 pcm>"}
 * - 接收: {"type": "end"}
 * - 发送: {"type": "started"}
 * - 发送: {"type": "result", "text": "...", "is_final": false}
 * - 发送: {"type": "error", "message": "..."}
 */
@Component
public class SpeechWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SpeechWebSocketHandler.class);

    private final XfyunASRAdapter asrAdapter;
    private final ObjectMapper objectMapper;
    private final Map<String, RecognitionSession> sessions = new ConcurrentHashMap<>();

    public SpeechWebSocketHandler(XfyunASRAdapter asrAdapter, ObjectMapper objectMapper) {
        this.asrAdapter = asrAdapter;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        log.info("Speech WebSocket connected: {}", session.getId());
        RecognitionSession rs = new RecognitionSession();
        sessions.put(session.getId(), rs);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        RecognitionSession rs = sessions.get(session.getId());
        if (rs == null) return;

        try {
            var data = objectMapper.readValue(message.getPayload(), Map.class);
            String type = (String) data.get("type");

            switch (type) {
                case "start" -> {
                    String language = (String) data.getOrDefault("language", "zh_cn");
                    rs.language = language;
                    rs.audioBuffer = new ByteArrayOutputStream();

                    sendJson(session, Map.of("type", "started"));
                    log.info("Speech recognition started: lang={}", language);
                }
                case "audio" -> {
                    String audioBase64 = (String) data.get("data");
                    if (audioBase64 != null && !audioBase64.isEmpty()) {
                        byte[] chunk = Base64.getDecoder().decode(audioBase64);
                        rs.audioBuffer.write(chunk);
                    }
                }
                case "end" -> {
                    if (!asrAdapter.isConfigured()) {
                        sendJson(session, Map.of("type", "result", "text", "", "is_final", true));
                        sendJson(session, Map.of("type", "info", "message", "ASR service not configured"));
                    } else if (rs.audioBuffer.size() > 0) {
                        byte[] audioData = rs.audioBuffer.toByteArray();
                        final String[] lastText = {""};
                        asrAdapter.recognizeStream(audioData, rs.language,
                                text -> {
                                    lastText[0] = text;
                                    sendJsonQuiet(session, Map.of("type", "result", "text", text, "is_final", false));
                                },
                                () -> sendJsonQuiet(session, Map.of("type", "result", "text",
                                        lastText[0], "is_final", true)),
                                error -> sendJsonQuiet(session, Map.of("type", "error", "message", error))
                        );
                    } else {
                        sendJson(session, Map.of("type", "result", "text", "", "is_final", true));
                    }
                    rs.audioBuffer = null;
                }
                default -> log.debug("Unknown speech message type: {}", type);
            }
        } catch (IOException | IllegalArgumentException e) {
            log.error("Error handling speech message: {}", e.getMessage());
            sendJsonQuiet(session, Map.of("type", "error", "message", e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        log.info("Speech WebSocket disconnected: {}", session.getId());
        sessions.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("Speech WebSocket transport error: {}", exception.getMessage());
        sessions.remove(session.getId());
    }

    // ==================== Helpers ====================

    private void sendJson(WebSocketSession session, Map<String, Object> data) throws IOException {
        session.sendMessage(new TextMessage(objectMapper.writeValueAsString(data)));
    }

    private void sendJsonQuiet(WebSocketSession session, Map<String, Object> data) {
        try {
            sendJson(session, data);
        } catch (IOException e) {
            log.warn("Failed to send WebSocket message: {}", e.getMessage());
        }
    }

    // ==================== Session State ====================

    private static class RecognitionSession {
        String language = "zh_cn";
        ByteArrayOutputStream audioBuffer;
    }

    private static class ByteArrayOutputStream extends java.io.ByteArrayOutputStream {
        // Re-export for clarity
    }
}
