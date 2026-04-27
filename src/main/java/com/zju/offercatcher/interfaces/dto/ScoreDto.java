package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

public final class ScoreDto {

    private ScoreDto() {}

    public record ScoreRequest(
        @NotBlank String questionId,
        @NotBlank String userAnswer
    ) {}
}
