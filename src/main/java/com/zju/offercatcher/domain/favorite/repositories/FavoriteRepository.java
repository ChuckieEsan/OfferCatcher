package com.zju.offercatcher.domain.favorite.repositories;

import com.zju.offercatcher.domain.favorite.aggregates.Favorite;

import java.util.List;
import java.util.Optional;

/**
 * 收藏仓储接口
 */
public interface FavoriteRepository {

    List<Favorite> findByUserId(String userId, int page, int size);

    Optional<Favorite> findByUserIdAndQuestionId(String userId, String questionId);

    Optional<Favorite> findById(Long favoriteId);

    void save(Favorite favorite);

    /**
     * @throws com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException 如果用户不是所有者
     */
    void deleteById(Long favoriteId, String userId);

    long countByUserId(String userId);

    boolean existsByUserIdAndQuestionId(String userId, String questionId);

    void deleteByUserIdAndQuestionId(String userId, String questionId);
}
