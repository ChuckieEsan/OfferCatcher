package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zju.offercatcher.domain.memory.entities.MemoryReference;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MemoryReference JSON 转换器
 */
@Converter
public class MemoryReferenceConverter implements AttributeConverter<List<MemoryReference>, String> {

    private static final Logger log = LoggerFactory.getLogger(MemoryReferenceConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<MemoryReference> references) {
        if (references == null || references.isEmpty()) {
            return "[]";
        }
        try {
            List<Map<String, Object>> referenceData = references.stream()
                    .map(this::referenceToMap)
                    .toList();
            return objectMapper.writeValueAsString(referenceData);
        } catch (JsonProcessingException e) {
            log.error("Failed to convert MemoryReference to JSON", e);
            throw new RuntimeException("Failed to convert MemoryReference to JSON", e);
        }
    }

    @Override
    public List<MemoryReference> convertToEntityAttribute(String json) {
        if (json == null || json.isBlank() || json.equals("[]")) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, Object>> referenceData = objectMapper.readValue(json,
                    new TypeReference<List<Map<String, Object>>>() {
                    });
            return referenceData.stream()
                    .map(this::mapToReference)
                    .toList();
        } catch (JsonProcessingException e) {
            log.error("Failed to convert JSON to MemoryReference", e);
            throw new RuntimeException("Failed to convert JSON to MemoryReference", e);
        }
    }

    private Map<String, Object> referenceToMap(MemoryReference ref) {
        Map<String, Object> map = new java.util.LinkedHashMap<>();
        map.put("referenceName", ref.getReferenceName());
        map.put("content", ref.getContent());
        map.put("updatedAt", ref.getUpdatedAt().toString());
        return map;
    }

    private MemoryReference mapToReference(Map<String, Object> map) {
        String updatedAtStr = (String) map.get("updatedAt");
        LocalDateTime updatedAt = updatedAtStr != null ? LocalDateTime.parse(updatedAtStr) : LocalDateTime.now();
        return MemoryReference.rebuild(
                (String) map.get("referenceName"),
                (String) map.get("content"),
                updatedAt
        );
    }
}