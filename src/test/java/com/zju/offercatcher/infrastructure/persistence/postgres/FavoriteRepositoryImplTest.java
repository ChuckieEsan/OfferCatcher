package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.favorite.aggregates.Favorite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({FavoriteRepositoryImpl.class})
class FavoriteRepositoryImplTest {

    @Autowired FavoriteRepositoryImpl repo;
    @Autowired FavoriteJpaRepository jpaRepo;

    @Test
    @DisplayName("save 应持久化收藏")
    void save() {
        Favorite f = Favorite.create("user-1", "q1");
        repo.save(f);

        Optional<FavoriteJpaEntity> saved = jpaRepo.findById(f.getFavoriteId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getUserId()).isEqualTo("user-1");
        assertThat(saved.get().getQuestionId()).isEqualTo("q1");
    }

    @Test
    @DisplayName("findByUserId 应返回用户收藏")
    void findByUserId() {
        repo.save(Favorite.create("user-1", "q1"));
        repo.save(Favorite.create("user-1", "q2"));
        repo.save(Favorite.create("user-2", "q3"));

        List<Favorite> result = repo.findByUserId("user-1", 0, 10);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("existsByUserIdAndQuestionId 应正确检测")
    void existsByUserIdAndQuestionId() {
        repo.save(Favorite.create("user-1", "q1"));

        assertThat(repo.existsByUserIdAndQuestionId("user-1", "q1")).isTrue();
        assertThat(repo.existsByUserIdAndQuestionId("user-1", "q2")).isFalse();
    }

    @Test
    @DisplayName("deleteById 应删除且验证所有权")
    void deleteById() {
        Favorite f = Favorite.create("user-1", "q1");
        repo.save(f);

        repo.deleteById(f.getFavoriteId(), "user-1");
        assertThat(jpaRepo.findById(f.getFavoriteId())).isEmpty();
    }

    @Test
    @DisplayName("deleteByUserIdAndQuestionId 应删除")
    void deleteByUserIdAndQuestionId() {
        repo.save(Favorite.create("user-1", "q1"));
        repo.deleteByUserIdAndQuestionId("user-1", "q1");
        assertThat(repo.existsByUserIdAndQuestionId("user-1", "q1")).isFalse();
    }
}
