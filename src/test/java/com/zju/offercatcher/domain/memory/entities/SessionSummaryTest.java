package com.zju.offercatcher.domain.memory.entities;

import com.zju.offercatcher.domain.shared.enums.MemoryLayer;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SessionSummary 实体测试
 */
@DisplayName("SessionSummary 实体测试")
class SessionSummaryTest {

    private static final Long CONVERSATION_ID = 1L;
    private static final String USER_ID = "user-001";
    private static final String SUMMARY = "用户询问了 Java 多态的概念，解释了继承和接口的关系";

    @Nested
    @DisplayName("工厂方法 create")
    class CreateTests {

        @Test
        @DisplayName("应成功创建会话摘要")
        void shouldCreateSummarySuccessfully() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);

            assertThat(summary.getConversationId()).isEqualTo(CONVERSATION_ID);
            assertThat(summary.getUserId()).isEqualTo(USER_ID);
            assertThat(summary.getSummary()).isEqualTo(SUMMARY);
            assertThat(summary.getMemoryLayer()).isEqualTo(MemoryLayer.STM);
            assertThat(summary.getImportanceScore()).isEqualTo(0.5);
            assertThat(summary.getAccessCount()).isEqualTo(0);
            assertThat(summary.getDecayFactor()).isEqualTo(1.0);
            assertThat(summary.isMarkedForDeletion()).isFalse();
        }

        @Test
        @DisplayName("conversationId 为空应抛出异常")
        void shouldThrowExceptionWhenConversationIdIsNull() {
            assertThatThrownBy(() -> SessionSummary.create(null, USER_ID, SUMMARY))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("conversationId");
        }

        @Test
        @DisplayName("userId 为空应抛出异常")
        void shouldThrowExceptionWhenUserIdIsNull() {
            assertThatThrownBy(() -> SessionSummary.create(CONVERSATION_ID, null, SUMMARY))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("userId");
        }

        @Test
        @DisplayName("summary 为空应抛出异常")
        void shouldThrowExceptionWhenSummaryIsNull() {
            assertThatThrownBy(() -> SessionSummary.create(CONVERSATION_ID, USER_ID, null))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("summary");
        }
    }

    @Nested
    @DisplayName("访问记录方法 recordAccess")
    class RecordAccessTests {

        @Test
        @DisplayName("应成功记录访问")
        void shouldRecordAccessSuccessfully() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);

            summary.recordAccess();
            summary.recordAccess();

            assertThat(summary.getAccessCount()).isEqualTo(2);
            assertThat(summary.getLastAccessed()).isNotNull();
            assertThat(summary.getLastAccessed()).isBeforeOrEqualTo(LocalDateTime.now());
        }
    }

    @Nested
    @DisplayName("反馈方法 addFeedback")
    class AddFeedbackTests {

        @Test
        @DisplayName("正向反馈应增加重要性")
        void shouldIncreaseImportanceForPositiveFeedback() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);
            double before = summary.getImportanceScore();

            summary.addFeedback(true);

            assertThat(summary.getFeedbackScore()).isEqualTo(1);
            assertThat(summary.getImportanceScore()).isGreaterThan(before);
        }

        @Test
        @DisplayName("负向反馈应降低重要性")
        void shouldDecreaseImportanceForNegativeFeedback() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);
            double before = summary.getImportanceScore();

            summary.addFeedback(false);

            assertThat(summary.getFeedbackScore()).isEqualTo(-1);
            assertThat(summary.getImportanceScore()).isLessThan(before);
        }

        @Test
        @DisplayName("重要性分数应在 0-1 范围内")
        void shouldKeepImportanceScoreInRange() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);

            // 多次正向反馈
            for (int i = 0; i < 20; i++) {
                summary.addFeedback(true);
            }
            assertThat(summary.getImportanceScore()).isLessThanOrEqualTo(1.0);

            // 多次负向反馈
            for (int i = 0; i < 20; i++) {
                summary.addFeedback(false);
            }
            assertThat(summary.getImportanceScore()).isGreaterThanOrEqualTo(0.0);
        }
    }

    @Nested
    @DisplayName("升级到长期记忆方法 upgradeToLtm")
    class UpgradeToLtmTests {

        @Test
        @DisplayName("应成功升级到长期记忆")
        void shouldUpgradeToLtmSuccessfully() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);

            summary.upgradeToLtm();

            assertThat(summary.getMemoryLayer()).isEqualTo(MemoryLayer.LTM);
            assertThat(summary.isLongTerm()).isTrue();
            assertThat(summary.isShortTerm()).isFalse();
            assertThat(summary.getImportanceScore()).isGreaterThanOrEqualTo(0.7);
        }

        @Test
        @DisplayName("长期记忆的衰减因子应为 1.0")
        void shouldHaveDecayFactorOneForLtm() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);
            summary.upgradeToLtm();

            assertThat(summary.getDecayFactor()).isEqualTo(1.0);
        }
    }

    @Nested
    @DisplayName("衰减方法 applyDecay")
    class ApplyDecayTests {

        @Test
        @DisplayName("STM 应正确衰减")
        void shouldDecayStmCorrectly() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);

            summary.applyDecay(0.1);

            assertThat(summary.getDecayFactor()).isEqualTo(0.9);
            assertThat(summary.isMarkedForDeletion()).isFalse();
        }

        @Test
        @DisplayName("低衰减因子应标记删除")
        void shouldMarkForDeletionWhenDecayFactorIsLow() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);

            // 多次衰减直到低于 0.1
            for (int i = 0; i < 30; i++) {
                summary.applyDecay(0.1);
            }

            assertThat(summary.getDecayFactor()).isLessThan(0.1);
            assertThat(summary.isMarkedForDeletion()).isTrue();
        }

        @Test
        @DisplayName("LTM 不应衰减")
        void shouldNotDecayLtm() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);
            summary.upgradeToLtm();
            double before = summary.getDecayFactor();

            summary.applyDecay(0.5);

            assertThat(summary.getDecayFactor()).isEqualTo(before);
        }
    }

    @Nested
    @DisplayName("用户隔离方法 isOwnedBy")
    class IsOwnedByTests {

        @Test
        @DisplayName("所有者应返回 true")
        void shouldReturnTrueForOwner() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);

            assertThat(summary.isOwnedBy(USER_ID)).isTrue();
        }

        @Test
        @DisplayName("非所有者应返回 false")
        void shouldReturnFalseForNonOwner() {
            SessionSummary summary = SessionSummary.create(CONVERSATION_ID, USER_ID, SUMMARY);

            assertThat(summary.isOwnedBy("other-user")).isFalse();
        }
    }
}