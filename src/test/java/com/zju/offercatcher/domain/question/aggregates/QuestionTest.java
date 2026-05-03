package com.zju.offercatcher.domain.question.aggregates;

import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.domain.shared.enums.SourceType;
import com.zju.offercatcher.domain.shared.enums.Visibility;
import com.zju.offercatcher.domain.shared.exception.DomainException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Question 聚合根测试
 */
class QuestionTest {

    @Nested
    @DisplayName("工厂方法测试")
    class FactoryMethodTests {

        @Test
        @DisplayName("创建私有题目：visibility 应为 PRIVATE")
        void createPrivate_shouldHavePrivateVisibility() {
            Question question = Question.createPrivate(
                    "user-001",
                    "Java 中 HashMap 的实现原理是什么？",
                    "阿里巴巴",
                    "Java 后端开发",
                    QuestionType.KNOWLEDGE,
                    List.of("HashMap", "数据结构")
            );

            assertThat(question.getVisibility()).isEqualTo(Visibility.PRIVATE);
            assertThat(question.getSourceType()).isEqualTo(SourceType.USER_UPLOAD);
            assertThat(question.getUserId()).isEqualTo("user-001");
        }

        @Test
        @DisplayName("创建公共题目：visibility 应为 PUBLIC")
        void createPublic_shouldHavePublicVisibility() {
            Question question = Question.createPublic(
                    "user-001",
                    "如何设计一个高并发系统？",
                    "字节跳动",
                    "架构师",
                    QuestionType.SCENARIO,
                    List.of("高并发", "系统设计")
            );

            assertThat(question.getVisibility()).isEqualTo(Visibility.PUBLIC);
            assertThat(question.getSourceType()).isEqualTo(SourceType.USER_UPLOAD);
        }

        @Test
        @DisplayName("创建系统导入题目：userId 应为 system")
        void createSystemImport_shouldHaveSystemUserId() {
            Question question = Question.createSystemImport(
                    "什么是微服务架构？",
                    "腾讯",
                    "后端开发",
                    QuestionType.KNOWLEDGE,
                    List.of("微服务", "架构")
            );

            assertThat(question.getUserId()).isEqualTo("system");
            assertThat(question.getVisibility()).isEqualTo(Visibility.PUBLIC);
            assertThat(question.getSourceType()).isEqualTo(SourceType.SYSTEM_IMPORT);
        }

        @Test
        @DisplayName("相同内容的题目：不同用户应有不同 ID")
        void sameContent_differentUsers_shouldHaveDifferentIds() {
            String questionText = "Java 中 HashMap 的实现原理";
            String company = "阿里巴巴";

            Question q1 = Question.createPrivate("user-001", questionText, company,
                    "开发", QuestionType.KNOWLEDGE, List.of());
            Question q2 = Question.createPrivate("user-002", questionText, company,
                    "开发", QuestionType.KNOWLEDGE, List.of());

            assertThat(q1.getQuestionHash()).isNotEqualTo(q2.getQuestionHash());
        }

        @Test
        @DisplayName("相同用户相同内容：应生成相同 ID")
        void sameUserSameContent_shouldHaveSameId() {
            String userId = "user-001";
            String questionText = "Java 中 HashMap 的实现原理";
            String company = "阿里巴巴";

            Question q1 = Question.createPrivate(userId, questionText, company,
                    "开发", QuestionType.KNOWLEDGE, List.of());
            Question q2 = Question.createPrivate(userId, questionText, company,
                    "开发", QuestionType.KNOWLEDGE, List.of());

            assertThat(q1.getQuestionHash()).isEqualTo(q2.getQuestionHash());
        }
    }

    @Nested
    @DisplayName("可见性判断测试")
    class VisibilityTests {

        @Test
        @DisplayName("公共题目：对所有用户可见")
        void publicQuestion_shouldBeVisibleToAllUsers() {
            Question question = Question.createPublic(
                    "owner-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThat(question.isVisibleTo("owner-001")).isTrue();
            assertThat(question.isVisibleTo("other-user")).isTrue();
            assertThat(question.isVisibleTo(null)).isTrue(); // 公共题目对 null 也可见
        }

        @Test
        @DisplayName("私有题目：仅对所有者可见")
        void privateQuestion_shouldBeVisibleOnlyToOwner() {
            Question question = Question.createPrivate(
                    "owner-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThat(question.isVisibleTo("owner-001")).isTrue();
            assertThat(question.isVisibleTo("other-user")).isFalse();
        }

        @Test
        @DisplayName("所有权判断：仅所有者拥有")
        void isOwnedBy_shouldReturnCorrectResult() {
            Question question = Question.createPrivate(
                    "owner-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThat(question.isOwnedBy("owner-001")).isTrue();
            assertThat(question.isOwnedBy("other-user")).isFalse();
        }
    }

    @Nested
    @DisplayName("业务方法测试")
    class BusinessMethodTests {

        @Test
        @DisplayName("发布私有题目到公共题库")
        void publishToPublic_shouldChangeVisibility() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThat(question.getVisibility()).isEqualTo(Visibility.PRIVATE);

            question.publishToPublic();

            assertThat(question.getVisibility()).isEqualTo(Visibility.PUBLIC);
        }

        @Test
        @DisplayName("公共题目发布应抛出异常")
        void publishPublicQuestion_shouldThrowException() {
            Question question = Question.createPublic(
                    "user-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThatThrownBy(question::publishToPublic)
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("只有私有题目才能发布到公共题库");
        }

        @Test
        @DisplayName("更新答案")
        void updateAnswer_shouldUpdateAnswerAndTimestamp() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThat(question.getAnswer()).isNull();

            question.updateAnswer("这是答案内容");

            assertThat(question.getAnswer()).isEqualTo("这是答案内容");
            assertThat(question.getUpdatedAt()).isAfter(question.getCreatedAt());
        }

        @Test
        @DisplayName("添加考点簇")
        void addCluster_shouldAddClusterId() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThat(question.getClusterIds()).isEmpty();

            question.addCluster("cluster-001");
            question.addCluster("cluster-002");
            question.addCluster("cluster-001"); // 重复添加

            assertThat(question.getClusterIds()).containsExactly("cluster-001", "cluster-002");
        }

        @Test
        @DisplayName("更新熟练度")
        void updateMastery_shouldUpdateLevel() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThat(question.getMasteryLevel()).isEqualTo(MasteryLevel.LEVEL_0);

            question.updateMastery(MasteryLevel.LEVEL_3);

            assertThat(question.getMasteryLevel()).isEqualTo(MasteryLevel.LEVEL_3);
        }
    }

    @Nested
    @DisplayName("题目类型测试")
    class QuestionTypeTests {

        @Test
        @DisplayName("知识型题目不需要异步生成答案")
        void knowledgeType_shouldNotRequireAsyncAnswer() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThat(question.requiresAsyncAnswer()).isFalse();
        }

        @Test
        @DisplayName("项目型题目需要异步生成答案")
        void projectType_shouldRequireAsyncAnswer() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目",
                    "公司",
                    "岗位",
                    QuestionType.PROJECT,
                    List.of()
            );

            assertThat(question.requiresAsyncAnswer()).isTrue();
        }

        @Test
        @DisplayName("算法型题目需要异步生成答案")
        void algorithmType_shouldRequireAsyncAnswer() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目",
                    "公司",
                    "岗位",
                    QuestionType.ALGORITHM,
                    List.of()
            );

            assertThat(question.requiresAsyncAnswer()).isTrue();
        }
    }

    @Nested
    @DisplayName("上下文生成测试")
    class ContextGenerationTests {

        @Test
        @DisplayName("生成 Embedding 上下文")
        void toContext_shouldGenerateCorrectFormat() {
            Question question = Question.createPrivate(
                    "user-001",
                    "Java 中 HashMap 的实现原理是什么？",
                    "阿里巴巴",
                    "Java 后端开发",
                    QuestionType.KNOWLEDGE,
                    List.of("HashMap", "数据结构")
            );

            String context = question.toContext();

            assertThat(context).contains("阿里巴巴");
            assertThat(context).contains("Java 后端开发");
            assertThat(context).contains("knowledge");
            assertThat(context).contains("HashMap,数据结构");
            assertThat(context).contains("Java 中 HashMap 的实现原理是什么？");
        }

        @Test
        @DisplayName("无考点时上下文显示综合")
        void toContext_withEmptyEntities_shouldShow综合() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目内容",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            String context = question.toContext();

            assertThat(context).contains("考点：综合");
        }
    }

    @Nested
    @DisplayName("不可变性测试")
    class ImmutabilityTests {

        @Test
        @DisplayName("核心考点列表不可修改")
        void getCoreEntities_shouldBeUnmodifiable() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of("考点1", "考点2")
            );

            assertThatThrownBy(() -> question.getCoreEntities().add("考点3"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("考点簇列表不可修改")
        void getClusterIds_shouldBeUnmodifiable() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );
            question.addCluster("cluster-001");

            assertThatThrownBy(() -> question.getClusterIds().add("cluster-002"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("元数据不可修改")
        void getMetadata_shouldBeUnmodifiable() {
            Question question = Question.createPrivate(
                    "user-001",
                    "题目",
                    "公司",
                    "岗位",
                    QuestionType.KNOWLEDGE,
                    List.of()
            );

            assertThatThrownBy(() -> question.getMetadata().put("key", "value"))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }
}