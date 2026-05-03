package com.zju.offercatcher.domain.interview.entities;

import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionStatus;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import com.zju.offercatcher.domain.shared.exception.InvalidStateException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * InterviewQuestion 实体测试
 */
@DisplayName("InterviewQuestion 实体测试")
class InterviewQuestionTest {

    private static final Long QUESTION_ID = 1L;
    private static final String QUESTION_HASH = "q-001";
    private static final String QUESTION_TEXT = "什么是 Java 的多态？";

    @Nested
    @DisplayName("工厂方法 create")
    class CreateTests {

        @Test
        @DisplayName("应成功创建面试题目")
        void shouldCreateQuestionSuccessfully() {
            InterviewQuestion question = InterviewQuestion.create(
                    QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM,
                    List.of("Java", "多态", "继承")
            );

            assertThat(question.getQuestionId()).isEqualTo(QUESTION_ID);
            assertThat(question.getQuestionHash()).isEqualTo(QUESTION_HASH);
            assertThat(question.getQuestionText()).isEqualTo(QUESTION_TEXT);
            assertThat(question.getDifficulty()).isEqualTo(DifficultyLevel.MEDIUM);
            assertThat(question.getStatus()).isEqualTo(QuestionStatus.PENDING);
            assertThat(question.getKnowledgePoints()).contains("Java", "多态");
        }

        @Test
        @DisplayName("questionId 为空应抛出异常")
        void shouldThrowExceptionWhenQuestionIdIsNull() {
            assertThatThrownBy(() -> InterviewQuestion.create(null, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of()))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("questionId");
        }

        @Test
        @DisplayName("questionHash 为空应抛出异常")
        void shouldThrowExceptionWhenQuestionHashIsBlank() {
            assertThatThrownBy(() -> InterviewQuestion.create(QUESTION_ID, null, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of()))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("questionHash");
        }

        @Test
        @DisplayName("questionText 为空应抛出异常")
        void shouldThrowExceptionWhenQuestionTextIsNull() {
            assertThatThrownBy(() -> InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, null, "knowledge", DifficultyLevel.MEDIUM, List.of()))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("questionText");
        }
    }

    @Nested
    @DisplayName("回答方法 answer")
    class AnswerTests {

        @Test
        @DisplayName("应成功回答题目")
        void shouldAnswerQuestionSuccessfully() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());

            question.answer("多态是指...", 85, "回答正确");

            assertThat(question.getStatus()).isEqualTo(QuestionStatus.SCORED);
            assertThat(question.getScore()).isEqualTo(85);
            assertThat(question.getUserAnswer()).isEqualTo("多态是指...");
            assertThat(question.isAnswered()).isTrue();
            assertThat(question.isPassed()).isTrue();
        }

        @Test
        @DisplayName("评分过低应不通过")
        void shouldNotPassWhenScoreIsLow() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());

            question.answer("不太清楚", 45, "需要加强学习");

            assertThat(question.isPassed()).isFalse();
        }

        @Test
        @DisplayName("评分超出范围应抛出异常")
        void shouldThrowExceptionWhenScoreIsOutOfRange() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());

            assertThatThrownBy(() -> question.answer("答案", 101, "反馈"))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("评分");
        }

        @Test
        @DisplayName("重复回答应抛出异常")
        void shouldThrowExceptionWhenAnsweringAlreadyAnsweredQuestion() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());
            question.answer("答案1", 80, "反馈1");

            assertThatThrownBy(() -> question.answer("答案2", 90, "反馈2"))
                    .isInstanceOf(InvalidStateException.class)
                    .hasMessageContaining("已经回答");
        }
    }

    @Nested
    @DisplayName("跳过方法 skip")
    class SkipTests {

        @Test
        @DisplayName("应成功跳过题目")
        void shouldSkipQuestionSuccessfully() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());

            question.skip();

            assertThat(question.getStatus()).isEqualTo(QuestionStatus.SKIPPED);
            assertThat(question.isAnswered()).isTrue();
        }

        @Test
        @DisplayName("已回答题目不能跳过")
        void shouldThrowExceptionWhenSkippingAnsweredQuestion() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());
            question.answer("答案", 80, "反馈");

            assertThatThrownBy(() -> question.skip())
                    .isInstanceOf(InvalidStateException.class);
        }
    }

    @Nested
    @DisplayName("提示和追问")
    class HintAndFollowUpTests {

        @Test
        @DisplayName("应成功添加提示")
        void shouldAddHintSuccessfully() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());

            question.addHint("提示1：多态涉及继承");
            question.addHint("提示2：方法重写");

            assertThat(question.getHintsGiven()).hasSize(2);
        }

        @Test
        @DisplayName("应成功添加追问")
        void shouldAddFollowUpSuccessfully() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());

            question.addFollowUp("追问1：接口和抽象类有什么区别？");
            question.addFollowUp("追问2：什么是向上转型？");

            assertThat(question.getFollowUps()).hasSize(2);
        }

        @Test
        @DisplayName("应正确获取当前追问")
        void shouldGetCurrentFollowUp() {
            InterviewQuestion question = InterviewQuestion.create(QUESTION_ID, QUESTION_HASH, QUESTION_TEXT, "knowledge", DifficultyLevel.MEDIUM, List.of());
            question.addFollowUp("追问1");
            question.addFollowUp("追问2");

            assertThat(question.getCurrentFollowUp()).isEqualTo("追问1");

            question.nextFollowUp();
            assertThat(question.getCurrentFollowUp()).isEqualTo("追问2");
        }
    }
}
