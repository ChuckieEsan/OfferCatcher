package com.zju.offercatcher.application.agent.dto;

import java.util.Map;

public record PositionMappingOutput(
        Map<String, String> mappings
) {
    public static final PositionMappingOutput DEFAULT = new PositionMappingOutput(Map.of());
}
