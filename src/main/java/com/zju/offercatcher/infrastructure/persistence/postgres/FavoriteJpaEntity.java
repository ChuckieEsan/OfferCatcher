package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.favorite.aggregates.Favorite;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Favorite JPA 实体
 */
@Entity
@Table(name = "favorites", indexes = {
    @Index(name = "idx_favorites_user_created", columnList = "user_id, created_at"),
    @Index(name = "idx_favorites_user_question", columnList = "user_id, question_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
public class FavoriteJpaEntity {

    @Id
    @Column(name = "favorite_id")
    private Long favoriteId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static FavoriteJpaEntity fromDomain(Favorite favorite) {
        FavoriteJpaEntity entity = new FavoriteJpaEntity();
        entity.setFavoriteId(favorite.getFavoriteId());
        entity.setUserId(favorite.getUserId());
        entity.setQuestionId(favorite.getQuestionId());
        entity.setCreatedAt(favorite.getCreatedAt());
        return entity;
    }

    public Favorite toDomain() {
        return Favorite.rebuild(favoriteId, userId, questionId, createdAt);
    }
}
