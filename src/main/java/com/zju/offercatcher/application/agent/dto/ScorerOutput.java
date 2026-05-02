package com.zju.offercatcher.application.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ScorerOutput(
    int score,
    @JsonProperty("mastery_level") String masteryLevel,
    List<String> strengths,
    List<String> improvements,
    String feedback
) {
    public static final ScorerOutput DEFAULT = new ScorerOutput(
        0, "LEVEL_0", List.of(), List.of(), "");
}
