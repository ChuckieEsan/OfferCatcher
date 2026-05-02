package com.zju.offercatcher.domain.interview.services;

import com.zju.offercatcher.domain.interview.services.CoverageAnalyzer.CoverageReport;
import com.zju.offercatcher.domain.interview.services.CoverageAnalyzer.SkillCoverage;
import com.zju.offercatcher.domain.interview.valueobjects.CandidateQuestion;
import com.zju.offercatcher.domain.interview.valueobjects.RecallChannel;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CoverageAnalyzer 单元测试")
class CoverageAnalyzerTest {

    private final CoverageAnalyzer analyzer = new CoverageAnalyzer();

    private static CandidateQuestion makeQ(String text, String type, String skill) {
        return new CandidateQuestion(
            (long) text.hashCode(), text, type,
            DifficultyLevel.MEDIUM, List.of(skill),
            0.9, 0.5, 1.0, RecallChannel.PG_TRGM, skill, 0
        );
    }

    @Test
    @DisplayName("全部覆盖 → coverageRate = 1.0")
    void fullCoverage() {
        List<CandidateQuestion> results = List.of(
            makeQ("RAG 原理", "KNOWLEDGE", "RAG"),
            makeQ("Agent 架构", "KNOWLEDGE", "Agent"),
            makeQ("MCP 协议", "KNOWLEDGE", "MCP")
        );
        List<SkillRequirement> required = List.of(
            new SkillRequirement("RAG", "proficient", ""),
            new SkillRequirement("Agent", "proficient", ""),
            new SkillRequirement("MCP", "familiar", "")
        );

        CoverageReport report = analyzer.analyze(results, required);

        assertThat(report.coverageRate()).isEqualTo(1.0);
        assertThat(report.isFullyCovered()).isTrue();
        assertThat(report.missingSkills()).isEqualTo(0);
    }

    @Test
    @DisplayName("部分覆盖 → coverageRate < 1.0，missing 非空")
    void partialCoverage() {
        List<CandidateQuestion> results = List.of(
            makeQ("RAG 原理", "KNOWLEDGE", "RAG")
        );
        List<SkillRequirement> required = List.of(
            new SkillRequirement("RAG", "proficient", ""),
            new SkillRequirement("Agent", "proficient", ""),
            new SkillRequirement("MCP", "familiar", "")
        );

        CoverageReport report = analyzer.analyze(results, required);

        assertThat(report.coverageRate()).isLessThan(1.0);
        assertThat(report.isFullyCovered()).isFalse();
        assertThat(report.missing()).hasSize(2);
        assertThat(report.missing().stream().map(SkillCoverage::skillName))
            .contains("Agent", "MCP");
    }

    @Test
    @DisplayName("空 requiredSkills → coverageRate = 1.0")
    void emptyRequiredSkills() {
        CoverageReport report = analyzer.analyze(List.of(), List.of());
        assertThat(report.coverageRate()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("空结果 → 全部 missing")
    void emptyResults() {
        List<SkillRequirement> required = List.of(
            new SkillRequirement("RAG", "proficient", "")
        );
        CoverageReport report = analyzer.analyze(List.of(), required);

        assertThat(report.coverageRate()).isEqualTo(0.0);
        assertThat(report.missing()).hasSize(1);
    }

    @Test
    @DisplayName("details 包含每题数量")
    void detailsIncludeMatchCount() {
        List<CandidateQuestion> results = List.of(
            makeQ("RAG 题1", "KNOWLEDGE", "RAG"),
            makeQ("RAG 题2", "SCENARIO", "RAG"),
            makeQ("Agent 题1", "KNOWLEDGE", "Agent")
        );
        List<SkillRequirement> required = List.of(
            new SkillRequirement("RAG", "proficient", "")
        );

        CoverageReport report = analyzer.analyze(results, required);
        SkillCoverage detail = report.details().get(0);

        assertThat(detail.skillName()).isEqualTo("RAG");
        assertThat(detail.covered()).isTrue();
        assertThat(detail.matchCount()).isEqualTo(2);
    }
}
