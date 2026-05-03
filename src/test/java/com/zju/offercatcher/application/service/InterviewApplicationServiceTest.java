package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.interview.repositories.InterviewSessionRepository;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionStatus;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
    "offercatcher.worker.extract.enabled=false",
    "offercatcher.worker.answer.polling=false",
    "offercatcher.rabbitmq.enabled=false"
})
@Transactional
class InterviewApplicationServiceTest {

    @Autowired
    InterviewApplicationService interviewService;

    @Autowired
    InterviewSessionRepository sessionRepository;

    static final String USER = "test-user";
    static final String COMPANY = "阿里巴巴";
    static final String POSITION = "Java";

    @Nested
    @DisplayName("createSession + saveSession")
    class CreateAndSave {

        @Test
        @DisplayName("saveSession 后题目应被持久化")
        void shouldPersistQuestionsAfterSave() {
            InterviewSession session = interviewService.createSession(
                USER, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3, null);

            InterviewQuestion q1 = InterviewQuestion.create(
                1L, "q-001", "第一题", "knowledge", DifficultyLevel.MEDIUM, List.of());
            session.addQuestion(q1);
            interviewService.saveSession(session);

            // 从 DB 重新加载
            Optional<InterviewSession> reloaded = interviewService.getSession(
                session.getSessionId(), USER);
            assertThat(reloaded).isPresent();
            assertThat(reloaded.get().getQuestions()).hasSize(1);
        }
    }

    @Nested
    @DisplayName("skipCurrentQuestion")
    class Skip {

        @Test
        @DisplayName("skip 后 currentQuestionIdx 推进")
        void shouldAdvanceAfterSkip() {
            InterviewSession session = interviewService.createSession(
                USER, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3, null);

            session.addQuestion(InterviewQuestion.create(
                1L, "q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of()));
            session.addQuestion(InterviewQuestion.create(
                2L, "q-002", "题目2", "behavioral", DifficultyLevel.MEDIUM, List.of()));
            interviewService.saveSession(session);

            // reload + skip
            InterviewSession reloaded = interviewService.getSession(
                session.getSessionId(), USER).orElseThrow();
            assertThat(reloaded.getCurrentQuestionIdx()).isEqualTo(0);

            interviewService.skipQuestion(session.getSessionId(), USER);

            InterviewSession afterSkip = interviewService.getSession(
                session.getSessionId(), USER).orElseThrow();
            assertThat(afterSkip.getCurrentQuestionIdx()).isEqualTo(1);
            assertThat(afterSkip.getCurrentQuestion().orElseThrow().getQuestionText())
                .isEqualTo("题目2");
        }

        @Test
        @DisplayName("skip 最后一题应完成面试")
        void shouldCompleteWhenSkipLastQuestion() {
            InterviewSession session = interviewService.createSession(
                USER, COMPANY, POSITION, DifficultyLevel.MEDIUM, 1, null);

            session.addQuestion(InterviewQuestion.create(
                1L, "q-001", "唯一题目", "knowledge", DifficultyLevel.MEDIUM, List.of()));
            interviewService.saveSession(session);

            interviewService.skipQuestion(session.getSessionId(), USER);

            InterviewSession afterSkip = interviewService.getSession(
                session.getSessionId(), USER).orElseThrow();
            assertThat(afterSkip.getStatus()).isEqualTo(SessionStatus.COMPLETED);
        }
    }
}
