package com.zju.offercatcher.interfaces.config;

import com.zju.offercatcher.interfaces.websocket.SpeechWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SpeechWebSocketHandler speechHandler;

    public WebSocketConfig(SpeechWebSocketHandler speechHandler) {
        this.speechHandler = speechHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(speechHandler, "/ws/speech").setAllowedOrigins("*");
    }
}
