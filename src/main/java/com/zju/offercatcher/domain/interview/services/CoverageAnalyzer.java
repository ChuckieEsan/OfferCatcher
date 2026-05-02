package com.zju.offercatcher.domain.interview.services;

import com.zju.offercatcher.domain.interview.valueobjects.CandidateQuestion;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;

import java.util.*;
import java.util.stream.Collectors;

/**
 * JD 技能覆盖分析器。
 *
 * 检查推荐结果是否覆盖了 JD 的 requiredSkills，输出覆盖报告。
 */
public class CoverageAnalyzer {

    /**
     * 覆盖状态：单个 JD 技能的匹配结果。
     */
    public record SkillCoverage(
        String skillName,
        String requiredLevel,
        boolean covered,
        int matchCount,
        List<String> questionTexts
    ) {}

    /**
     * 覆盖报告。
     */
    public record CoverageReport(
        int totalSkills,
        int coveredSkills,
        int missingSkills,
        List<SkillCoverage> details,
        double coverageRate
    ) {
        public List<SkillCoverage> missing() {
            return details.stream().filter(s -> !s.covered).toList();
        }

        public boolean isFullyCovered() {
            return missingSkills == 0;
        }
    }

    /**
     * 分析推荐结果对 JD requiredSkills 的覆盖情况。
     */
    public CoverageReport analyze(List<CandidateQuestion> results,
                                   List<SkillRequirement> requiredSkills) {
        if (requiredSkills.isEmpty()) {
            return new CoverageReport(0, 0, 0, List.of(), 1.0);
        }

        // 按 matchedSkillName 分组
        Map<String, List<CandidateQuestion>> bySkill = results.stream()
            .filter(c -> c.matchedSkillName() != null)
            .collect(Collectors.groupingBy(CandidateQuestion::matchedSkillName));

        List<SkillCoverage> details = new ArrayList<>();
        int covered = 0;

        for (SkillRequirement skill : requiredSkills) {
            List<CandidateQuestion> matches = bySkill.getOrDefault(skill.name(), List.of());
            boolean hasMatch = !matches.isEmpty();
            if (hasMatch) covered++;

            details.add(new SkillCoverage(
                skill.name(),
                skill.level(),
                hasMatch,
                matches.size(),
                matches.stream().map(CandidateQuestion::questionText)
                    .map(this::truncate).toList()
            ));
        }

        int missing = requiredSkills.size() - covered;
        return new CoverageReport(
            requiredSkills.size(), covered, missing, details,
            (double) covered / requiredSkills.size()
        );
    }

    private String truncate(String s) {
        return s != null && s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
