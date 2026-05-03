package com.zju.offercatcher.domain.interview.services;

import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.services.CoverageAnalyzer.CoverageReport;
import com.zju.offercatcher.domain.interview.valueobjects.CandidateQuestion;
import com.zju.offercatcher.domain.interview.valueobjects.RecallChannel;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.question.valueobjects.QuestionWithScore;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * JD 驱动出题推荐 Pipeline。
 * <p>
 * 串联召回 → 排序 → 重排（MMR + 覆盖约束）→ 截断。
 * 纯领域逻辑，不依赖框架。嵌入计算通过函数接口注入。
 */
public class RecommendationPipeline {

    private static final double MMR_LAMBDA = 0.7;

    private final QuestionRepository questionRepository;
    private final Function<String, float[]> embedder;
    private final Function<SkillRequirement, List<String>> keywordExpander;

    public RecommendationPipeline(QuestionRepository questionRepository,
                                  Function<String, float[]> embedder,
                                  Function<SkillRequirement, List<String>> keywordExpander) {
        this.questionRepository = questionRepository;
        this.embedder = embedder;
        this.keywordExpander = keywordExpander;
    }

    /**
     * 执行完整推荐。
     */
    public List<CandidateQuestion> recommend(JobDescription jd, String userId, int totalQuestions) {
        // 1. 召回
        Map<Long, CandidateQuestion> candidates = new LinkedHashMap<>();

        // 通道1+2: 对每个 JD skill 做精确+语义召回
        for (SkillRequirement skill : jd.getRequiredSkills()) {
            recallChannel1(userId, skill, candidates);
            recallChannel2(userId, skill, candidates);
        }
        for (SkillRequirement skill : jd.getPreferredSkills()) {
            recallChannel1(userId, skill, candidates);
            recallChannel2(userId, skill, candidates);
        }

        // 通道3: 泛召回兜底
        recallChannel3(userId, jd.getCompany(), jd.getPosition(), candidates);

        if (candidates.isEmpty()) return List.of();

        // 2. 排序
        List<CandidateQuestion> scored = new ArrayList<>(candidates.values());
        scored.sort(Comparator.comparingDouble(CandidateQuestion::combinedScore).reversed());

        // 3. MMR 重排 + 覆盖约束
        List<CandidateQuestion> reranked = mmrRerank(scored, jd.getRequiredSkills(), totalQuestions);

        return reranked;
    }

    /**
     * 推荐并输出覆盖报告。
     */
    public RecommendationResult recommendWithCoverage(JobDescription jd, String userId,
                                                      int totalQuestions) {
        List<CandidateQuestion> questions = recommend(jd, userId, totalQuestions);
        CoverageAnalyzer analyzer = new CoverageAnalyzer();
        CoverageReport report = analyzer.analyze(questions, jd.getRequiredSkills());
        return new RecommendationResult(questions, report);
    }

    public record RecommendationResult(List<CandidateQuestion> questions, CoverageReport coverage) {
    }

    // ==================== 召回通道 ====================

    /**
     * 通道1: pg_trgm 三元组相似度 + 查询改写
     */
    private void recallChannel1(String userId, SkillRequirement skill,
                                Map<Long, CandidateQuestion> candidates) {
        Set<String> seen = new HashSet<>();
        for (String keyword : expandKeywords(skill)) {
            List<Question> hits = questionRepository.findByCoreEntitySimilar(userId, keyword, 3);
            for (Question q : hits) {
                if (seen.add(q.getId().toString())) {
                    candidates.putIfAbsent(q.getId(), new CandidateQuestion(
                            q.getId(), q.getQuestionText(), q.getQuestionType().getValue(),
                            DifficultyLevel.MEDIUM, q.getCoreEntities(),
                            0.9, jaccard(skill, q.getCoreEntities()),
                            RecallChannel.PG_TRGM.getWeight(), RecallChannel.PG_TRGM, skill.name(),
                            q.getMasteryLevel().getLevel()
                    ));
                }
            }
        }
    }

    /**
     * 通道2: 嵌入语义召回 + 查询改写
     */
    private void recallChannel2(String userId, SkillRequirement skill,
                                Map<Long, CandidateQuestion> candidates) {
        List<String> keywords = expandKeywords(skill);
        int limit = Math.min(3, keywords.size());
        for (int i = 0; i < limit; i++) {
            String query = "考点：" + keywords.get(i) + " | 题目：" + skill.name() + " 面试题";
            float[] vector = embedder.apply(query);
            List<QuestionWithScore> hits = questionRepository.searchUserVisible(userId, vector, 3);
            for (QuestionWithScore qs : hits) {
                Question q = qs.question();
                double cosine = qs.score();
                candidates.merge(q.getId(), new CandidateQuestion(
                        q.getId(), q.getQuestionText(), q.getQuestionType().getValue(),
                        DifficultyLevel.MEDIUM,
                        q.getCoreEntities(),
                        cosine, jaccard(skill, q.getCoreEntities()),
                        RecallChannel.SEMANTIC.getWeight(), RecallChannel.SEMANTIC, skill.name(),
                        q.getMasteryLevel().getLevel()
                ), (old, neu) -> old.combinedScore() >= neu.combinedScore() ? old : neu);
            }
        }
    }

    /**
     * 通道3: 公司+岗位泛召回兜底
     */
    private void recallChannel3(String userId, String company, String position,
                                Map<Long, CandidateQuestion> candidates) {
        String context = "公司：" + (company != null ? company : "综合")
                + " | 岗位：" + (position != null ? position : "综合") + " | 面试题";
        float[] vector = embedder.apply(context);
        List<QuestionWithScore> hits = questionRepository.searchUserVisible(userId, vector, 10);
        for (QuestionWithScore qs : hits) {
            Question q = qs.question();
            candidates.putIfAbsent(q.getId(), new CandidateQuestion(
                    q.getId(), q.getQuestionText(), q.getQuestionType().getValue(),
                    DifficultyLevel.MEDIUM,
                    q.getCoreEntities(),
                    qs.score(), 0.0, RecallChannel.GENERAL.getWeight(), RecallChannel.GENERAL, null,
                    q.getMasteryLevel().getLevel()
            ));
        }
    }

    // ==================== 重排 ====================

    /**
     * MMR 贪婪选取 + requiredSkill 覆盖硬约束。
     */
    List<CandidateQuestion> mmrRerank(List<CandidateQuestion> scored,
                                      List<SkillRequirement> requiredSkills,
                                      int N) {
        Set<String> uncovered = requiredSkills.stream()
                .map(SkillRequirement::name).collect(Collectors.toCollection(HashSet::new));
        List<CandidateQuestion> selected = new ArrayList<>();
        List<CandidateQuestion> pool = new ArrayList<>(scored);

        while (selected.size() < N && !pool.isEmpty()) {
            CandidateQuestion best;
            // 优先满足未覆盖的 required skill
            if (!uncovered.isEmpty()) {
                best = pool.stream()
                        .filter(c -> uncovered.contains(c.matchedSkillName()))
                        .max(Comparator.comparingDouble(CandidateQuestion::combinedScore))
                        .orElse(null);
            } else {
                best = null;
            }

            // 无特定技能需求时走 MMR
            if (best == null) {
                best = selectByMMR(pool, selected);
            }

            if (best != null) {
                selected.add(best);
                pool.remove(best);
                if (best.matchedSkillName() != null) {
                    uncovered.remove(best.matchedSkillName());
                }
            } else {
                break;
            }
        }

        return selected;
    }

    private CandidateQuestion selectByMMR(List<CandidateQuestion> pool,
                                          List<CandidateQuestion> selected) {
        CandidateQuestion best = null;
        double bestMMR = Double.NEGATIVE_INFINITY;
        String lastType = selected.isEmpty() ? null
                : selected.get(selected.size() - 1).questionType();

        for (CandidateQuestion c : pool) {
            // 题型打散：跳过与上一题同类型
            if (lastType != null && lastType.equals(c.questionType()) && pool.size() > 2) {
                continue;
            }
            double maxSim = selected.isEmpty() ? 0
                    : selected.stream()
                    .mapToDouble(s -> jaccardImpl(s.coreEntities(), c.coreEntities()))
                    .max().orElse(0);
            double mmr = MMR_LAMBDA * c.combinedScore() - (1 - MMR_LAMBDA) * maxSim;
            if (mmr > bestMMR) {
                bestMMR = mmr;
                best = c;
            }
        }
        // fallback: 如果题型打散导致无可用候选，放宽限制
        if (best == null && lastType != null) {
            return selectByMMRNoTypeFilter(pool, selected);
        }
        return best;
    }

    private CandidateQuestion selectByMMRNoTypeFilter(List<CandidateQuestion> pool,
                                                      List<CandidateQuestion> selected) {
        CandidateQuestion best = null;
        double bestMMR = Double.NEGATIVE_INFINITY;
        for (CandidateQuestion c : pool) {
            double maxSim = selected.isEmpty() ? 0
                    : selected.stream()
                    .mapToDouble(s -> jaccardImpl(s.coreEntities(), c.coreEntities()))
                    .max().orElse(0);
            double mmr = MMR_LAMBDA * c.combinedScore() - (1 - MMR_LAMBDA) * maxSim;
            if (mmr > bestMMR) {
                bestMMR = mmr;
                best = c;
            }
        }
        return best;
    }

    // ==================== 工具方法 ====================

    private List<String> expandKeywords(SkillRequirement skill) {
        if (keywordExpander != null) {
            List<String> expanded = keywordExpander.apply(skill);
            if (expanded != null && !expanded.isEmpty()) {
                Set<String> unique = new LinkedHashSet<>(expanded);
                unique.add(skill.name()); // 确保原始 skill name 始终包含
                return new ArrayList<>(unique);
            }
        }
        return List.of(skill.name());
    }

    static double jaccard(SkillRequirement skill, List<String> entities) {
        Set<String> skillTerms = new HashSet<>();
        skillTerms.add(skill.name());
        if (skill.evidence() != null) {
            for (String word : skill.evidence().split("[，,、\\s]+")) {
                if (word.length() >= 2) skillTerms.add(word);
            }
        }
        return jaccardImpl(skillTerms, new HashSet<>(entities));
    }

    static double jaccardImpl(Collection<String> a, Collection<String> b) {
        if (a.isEmpty() || b.isEmpty()) return 0;
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return (double) intersection.size() / union.size();
    }
}
