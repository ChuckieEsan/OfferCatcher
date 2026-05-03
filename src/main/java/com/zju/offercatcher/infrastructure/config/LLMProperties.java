package com.zju.offercatcher.infrastructure.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Setter
@Getter
@ConfigurationProperties(prefix = "offercatcher.llm")
public class LLMProperties {

    private String defaultProvider = "deepseek";
    private DeepSeek deepseek = new DeepSeek();
    private OpenAI openai = new OpenAI();
    private SiliconFlow siliconflow = new SiliconFlow();
    private DashScope dashscope = new DashScope();

    @Setter
    @Getter
    public static class DeepSeek {
        private String apiKey = "";
        private String baseUrl = "https://api.deepseek.com/v1";
        private String model = "deepseek-chat";
        private String modelSimple = "deepseek-v4-flash";
        private String modelComplex = "deepseek-v4-pro";
    }

    @Setter
    @Getter
    public static class OpenAI {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o";
        private String modelSimple = "gpt-4o-mini";
        private String modelComplex = "gpt-4o";
    }

    @Setter
    @Getter
    public static class SiliconFlow {
        private String apiKey = "";
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String model = "Qwen/Qwen2.5-7B-Instruct";
        private String modelSimple = "Qwen/Qwen2.5-7B-Instruct";
        private String modelComplex = "Qwen/Qwen2.5-72B-Instruct";
    }

    @Setter
    @Getter
    public static class DashScope {
        private String apiKey = "";
        private String model = "qwen-plus";
        private String modelSimple = "qwen-plus";
        private String modelComplex = "qwen-max";
    }

    /**
     * 统一 Provider 视图，所有 LLM 提供商都用此结构。
     */
    public record Provider(String apiKey, String baseUrl, String model) {
    }

    /**
     * 默认 model。
     */
    public Provider getProvider(String key) {
        return buildProvider(key, null);
    }

    /**
     * 简单任务（flash / mini）。
     */
    public Provider getSimple(String key) {
        return buildProvider(key, "simple");
    }

    /**
     * 复杂任务（pro / max）。
     */
    public Provider getComplex(String key) {
        return buildProvider(key, "complex");
    }

    /**
     * 手动指定 model 名，覆盖配置。
     */
    public Provider getProvider(String key, String model) {
        return buildProvider(key, model);
    }

    private Provider buildProvider(String key, String modelName) {
        return switch (key.toLowerCase()) {
            case "deepseek" -> new Provider(deepseek.getApiKey(), deepseek.getBaseUrl(),
                    effectiveModel(deepseek.getModel(), deepseek.getModelSimple(), deepseek.getModelComplex(), modelName));
            case "openai" -> new Provider(openai.getApiKey(), openai.getBaseUrl(),
                    effectiveModel(openai.getModel(), openai.getModelSimple(), openai.getModelComplex(), modelName));
            case "siliconflow" -> new Provider(siliconflow.getApiKey(), siliconflow.getBaseUrl(),
                    effectiveModel(siliconflow.getModel(), siliconflow.getModelSimple(), siliconflow.getModelComplex(), modelName));
            case "dashscope" -> new Provider(dashscope.getApiKey(), "",
                    effectiveModel(dashscope.getModel(), dashscope.getModelSimple(), dashscope.getModelComplex(), modelName));
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + key);
        };
    }

    /**
     * modelName 为 null → 默认；"simple"/"complex" → 对应预设；否则直接用作 model 名。
     */
    private static String effectiveModel(String defaultModel, String simple, String complex, String modelName) {
        if (modelName == null) return defaultModel;
        return switch (modelName) {
            case "simple" -> simple;
            case "complex" -> complex;
            default -> modelName;
        };
    }
}
