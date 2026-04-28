package com.zju.offercatcher.infrastructure.persistence.postgres;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Favorite JPA Repository
 */
@Repository
public interface FavoriteJpaRepository extends JpaRepository<FavoriteJpaEntity, Long> {

    @Query("SELECT f FROM FavoriteJpaEntity f WHERE f.favoriteId = :favoriteId")
    Optional<FavoriteJpaEntity> findByFavoriteId(@Param("favoriteId") Long favoriteId);

    @Query("SELECT f FROM FavoriteJpaEntity f WHERE f.userId = :userId ORDER BY f.createdAt DESC LIMIT :limit OFFSET :offset")
    List<FavoriteJpaEntity> findByUserIdPaginated(@Param("userId") String userId, @Param("limit") int limit, @Param("offset") int offset);

    @Query("SELECT f FROM FavoriteJpaEntity f WHERE f.userId = :userId AND f.questionId = :questionId")
    Optional<FavoriteJpaEntity> findByUserIdAndQuestionId(@Param("userId") String userId, @Param("questionId") Long questionId);

    @Query("SELECT COUNT(f) FROM FavoriteJpaEntity f WHERE f.userId = :userId")
    long countByUserId(@Param("userId") String userId);

    @Query("SELECT CASE WHEN COUNT(f) > 0 THEN true ELSE false END FROM FavoriteJpaEntity f WHERE f.userId = :userId AND f.questionId = :questionId")
    boolean existsByUserIdAndQuestionId(@Param("userId") String userId, @Param("questionId") Long questionId);

    @Modifying
    @Query("DELETE FROM FavoriteJpaEntity f WHERE f.favoriteId = :favoriteId AND f.userId = :userId")
    int deleteByFavoriteIdAndUserId(@Param("favoriteId") Long favoriteId, @Param("userId") String userId);

    @Modifying
    @Query("DELETE FROM FavoriteJpaEntity f WHERE f.userId = :userId AND f.questionId = :questionId")
    int deleteByUserIdAndQuestionId(@Param("userId") String userId, @Param("questionId") Long questionId);
}
