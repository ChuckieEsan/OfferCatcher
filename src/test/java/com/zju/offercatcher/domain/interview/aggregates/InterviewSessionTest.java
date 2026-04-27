package com.zju.offercatcher.domain.interview.aggregates;

import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.SessionStatus;
import com.zju.offercatcher.domain.shared.enums.QuestionStatus;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.domain.shared.exception.InvalidStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * InterviewSession 聚合根测试
 */
@DisplayName("InterviewSession 聚合根测试")
class InterviewSessionTest {

    private static final String USER_ID = "user-001";
    private static final String COMPANY = "阿里巴巴";
    private static final String POSITION = "Java 开发工程师";

    @Nested
    @DisplayName("工厂方法 create")
    class CreateTests {

        @Test
        @DisplayName("应成功创建面试会话")
        void shouldCreateSessionSuccessfully() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 10);

            assertThat(session.getUserId()).isEqualTo(USER_ID);
            assertThat(session.getCompany()).isEqualTo(COMPANY);
            assertThat(session.getPosition()).isEqualTo(POSITION);
            assertThat(session.getDifficulty()).isEqualTo(DifficultyLevel.MEDIUM);
            assertThat(session.getTotalQuestions()).isEqualTo(10);
            assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(session.getSessionId()).isNotNull();
            assertThat(session.getQuestions()).isEmpty();
            assertThat(session.isActive()).isTrue();
        }

        @Test
        @DisplayName("userId 为空应抛出异常")
        void shouldThrowExceptionWhenUserIdIsNull() {
            assertThatThrownBy(() -> InterviewSession.create(null, COMPANY, POSITION, DifficultyLevel.MEDIUM, 10))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("题目总数为 0 应抛出异常")
        void shouldThrowExceptionWhenTotalQuestionsIsZero() {
            assertThatThrownBy(() -> InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 0))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("题目总数");
        }
    }

    @Nested
    @DisplayName("题目操作")
    class QuestionOperationTests {

        @Test
        @DisplayName("应成功添加题目")
        void shouldAddQuestionSuccessfully() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3);
            InterviewQuestion question = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(question);

            assertThat(session.getQuestions()).hasSize(1);
        }

        @Test
        @DisplayName("题目超出上限应抛出异常")
        void shouldThrowExceptionWhenExceedingMaxQuestions() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 1);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());
            InterviewQuestion q2 = InterviewQuestion.create("q-002", "题目2", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);

            assertThatThrownBy(() -> session.addQuestion(q2))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("题目总数上限");
        }

        @Test
        @DisplayName("应正确获取当前题目")
        void shouldGetCurrentQuestion() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());
            InterviewQuestion q2 = InterviewQuestion.create("q-002", "题目2", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);
            session.addQuestion(q2);

            assertThat(session.getCurrentQuestion()).isPresent();
            assertThat(session.getCurrentQuestion().get().getQuestionId()).isEqualTo("q-001");
        }

        @Test
        @DisplayName("应正确进入下一题")
        void shouldGoToNextQuestion() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());
            InterviewQuestion q2 = InterviewQuestion.create("q-002", "题目2", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);
            session.addQuestion(q2);

            session.nextQuestion();

            assertThat(session.getCurrentQuestion()).isPresent();
            assertThat(session.getCurrentQuestion().get().getQuestionId()).isEqualTo("q-002");
        }
    }

    @Nested
    @DisplayName("回答和评分")
    class AnswerAndScoreTests {

        @Test
        @DisplayName("应正确回答并累加分数")
        void shouldAnswerAndAccumulateScore() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);
            session.answerCurrentQuestion("答案", 85, "反馈");

            assertThat(session.getTotalScore()).isEqualTo(85);
            assertThat(session.getCorrectCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("低分不应增加正确数")
        void shouldNotIncreaseCorrectCountForLowScore() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);
            session.answerCurrentQuestion("答案", 55, "需要加强");

            assertThat(session.getTotalScore()).isEqualTo(55);
            assertThat(session.getCorrectCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("应正确跳过题目")
        void shouldSkipQuestionSuccessfully() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);
            session.skipCurrentQuestion();

            assertThat(session.getCurrentQuestion().get().getStatus()).isEqualTo(QuestionStatus.SKIPPED);
        }
    }

    @Nested
    @DisplayName("状态管理")
    class StatusManagementTests {

        @Test
        @DisplayName("应成功完成面试")
        void shouldCompleteSessionSuccessfully() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 1);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);
            session.answerCurrentQuestion("答案", 80, "反馈");
            session.complete();

            assertThat(session.getStatus()).isEqualTo(SessionStatus.COMPLETED);
            assertThat(session.isCompleted()).isTrue();
            assertThat(session.getEndedAt()).isNotNull();
        }

        @Test
        @DisplayName("已完成的面试不能继续")
        void shouldNotContinueCompletedSession() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 1);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);
            session.complete();

            assertThatThrownBy(() -> session.answerCurrentQuestion("答案", 80, "反馈"))
                .isInstanceOf(InvalidStateException.class);
        }

        @Test
        @DisplayName("应成功暂停和恢复面试")
        void shouldPauseAndResumeSuccessfully() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 1);

            session.pause();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.PAUSED);
            assertThat(session.isPaused()).isTrue();

            session.resume();
            assertThat(session.getStatus()).isEqualTo(SessionStatus.ACTIVE);
            assertThat(session.isActive()).isTrue();
        }
    }

    @Nested
    @DisplayName("统计方法")
    class StatisticsTests {

        @Test
        @DisplayName("应正确计算平均分")
        void shouldCalculateAverageScore() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 3);
            InterviewQuestion q1 = InterviewQuestion.create("q-001", "题目1", "knowledge", DifficultyLevel.MEDIUM, List.of());
            InterviewQuestion q2 = InterviewQuestion.create("q-002", "题目2", "knowledge", DifficultyLevel.MEDIUM, List.of());

            session.addQuestion(q1);
            session.addQuestion(q2);

            session.answerCurrentQuestion("答案1", 80, "反馈1");
            session.nextQuestion();
            session.answerCurrentQuestion("答案2", 90, "反馈2");

            assertThat(session.calculateAverageScore()).isEqualTo(85.0);
        }

        @Test
        @DisplayName("用户隔离检查")
        void shouldCheckOwnership() {
            InterviewSession session = InterviewSession.create(USER_ID, COMPANY, POSITION, DifficultyLevel.MEDIUM, 1);

            assertThat(session.isOwnedBy(USER_ID)).isTrue();
            assertThat(session.isOwnedBy("other-user")).isFalse();
        }
    }
}