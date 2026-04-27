package com.zju.offercatcher.interfaces.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * 收藏相关 DTO。
 */
public interface FavoriteDto {

    record CreateRequest(
        @NotBlank String questionId
    ) {}

    record Response(
        Long favoriteId,
        String userId,
        String questionId,
        String createdAt
    ) {}

    record ListResponse(
        List<Response> favorites
    ) {}

    record CheckRequest(
        @NotBlank List<String> questionIds
    ) {}

    record CheckResponse(
        java.util.Map<String, Boolean> favorited
    ) {}
}
