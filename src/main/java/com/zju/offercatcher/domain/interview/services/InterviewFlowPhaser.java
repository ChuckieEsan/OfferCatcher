package com.zju.offercatcher.domain.interview.services;

import com.zju.offercatcher.domain.interview.entities.InterviewQuestion;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.domain.shared.enums.InterviewPhase;
import com.zju.offercatcher.domain.shared.enums.QuestionType;

import java.util.*;

/**
 * 面试流程阶段机。
 * <p>
 * 将候选题目按面试自然节奏分组排序：
 * Opening（开场热身）→ Technical（技术深挖）→ Behavioral（行为考察）
 * <p>
 * 纯领域逻辑，无框架依赖。
 */
public class InterviewFlowPhaser {

    private static final List<String> OPENING_QUESTIONS = List.of(
            "请做个简单的自我介绍",
            "能介绍一下你最近做过的一个项目吗？"
    );

    /**
     * 技术题类型：KNOWLEDGE / SCENARIO / ALGORITHM
     */
    private static boolean isTechnical(InterviewQuestion q) {
        QuestionType type = QuestionType.fromValue(q.getQuestionType());
        return type == QuestionType.KNOWLEDGE || type == QuestionType.SCENARIO
                || type == QuestionType.ALGORITHM;
    }

    /**
     * 行为题类型：BEHAVIORAL / PROJECT
     */
    private static boolean isBehavioral(InterviewQuestion q) {
        QuestionType type = QuestionType.fromValue(q.getQuestionType());
        return type == QuestionType.BEHAVIORAL || type == QuestionType.PROJECT;
    }

    /**
     * 对候选题目进行阶段分组和排序。
     *
     * @param candidates     从题库召回的全部候选题目（未打标 phase）
     * @param totalQuestions 面试总题数
     * @return 排序后的题目列表，每道题已设置 phase 标签
     */
    public List<InterviewQuestion> phase(List<InterviewQuestion> candidates, int totalQuestions) {
        if (candidates.isEmpty()) return List.of();

        // 1. 候选集分类
        List<InterviewQuestion> technicalPool = new ArrayList<>(candidates.stream()
                .filter(InterviewFlowPhaser::isTechnical).toList());
        List<InterviewQuestion> behavioralPool = new ArrayList<>(candidates.stream()
                .filter(InterviewFlowPhaser::isBehavioral).toList());

        // 2. Opening 固定 2 题（自我介绍 + 项目介绍），行为面控制在 1 题
        int openingCount = 2;
        int behavioralCount = totalQuestions >= 5 ? 1 : 0;
        int technicalCount = totalQuestions - openingCount - behavioralCount;

        // 3. 自适应：某池不够时从另一池补充
        if (technicalCount > technicalPool.size()) {
            int shortage = technicalCount - technicalPool.size();
            technicalCount = technicalPool.size();
            behavioralCount = Math.min(behavioralCount + shortage, behavioralPool.size());
        }
        if (behavioralCount > behavioralPool.size()) {
            behavioralCount = behavioralPool.size();
        }

        List<InterviewQuestion> result = new ArrayList<>();

        // 4. Opening: 固定开场题
        result.addAll(buildOpening(openingCount, behavioralPool));

        // 5. Technical: 按 knowledgePoints 分组，组内由浅入深，交替排列
        result.addAll(buildTechnical(technicalCount, technicalPool));

        // 6. Behavioral: 从剩余中选取
        result.addAll(buildBehavioral(behavioralCount, behavioralPool));

        // 7. 打标 phase（按实际数量而非预分配数量）
        int actualOpening = Math.min(openingCount, result.size());
        int actualTechnical = Math.min(technicalCount, result.size() - actualOpening);
        for (int i = 0; i < result.size(); i++) {
            if (i < actualOpening) {
                result.get(i).setPhase(InterviewPhase.OPENING);
            } else if (i < actualOpening + actualTechnical) {
                result.get(i).setPhase(InterviewPhase.TECHNICAL);
            } else {
                result.get(i).setPhase(InterviewPhase.BEHAVIORAL);
            }
        }

        return result;
    }

    // ==================== Phase Builders ====================

    /**
     * 构建 Opening 阶段。
     * 前 2 道题固定（自我介绍、项目介绍），不足时用 behavioral 补充。
     */
    private List<InterviewQuestion> buildOpening(int count, List<InterviewQuestion> behavioralPool) {
        List<InterviewQuestion> opening = new ArrayList<>();
        for (int i = 0; i < Math.min(count, OPENING_QUESTIONS.size()); i++) {
            opening.add(createFixedQuestion(OPENING_QUESTIONS.get(i)));
        }
        // 开场题不足时从 behavioral 中补
        int remaining = count - opening.size();
        if (remaining > 0) {
            List<InterviewQuestion> supplements = behavioralPool.stream()
                    .limit(remaining).toList();
            opening.addAll(supplements);
            behavioralPool.removeAll(supplements);
        }
        return opening;
    }

    /**
     * 构建 Technical 阶段。
     * 按 knowledgePoints 第一项分组，同组题目连续排列，组内 difficulty 递增。
     */
    private List<InterviewQuestion> buildTechnical(int count, List<InterviewQuestion> pool) {
        List<InterviewQuestion> techQuestions = new ArrayList<>(pool);
        if (techQuestions.size() > count) {
            techQuestions = techQuestions.subList(0, count);
        }

        // 按 knowledgePoints 第一项分组（保留插入顺序的 LinkedHashMap）
        Map<String, List<InterviewQuestion>> grouped = new LinkedHashMap<>();
        for (InterviewQuestion q : techQuestions) {
            String key = q.getKnowledgePoints().isEmpty() ? "综合" : q.getKnowledgePoints().get(0);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(q);
        }

        // 组内由浅入深排序
        for (List<InterviewQuestion> group : grouped.values()) {
            group.sort(Comparator.comparingInt(q -> difficultyOrder(q.getDifficulty())));
        }

        // 交替放置不同组的题目，而非连续排列同组
        return interleaveGroups(new ArrayList<>(grouped.values()), count);
    }

    /**
     * 构建 Behavioral 阶段。
     */
    private List<InterviewQuestion> buildBehavioral(int count, List<InterviewQuestion> pool) {
        return pool.stream().limit(count).toList();
    }

    // ==================== Helpers ====================

    private InterviewQuestion createFixedQuestion(String text) {
        return InterviewQuestion.create(
                0L, "fixed-" + text.hashCode(), text,
                "behavioral", DifficultyLevel.EASY, List.of("开场")
        );
    }

    /**
     * 不同知识分组交替排列，避免同组题目连续出现
     */
    private List<InterviewQuestion> interleaveGroups(List<List<InterviewQuestion>> groups, int maxCount) {
        List<InterviewQuestion> result = new ArrayList<>();
        int[] indices = new int[groups.size()];
        boolean added;
        do {
            added = false;
            for (int g = 0; g < groups.size() && result.size() < maxCount; g++) {
                if (indices[g] < groups.get(g).size()) {
                    result.add(groups.get(g).get(indices[g]++));
                    added = true;
                }
            }
        } while (added && result.size() < maxCount);
        return result;
    }

    private static int difficultyOrder(DifficultyLevel d) {
        return switch (d) {
            case EASY -> 0;
            case MEDIUM -> 1;
            case HARD -> 2;
        };
    }
}
