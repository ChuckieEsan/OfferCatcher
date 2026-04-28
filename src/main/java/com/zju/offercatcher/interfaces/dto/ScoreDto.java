package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class ScoreDto {

    private ScoreDto() {}

    public record ScoreRequest(
        @NotNull Long questionId,
        @NotBlank String userAnswer
    ) {}
}
