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

    }

    @Setter
    @Getter
    public static class OpenAI {
        private String apiKey = "";
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-4o";

    }

    @Setter
    @Getter
    public static class SiliconFlow {
        private String apiKey = "";
        private String baseUrl = "https://api.siliconflow.cn/v1";
        private String model = "Qwen/Qwen2.5-7B-Instruct";

    }

    @Setter
    @Getter
    public static class DashScope {
        private String apiKey = "";
        private String model = "qwen-plus";

    }
}
