package com.zju.offercatcher.application.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 模板加载器
 *
 * 从 classpath:prompts/ 加载 Markdown 提示词模板。
 * 支持 Jinja2 {{ variable }} 风格的变量替换。
 * 对应 Python: app/infrastructure/common/prompt.py
 */
@Component
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);
    private static final String PROMPTS_PATH = "prompts/";

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String load(String name) {
        return cache.computeIfAbsent(name, this::doLoad);
    }

    public String render(String name, Map<String, String> vars) {
        String template = load(name);
        for (var entry : vars.entrySet()) {
            template = template.replace("{{ " + entry.getKey() + " }}", entry.getValue());
            template = template.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return template;
    }

    public String render(String name, Object... kvPairs) {
        String template = load(name);
        for (int i = 0; i < kvPairs.length; i += 2) {
            String key = kvPairs[i].toString();
            String value = kvPairs[i + 1] != null ? kvPairs[i + 1].toString() : "";
            template = template.replace("{{ " + key + " }}", value);
            template = template.replace("{{" + key + "}}", value);
        }
        return template;
    }

    private String doLoad(String name) {
        try {
            String path = PROMPTS_PATH + name;
            var resource = new ClassPathResource(path);
            String content = resource.getContentAsString(StandardCharsets.UTF_8);
            log.debug("Loaded prompt: {}", path);
            return content;
        } catch (IOException e) {
            log.error("Failed to load prompt: {}", name, e);
            return "";
        }
    }

    public void clearCache() {
        cache.clear();
    }
}
