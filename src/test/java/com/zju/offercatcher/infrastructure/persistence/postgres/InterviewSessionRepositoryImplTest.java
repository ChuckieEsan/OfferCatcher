package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;
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
@Import({InterviewSessionRepositoryImpl.class})
class InterviewSessionRepositoryImplTest {

    @Autowired
    InterviewSessionRepositoryImpl repo;
    @Autowired
    InterviewSessionJpaRepository jpaRepo;

    @Test
    @DisplayName("save 应持久化面试会话")
    void save() {
        InterviewSession s = InterviewSession.create("user-1", "字节跳动", "Java 后端",
                DifficultyLevel.MEDIUM, 5);
        repo.save(s);

        Optional<InterviewSessionJpaEntity> saved = jpaRepo.findById(s.getSessionId());
        assertThat(saved).isPresent();
        assertThat(saved.get().getCompany()).isEqualTo("字节跳动");
        assertThat(saved.get().getPosition()).isEqualTo("Java 后端");
    }

    @Test
    @DisplayName("findById 应重建完整 InterviewSession")
    void findById() {
        InterviewSession s = InterviewSession.create("user-1", "阿里", "架构师",
                DifficultyLevel.HARD, 3);
        repo.save(s);

        Optional<InterviewSession> result = repo.findById(s.getSessionId());
        assertThat(result).isPresent();
        assertThat(result.get().getCompany()).isEqualTo("阿里");
        assertThat(result.get().getTotalQuestions()).isEqualTo(3);
    }

    @Test
    @DisplayName("findByUserId 应分页返回用户会话")
    void findByUserId() {
        repo.save(InterviewSession.create("user-1", "阿里", "Java", DifficultyLevel.MEDIUM, 3));
        repo.save(InterviewSession.create("user-1", "字节", "Go", DifficultyLevel.EASY, 5));
        repo.save(InterviewSession.create("user-2", "美团", "Python", DifficultyLevel.HARD, 7));

        List<InterviewSession> result = repo.findByUserId("user-1", 0, 10);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByUserIdAndStatus 应按状态过滤")
    void findByUserIdAndStatus() {
        InterviewSession active = InterviewSession.create("user-1", "阿里", "Java",
                DifficultyLevel.MEDIUM, 3);
        repo.save(active);

        InterviewSession paused = InterviewSession.create("user-1", "字节", "Go",
                DifficultyLevel.EASY, 5);
        paused.pause();
        repo.save(paused);

        List<InterviewSession> result = repo.findByUserIdAndStatus("user-1", SessionStatus.PAUSED, 0, 10);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStatus()).isEqualTo(SessionStatus.PAUSED);
    }

    @Test
    @DisplayName("deleteById 应删除且验证所有权")
    void deleteById() {
        InterviewSession s = InterviewSession.create("user-1", "阿里", "Java",
                DifficultyLevel.MEDIUM, 3);
        repo.save(s);

        repo.deleteById(s.getSessionId(), "user-1");
        assertThat(jpaRepo.findById(s.getSessionId())).isEmpty();
    }
}
