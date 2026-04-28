package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.favorite.aggregates.Favorite;
import com.zju.offercatcher.domain.favorite.repositories.FavoriteRepository;
import com.zju.offercatcher.domain.shared.exception.FavoriteNotFoundException;
import com.zju.offercatcher.domain.shared.exception.UnauthorizedOperationException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Favorite Repository 实现
 */
@Repository
public class FavoriteRepositoryImpl implements FavoriteRepository {

    private final FavoriteJpaRepository jpaRepository;

    public FavoriteRepositoryImpl(FavoriteJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Favorite> findByUserId(String userId, int page, int size) {
        int offset = Math.max(0, page - 1) * size;
        return jpaRepository.findByUserIdPaginated(userId, size, offset)
            .stream()
            .map(FavoriteJpaEntity::toDomain)
            .toList();
    }

    @Override
    public Optional<Favorite> findByUserIdAndQuestionId(String userId, Long questionId) {
        return jpaRepository.findByUserIdAndQuestionId(userId, questionId)
            .map(FavoriteJpaEntity::toDomain);
    }

    @Override
    public Optional<Favorite> findById(Long favoriteId) {
        return jpaRepository.findByFavoriteId(favoriteId)
            .map(FavoriteJpaEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(Favorite favorite) {
        FavoriteJpaEntity entity = FavoriteJpaEntity.fromDomain(favorite);
        jpaRepository.save(entity);
    }

    @Override
    @Transactional
    public void deleteById(Long favoriteId, String userId) {
        Optional<FavoriteJpaEntity> entity = jpaRepository.findByFavoriteId(favoriteId);
        if (entity.isEmpty()) {
            throw new FavoriteNotFoundException(favoriteId);
        }
        if (!entity.get().getUserId().equals(userId)) {
            throw new UnauthorizedOperationException(userId, favoriteId.toString(), "delete favorite");
        }
        jpaRepository.delete(entity.get());
    }

    @Override
    public long countByUserId(String userId) {
        return jpaRepository.countByUserId(userId);
    }

    @Override
    public boolean existsByUserIdAndQuestionId(String userId, Long questionId) {
        return jpaRepository.existsByUserIdAndQuestionId(userId, questionId);
    }

    @Override
    @Transactional
    public void deleteByUserIdAndQuestionId(String userId, Long questionId) {
        jpaRepository.deleteByUserIdAndQuestionId(userId, questionId);
    }
}
