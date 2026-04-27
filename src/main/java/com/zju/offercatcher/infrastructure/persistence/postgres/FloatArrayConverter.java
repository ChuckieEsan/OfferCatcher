package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.List;

/**
 * float[] 向量转换器
 *
 * 将 float[] 转换为 JSON 字符串存储。
 */
@Converter
public class FloatArrayConverter implements AttributeConverter<float[], String> {

    private static final Logger log = LoggerFactory.getLogger(FloatArrayConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert float[] to JSON", e);
            throw new RuntimeException("Failed to convert float[] to JSON", e);
        }
    }

    @Override
    public float[] convertToEntityAttribute(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            List<Float> floatList = objectMapper.readValue(json, new TypeReference<List<Float>>() {});
            float[] result = new float[floatList.size()];
            for (int i = 0; i < floatList.size(); i++) {
                result[i] = floatList.get(i);
            }
            return result;
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to float[]", e);
            throw new RuntimeException("Failed to convert JSON to float[]", e);
        }
    }
}