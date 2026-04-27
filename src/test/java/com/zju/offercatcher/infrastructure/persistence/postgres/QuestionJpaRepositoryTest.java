package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.domain.shared.enums.SourceType;
import com.zju.offercatcher.domain.shared.enums.Visibility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuestionJpaRepositoryTest {

    @Autowired QuestionJpaRepository repo;

    QuestionJpaEntity makeEntity(String questionId, String userId, String questionText,
                                  String company, String position, String answer) {
        QuestionJpaEntity e = new QuestionJpaEntity();
        e.setQuestionId(questionId);
        e.setUserId(userId);
        e.setQuestionText(questionText);
        e.setQuestionType(QuestionType.KNOWLEDGE);
        e.setVisibility(Visibility.PRIVATE);
        e.setSourceType(SourceType.USER_UPLOAD);
        e.setMasteryLevel(MasteryLevel.LEVEL_0);
        e.setCompany(company);
        e.setPosition(position);
        e.setAnswer(answer);
        e.setCoreEntities(List.of("Java"));
        e.setClusterIds(List.of());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        return e;
    }

    @Test
    @DisplayName("findByUserIdPaginated 应返回用户题目")
    void findByUserIdPaginated() {
        repo.save(makeEntity("q1", "user-1", "Q1", "阿里", "Java", null));
        repo.save(makeEntity("q2", "user-2", "Q2", "字节", "Go", null));

        List<QuestionJpaEntity> result = repo.findByUserIdPaginated("user-1", 10, 0);
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getQuestionId()).isEqualTo("q1");
    }

    @Test
    @DisplayName("findUnansweredQuestions 应只返回无答案的题目")
    void findUnansweredQuestions() {
        repo.save(makeEntity("q1", "user-1", "Q1", "阿里", "Java", null));
        repo.save(makeEntity("q2", "user-1", "Q2", "字节", "Go", "已有答案"));
        repo.save(makeEntity("q3", "user-1", "Q3", "美团", "Python", ""));

        List<QuestionJpaEntity> result = repo.findUnansweredQuestions(10);
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(QuestionJpaEntity::getQuestionId))
            .containsExactlyInAnyOrder("q1", "q3");
    }

    @Test
    @DisplayName("findRecentlyUpdated 应返回最近更新的题目")
    void findRecentlyUpdated() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        repo.save(makeEntity("q1", "user-1", "Q1", "阿里", "Java", null));
        repo.save(makeEntity("q2", "user-1", "Q2", "字节", "Go", null));

        List<QuestionJpaEntity> result = repo.findRecentlyUpdated(cutoff, 10);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("findByQuestionId 应返回指定题目")
    void findByQuestionId() {
        repo.save(makeEntity("q1", "user-1", "Q1", "阿里", "Java", null));

        Optional<QuestionJpaEntity> result = repo.findByQuestionId("q1");
        assertThat(result).isPresent();
        assertThat(result.get().getCompany()).isEqualTo("阿里");
    }

    @Test
    @DisplayName("deleteByQuestionIdAndUserId 应删除且验证所有权")
    void deleteByQuestionIdAndUserId() {
        repo.save(makeEntity("q1", "user-1", "Q1", "阿里", "Java", null));

        int deleted = repo.deleteByQuestionIdAndUserId("q1", "user-1");
        assertThat(deleted).isEqualTo(1);
        assertThat(repo.findByQuestionId("q1")).isEmpty();
    }

    @Test
    @DisplayName("deleteByQuestionIdAndUserId 不同用户不应删除")
    void deleteByOtherUser() {
        repo.save(makeEntity("q1", "user-1", "Q1", "阿里", "Java", null));

        int deleted = repo.deleteByQuestionIdAndUserId("q1", "user-2");
        assertThat(deleted).isEqualTo(0);
        assertThat(repo.findByQuestionId("q1")).isPresent();
    }
}
