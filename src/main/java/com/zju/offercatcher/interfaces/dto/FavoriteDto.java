package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * 收藏相关 DTO。
 */
public interface FavoriteDto {

    record CreateRequest(
        @jakarta.validation.constraints.NotNull Long questionId
    ) {}

    record Response(
        Long favoriteId,
        String userId,
        Long questionId,
        String createdAt
    ) {}

    record ListResponse(
        List<Response> favorites
    ) {}

    record CheckRequest(
        @NotEmpty List<Long> questionIds
    ) {}

    record CheckResponse(
        java.util.Map<Long, Boolean> favorited
    ) {}
}
