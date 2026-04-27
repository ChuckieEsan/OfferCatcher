package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 记忆相关 DTO。
 */
public interface MemoryDto {

    record Response(
        String userId,
        String content,
        String preferences,
        String behaviors
    ) {}

    record UpdatePreferencesRequest(
        @NotBlank @Size(max = 5000) String content
    ) {}

    record UpdateBehaviorsRequest(
        @NotBlank @Size(max = 5000) String content
    ) {}
}
