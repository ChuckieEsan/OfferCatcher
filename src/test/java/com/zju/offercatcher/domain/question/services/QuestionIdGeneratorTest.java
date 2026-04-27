package com.zju.offercatcher.domain.question.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * QuestionIdGenerator 测试
 */
class QuestionIdGeneratorTest {

    @Nested
    @DisplayName("ID 生成测试")
    class GenerateTests {

        @Test
        @DisplayName("生成 32 位 MD5 字符串")
        void generate_shouldReturn32CharacterMd5() {
            String id = QuestionIdGenerator.generate("user-001", "阿里巴巴", "题目内容");

            assertThat(id).hasSize(32);
            assertThat(id).matches("[0-9a-f]{32}");
        }

        @Test
        @DisplayName("相同输入生成相同 ID")
        void sameInput_shouldGenerateSameId() {
            String id1 = QuestionIdGenerator.generate("user-001", "阿里巴巴", "题目内容");
            String id2 = QuestionIdGenerator.generate("user-001", "阿里巴巴", "题目内容");

            assertThat(id1).isEqualTo(id2);
        }

        @Test
        @DisplayName("不同用户生成不同 ID")
        void differentUsers_shouldGenerateDifferentIds() {
            String id1 = QuestionIdGenerator.generate("user-001", "阿里巴巴", "题目内容");
            String id2 = QuestionIdGenerator.generate("user-002", "阿里巴巴", "题目内容");

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("不同公司生成不同 ID")
        void differentCompanies_shouldGenerateDifferentIds() {
            String id1 = QuestionIdGenerator.generate("user-001", "阿里巴巴", "题目内容");
            String id2 = QuestionIdGenerator.generate("user-001", "字节跳动", "题目内容");

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("不同题目内容生成不同 ID")
        void differentQuestionText_shouldGenerateDifferentIds() {
            String id1 = QuestionIdGenerator.generate("user-001", "阿里巴巴", "题目 A");
            String id2 = QuestionIdGenerator.generate("user-001", "阿里巴巴", "题目 B");

            assertThat(id1).isNotEqualTo(id2);
        }

        @Test
        @DisplayName("题目内容自动 trim")
        void questionText_shouldBeTrimmed() {
            String id1 = QuestionIdGenerator.generate("user-001", "阿里巴巴", "  题目内容  ");
            String id2 = QuestionIdGenerator.generate("user-001", "阿里巴巴", "题目内容");

            assertThat(id1).isEqualTo(id2);
        }

        @Test
        @DisplayName("公司可以为 null")
        void nullCompany_shouldStillGenerateId() {
            String id = QuestionIdGenerator.generate("user-001", null, "题目内容");

            assertThat(id).hasSize(32);
        }

        @Test
        @DisplayName("公司为空字符串")
        void emptyCompany_shouldStillGenerateId() {
            String id1 = QuestionIdGenerator.generate("user-001", null, "题目内容");
            String id2 = QuestionIdGenerator.generate("user-001", "", "题目内容");

            assertThat(id1).isEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("系统 ID 生成测试")
    class SystemIdTests {

        @Test
        @DisplayName("系统 ID 使用 userId = system")
        void generateSystemId_shouldUseSystemUserId() {
            String systemId = QuestionIdGenerator.generateSystemId("阿里巴巴", "题目内容");
            String manualId = QuestionIdGenerator.generate("system", "阿里巴巴", "题目内容");

            assertThat(systemId).isEqualTo(manualId);
        }

        @Test
        @DisplayName("系统 ID 格式正确")
        void generateSystemId_shouldBeValidFormat() {
            String id = QuestionIdGenerator.generateSystemId("阿里巴巴", "题目内容");

            assertThat(id).hasSize(32);
            assertThat(id).matches("[0-9a-f]{32}");
        }
    }

    @Nested
    @DisplayName("参数校验测试")
    class ValidationTests {

        @Test
        @DisplayName("userId 为 null 应抛出异常")
        void nullUserId_shouldThrowException() {
            assertThatThrownBy(() -> QuestionIdGenerator.generate(null, "公司", "题目"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId cannot be null or blank");
        }

        @Test
        @DisplayName("userId 为空字符串应抛出异常")
        void blankUserId_shouldThrowException() {
            assertThatThrownBy(() -> QuestionIdGenerator.generate("", "公司", "题目"))
                .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("questionText 为 null 应抛出异常")
        void nullQuestionText_shouldThrowException() {
            assertThatThrownBy(() -> QuestionIdGenerator.generate("user-001", "公司", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("questionText cannot be null or blank");
        }

        @Test
        @DisplayName("questionText 为空字符串应抛出异常")
        void blankQuestionText_shouldThrowException() {
            assertThatThrownBy(() -> QuestionIdGenerator.generate("user-001", "公司", "   "))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}