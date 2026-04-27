package com.zju.offercatcher.infrastructure.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelConfig {

    private static final Logger log = LoggerFactory.getLogger(ModelConfig.class);

    @Bean
    public OpenAIClient deepSeekClient(LLMProperties properties) {
        LLMProperties.DeepSeek cfg = properties.getDeepseek();
        log.info("DeepSeek client configured: baseUrl={}, model={}", cfg.getBaseUrl(), cfg.getModel());
        return OpenAIOkHttpClient.builder()
            .apiKey(cfg.getApiKey())
            .baseUrl(cfg.getBaseUrl())
            .build();
    }

    @Bean
    public OpenAIClient openAIClient(LLMProperties properties) {
        LLMProperties.OpenAI cfg = properties.getOpenai();
        log.info("OpenAI client configured: model={}", cfg.getModel());
        return OpenAIOkHttpClient.builder()
            .apiKey(cfg.getApiKey())
            .baseUrl(cfg.getBaseUrl())
            .build();
    }

    @Bean
    public OpenAIClient siliconFlowClient(LLMProperties properties) {
        LLMProperties.SiliconFlow cfg = properties.getSiliconflow();
        log.info("SiliconFlow client configured: baseUrl={}, model={}", cfg.getBaseUrl(), cfg.getModel());
        return OpenAIOkHttpClient.builder()
            .apiKey(cfg.getApiKey())
            .baseUrl(cfg.getBaseUrl())
            .build();
    }
}
