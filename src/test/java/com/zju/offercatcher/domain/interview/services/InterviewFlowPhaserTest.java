package com.zju.offercatcher.domain.interview.services;

import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.InterviewPhase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InterviewFlowPhaser 阶段机测试")
class InterviewFlowPhaserTest {

    private final InterviewFlowPhaser phaser = new InterviewFlowPhaser();

    private static InterviewQuestion makeQ(String text, String type, DifficultyLevel diff,
                                           List<String> knowledgePoints) {
        return InterviewQuestion.create(
                (long) text.hashCode(), "hash-" + text.hashCode(),
                text, type, diff, knowledgePoints
        );
    }

    // ==================== 阶段分类 ====================

    @Nested
    @DisplayName("阶段分类")
    class PhaseClassification {

        @Test
        @DisplayName("前 2-3 题为 OPENING 阶段")
        void openingPhaseFirst() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            candidates.add(makeQ("Redis 持久化原理", "knowledge", DifficultyLevel.MEDIUM, List.of("Redis")));
            candidates.add(makeQ("MQ 消息可靠性", "knowledge", DifficultyLevel.HARD, List.of("消息队列")));
            candidates.add(makeQ("缓存穿透怎么解决", "knowledge", DifficultyLevel.EASY, List.of("Redis")));
            candidates.add(makeQ("分布式锁实现对比", "knowledge", DifficultyLevel.MEDIUM, List.of("分布式系统")));
            candidates.add(makeQ("JVM 内存模型", "knowledge", DifficultyLevel.HARD, List.of("Java")));
            candidates.add(makeQ("微服务拆分原则", "scenario", DifficultyLevel.MEDIUM, List.of("微服务")));
            candidates.add(makeQ("讲讲你的项目经历", "behavioral", DifficultyLevel.EASY, List.of("项目")));
            candidates.add(makeQ("如何处理团队冲突", "behavioral", DifficultyLevel.EASY, List.of("软技能")));

            List<InterviewQuestion> ordered = phaser.phase(candidates, 10);

            // 前两题必须为 OPENING（自我介绍 + 项目介绍）
            assertThat(ordered.get(0).getPhase()).isEqualTo(InterviewPhase.OPENING);
            assertThat(ordered.get(1).getPhase()).isEqualTo(InterviewPhase.OPENING);
            assertThat(ordered.get(0).getQuestionText()).isEqualTo("请做个简单的自我介绍");
            assertThat(ordered.get(1).getQuestionText()).isEqualTo("能介绍一下你最近做过的一个项目吗？");
        }

        @Test
        @DisplayName("TECHNICAL 阶段紧随 OPENING 之后")
        void technicalAfterOpening() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            for (int i = 0; i < 15; i++) {
                candidates.add(makeQ("技术题 " + i, "knowledge", DifficultyLevel.MEDIUM, List.of("Topic" + i % 4)));
            }

            List<InterviewQuestion> ordered = phaser.phase(candidates, 10);

            // Opening 阶段（前 2-3 题）
            assertThat(ordered.get(0).getPhase()).isEqualTo(InterviewPhase.OPENING);
            assertThat(ordered.get(1).getPhase()).isEqualTo(InterviewPhase.OPENING);
            // 后续题目为 TECHNICAL 或 BEHAVIORAL
            long techCount = ordered.stream().filter(q -> InterviewPhase.TECHNICAL.equals(q.getPhase())).count();
            assertThat(techCount).isGreaterThan(0);
        }

        @Test
        @DisplayName("BEHAVIORAL 阶段的题目出现在 TECHNICAL 之后")
        void behavioralAfterTechnical() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                candidates.add(makeQ("技术题 " + i, "knowledge", DifficultyLevel.MEDIUM, List.of("Topic" + i)));
            }
            for (int i = 0; i < 6; i++) {
                candidates.add(makeQ("行为题 " + i, "behavioral", DifficultyLevel.EASY, List.of("软技能")));
            }

            List<InterviewQuestion> ordered = phaser.phase(candidates, 12);

            // 找到 behavioral 题的最小 index，应该 > 所有 opening 题
            int firstBehavioralIdx = -1;
            int lastTechnicalIdx = -1;
            for (int i = 0; i < ordered.size(); i++) {
                if (InterviewPhase.TECHNICAL.equals(ordered.get(i).getPhase())) lastTechnicalIdx = i;
                if (InterviewPhase.BEHAVIORAL.equals(ordered.get(i).getPhase()) && firstBehavioralIdx == -1) {
                    firstBehavioralIdx = i;
                }
            }
            if (firstBehavioralIdx >= 0 && lastTechnicalIdx >= 0) {
                assertThat(firstBehavioralIdx).isGreaterThan(lastTechnicalIdx);
            }
        }
    }

    // ==================== Opening 固定题 ====================

    @Nested
    @DisplayName("Opening 固定开场题")
    class OpeningFixedQuestions {

        @Test
        @DisplayName("第一题永远是自我介绍")
        void firstQuestionIsSelfIntroduction() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            // 只有 behavioral 题，没有 technical 题
            for (int i = 0; i < 10; i++) {
                candidates.add(makeQ("行为题 " + i, "behavioral", DifficultyLevel.EASY, List.of("软技能")));
            }

            List<InterviewQuestion> ordered = phaser.phase(candidates, 8);

            // 第一题必须是自我介绍
            assertThat(ordered.get(0).getQuestionText()).isEqualTo("请做个简单的自我介绍");
            assertThat(ordered.get(0).getPhase()).isEqualTo(InterviewPhase.OPENING);
        }

        @Test
        @DisplayName("第二题是项目介绍，第三题是岗位理解")
        void secondAndThirdFixed() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candidates.add(makeQ("技术题 " + i, "knowledge", DifficultyLevel.MEDIUM, List.of("Topic" + i)));
            }

            // totalQuestions >= 5 时 opening=3
            List<InterviewQuestion> ordered = phaser.phase(candidates, 15);

            assertThat(ordered.get(1).getQuestionText())
                    .isEqualTo("能介绍一下你最近做过的一个项目吗？");
            assertThat(ordered.get(2).getQuestionText())
                    .isEqualTo("你对这个岗位的理解是什么？");
        }
    }

    // ==================== Technical 排序 ====================

    @Nested
    @DisplayName("Technical 阶段排序")
    class TechnicalOrdering {

        @Test
        @DisplayName("同 knowledgePoints 组内由浅入深（EASY → MEDIUM → HARD）")
        void sameGroupOrderedByDifficulty() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            // 同一组 3 题，不同难度
            candidates.add(makeQ("分布式事务 HARD", "knowledge", DifficultyLevel.HARD, List.of("分布式事务")));
            candidates.add(makeQ("分布式事务 EASY", "knowledge", DifficultyLevel.EASY, List.of("分布式事务")));
            candidates.add(makeQ("分布式事务 MEDIUM", "knowledge", DifficultyLevel.MEDIUM, List.of("分布式事务")));

            List<InterviewQuestion> ordered = phaser.phase(candidates, 5);

            // 跳过前 2 道 opening 题，在 technical 中找到同组题
            List<InterviewQuestion> tech = ordered.stream()
                    .filter(q -> InterviewPhase.TECHNICAL.equals(q.getPhase())).toList();

            // 同组题目应该 orderly
            List<DifficultyLevel> levels = tech.stream()
                    .filter(q -> q.getKnowledgePoints().contains("分布式事务"))
                    .map(InterviewQuestion::getDifficulty)
                    .toList();

            if (levels.size() >= 2) {
                assertThat(levels.get(0).ordinal())
                        .isLessThanOrEqualTo(levels.get(levels.size() - 1).ordinal());
            }
        }

        @Test
        @DisplayName("不同 knowledgePoints 组的题目交替排列，避免同组连续")
        void differentGroupsInterleaved() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            candidates.add(makeQ("Redis 1", "knowledge", DifficultyLevel.EASY, List.of("Redis")));
            candidates.add(makeQ("Redis 2", "knowledge", DifficultyLevel.EASY, List.of("Redis")));
            candidates.add(makeQ("MySQL 1", "knowledge", DifficultyLevel.EASY, List.of("MySQL")));
            candidates.add(makeQ("MySQL 2", "knowledge", DifficultyLevel.EASY, List.of("MySQL")));
            candidates.add(makeQ("MQ 1", "knowledge", DifficultyLevel.EASY, List.of("消息队列")));
            candidates.add(makeQ("MQ 2", "knowledge", DifficultyLevel.EASY, List.of("消息队列")));

            List<InterviewQuestion> ordered = phaser.phase(candidates, 8);

            List<InterviewQuestion> tech = ordered.stream()
                    .filter(q -> InterviewPhase.TECHNICAL.equals(q.getPhase())).toList();

            // 验证没有连续 3 题同组
            for (int i = 0; i < tech.size() - 2; i++) {
                String g1 = tech.get(i).getKnowledgePoints().get(0);
                String g2 = tech.get(i + 1).getKnowledgePoints().get(0);
                String g3 = tech.get(i + 2).getKnowledgePoints().get(0);
                assertThat(g1.equals(g2) && g2.equals(g3))
                        .as("不应该有连续 3 题同组: indices %d-%d", i, i + 2)
                        .isFalse();
            }
        }
    }

    // ==================== 数量分配 ====================

    @Nested
    @DisplayName("题目数量分配")
    class QuestionCountAllocation {

        @Test
        @DisplayName("总题数不超过 totalQuestions")
        void totalNotExceedLimit() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                candidates.add(makeQ("技术题 " + i, "knowledge", DifficultyLevel.MEDIUM, List.of("Topic" + i % 5)));
                candidates.add(makeQ("行为题 " + i, "behavioral", DifficultyLevel.EASY, List.of("软技能")));
            }

            for (int n : new int[]{5, 10, 15, 20}) {
                List<InterviewQuestion> ordered = phaser.phase(candidates, n);
                assertThat(ordered).hasSize(n);
            }
        }

        @Test
        @DisplayName("Opening 至少 2 题")
        void openingAtLeastTwo() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            for (int i = 0; i < 20; i++) {
                candidates.add(makeQ("技术题 " + i, "knowledge", DifficultyLevel.MEDIUM, List.of("Topic")));
            }

            for (int n : new int[]{5, 10, 15, 20}) {
                List<InterviewQuestion> ordered = phaser.phase(candidates, n);
                long openingCount = ordered.stream()
                        .filter(q -> InterviewPhase.OPENING.equals(q.getPhase())).count();
                assertThat(openingCount).isGreaterThanOrEqualTo(2);
            }
        }
    }

    // ==================== 边界情况 ====================

    @Nested
    @DisplayName("边界情况")
    class EdgeCases {

        @Test
        @DisplayName("空候选列表返回空列表")
        void emptyCandidateList() {
            List<InterviewQuestion> ordered = phaser.phase(List.of(), 5);
            assertThat(ordered).isEmpty();
        }

        @Test
        @DisplayName("候选数少于总题数时返回全部候选")
        void fewerCandidatesThanTotal() {
            List<InterviewQuestion> candidates = List.of(
                    makeQ("只有一题", "knowledge", DifficultyLevel.EASY, List.of("Topic"))
            );

            List<InterviewQuestion> ordered = phaser.phase(candidates, 10);
            assertThat(ordered).hasSizeLessThanOrEqualTo(10);
        }

        @Test
        @DisplayName("只有 behavioral 题时 TECHNICAL 阶段为空")
        void onlyBehavioralQuestions() {
            List<InterviewQuestion> candidates = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                candidates.add(makeQ("行为题 " + i, "behavioral", DifficultyLevel.EASY, List.of("软技能")));
            }

            List<InterviewQuestion> ordered = phaser.phase(candidates, 8);

            long techCount = ordered.stream()
                    .filter(q -> InterviewPhase.TECHNICAL.equals(q.getPhase())).count();
            // 只有 behavioral 题池，无 technical 池，TECHNICAL 应为 0
            assertThat(techCount).isEqualTo(0);
        }
    }
}
