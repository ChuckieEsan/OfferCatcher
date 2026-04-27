package com.zju.offercatcher.interfaces.controller;

import com.zju.offercatcher.application.service.FavoriteApplicationService;
import com.zju.offercatcher.domain.favorite.aggregates.Favorite;
import com.zju.offercatcher.interfaces.config.UserId;
import com.zju.offercatcher.interfaces.dto.FavoriteDto.*;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/favorites")
public class FavoriteController {

    private static final Logger log = LoggerFactory.getLogger(FavoriteController.class);

    private final FavoriteApplicationService favoriteService;

    public FavoriteController(FavoriteApplicationService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @PostMapping
    public ResponseEntity<Response> addFavorite(@UserId String userId, @Valid @RequestBody CreateRequest req) {
        Favorite f = favoriteService.addFavorite(userId, req.questionId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(f));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removeFavorite(@UserId String userId, @PathVariable Long id) {
        favoriteService.removeFavorite(id, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/by-question/{questionId}")
    public ResponseEntity<Void> removeByQuestionId(@UserId String userId, @PathVariable String questionId) {
        favoriteService.removeFavoriteByQuestionId(userId, questionId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<ListResponse> listFavorites(
        @UserId String userId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int pageSize) {
        List<Favorite> favorites = favoriteService.listFavorites(userId, page, pageSize);
        return ResponseEntity.ok(new ListResponse(favorites.stream().map(FavoriteController::toResponse).toList()));
    }

    @PostMapping("/check")
    public ResponseEntity<CheckResponse> checkFavorited(
        @UserId String userId, @Valid @RequestBody CheckRequest req) {
        Map<String, Boolean> result = favoriteService.checkFavorited(userId, req.questionIds());
        return ResponseEntity.ok(new CheckResponse(result));
    }

    // ==================== Mappers ====================

    static Response toResponse(Favorite f) {
        return new Response(f.getFavoriteId(), f.getUserId(), f.getQuestionId(),
            f.getCreatedAt().toString());
    }
}
