package com.zju.offercatcher.domain.shared.exception;

/**
 * 收藏未找到异常
 */
public class FavoriteNotFoundException extends DomainException {

    private final Long favoriteId;

    public FavoriteNotFoundException(Long favoriteId) {
        super("收藏记录不存在: " + favoriteId, "FAVORITE_NOT_FOUND");
        this.favoriteId = favoriteId;
    }

    public Long getFavoriteId() {
        return favoriteId;
    }
}
