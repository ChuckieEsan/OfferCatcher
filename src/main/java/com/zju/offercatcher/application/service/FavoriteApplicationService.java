package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.favorite.aggregates.Favorite;
import com.zju.offercatcher.domain.favorite.repositories.FavoriteRepository;
import com.zju.offercatcher.domain.shared.exception.FavoriteNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 收藏应用服务。
 */
@Service
public class FavoriteApplicationService {

    private static final Logger log = LoggerFactory.getLogger(FavoriteApplicationService.class);

    private final FavoriteRepository favoriteRepository;

    public FavoriteApplicationService(FavoriteRepository favoriteRepository) {
        this.favoriteRepository = favoriteRepository;
    }

    @Transactional
    public Favorite addFavorite(String userId, Long questionId) {
        Optional<Favorite> existing = favoriteRepository.findByUserIdAndQuestionId(userId, questionId);
        if (existing.isPresent()) {
            return existing.get();
        }
        Favorite favorite = Favorite.create(userId, questionId);
        favoriteRepository.save(favorite);
        log.info("Favorite added: userId={}, questionId={}", userId, questionId);
        return favorite;
    }

    @Transactional
    public void removeFavorite(Long favoriteId, String userId) {
        Favorite favorite = favoriteRepository.findById(favoriteId)
                .orElseThrow(() -> new FavoriteNotFoundException(favoriteId));
        favoriteRepository.deleteById(favoriteId, userId);
        log.info("Favorite removed: {}", favoriteId);
    }

    @Transactional
    public void removeFavoriteByQuestionId(String userId, Long questionId) {
        favoriteRepository.deleteByUserIdAndQuestionId(userId, questionId);
        log.info("Favorite removed: userId={}, questionId={}", userId, questionId);
    }

    public List<Favorite> listFavorites(String userId, int page, int pageSize) {
        return favoriteRepository.findByUserId(userId, page, pageSize);
    }

    public Map<Long, Boolean> checkFavorited(String userId, List<Long> questionIds) {
        return questionIds.stream()
                .collect(Collectors.toMap(
                        id -> id,
                        id -> favoriteRepository.existsByUserIdAndQuestionId(userId, id),
                        (a, b) -> a
                ));
    }

    public boolean isFavorited(String userId, Long questionId) {
        return favoriteRepository.existsByUserIdAndQuestionId(userId, questionId);
    }
}
