package com.zju.offercatcher.interfaces.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Jackson 全局配置。
 *
 * 将 Long 序列化为字符串，防止 JavaScript Number 精度溢出。
 * JavaScript Number 安全整数上限为 2^53-1 (9007199254740991)，
 * Snowflake 64-bit ID 超过此范围会导致截断为相同值。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule longToStringModule() {
        SimpleModule module = new SimpleModule("LongToStringModule");
        module.addSerializer(Long.class, new JsonSerializer<>() {
            @Override
            public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
                gen.writeString(value.toString());
            }
        });
        module.addSerializer(Long.TYPE, new JsonSerializer<>() {
            @Override
            public void serialize(Long value, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
                gen.writeString(value.toString());
            }
        });
        return module;
    }
}
