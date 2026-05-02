package com.zju.offercatcher.application.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 简历分析 Agent 结构化输出。
 */
public record ResumeAnalysisOutput(
    @JsonProperty("projects") List<ProjectItem> projects,
    @JsonProperty("techStack") List<String> techStack,
    @JsonProperty("yearsOfExperience") String yearsOfExperience,
    @JsonProperty("education") String education
) {
    public record ProjectItem(
        @JsonProperty("name") String name,
        @JsonProperty("role") String role,
        @JsonProperty("techStack") List<String> techStack,
        @JsonProperty("highlights") List<String> highlights
    ) {}

    public String toInterviewContext() {
        StringBuilder sb = new StringBuilder("<候选人简历>\n");

        sb.append("技术栈：").append(String.join("、", techStack)).append("\n");
        if (yearsOfExperience != null && !yearsOfExperience.isBlank()) {
            sb.append("工作经验：").append(yearsOfExperience).append("\n");
        }
        if (education != null && !education.isBlank()) {
            sb.append("学历：").append(education).append("\n");
        }

        if (!projects.isEmpty()) {
            sb.append("项目经历：\n");
            for (ProjectItem p : projects) {
                sb.append("  - ").append(p.name());
                if (p.role() != null) sb.append("（角色：").append(p.role()).append("）");
                sb.append("\n");
                if (p.techStack() != null && !p.techStack().isEmpty()) {
                    sb.append("    技术栈：").append(String.join("、", p.techStack())).append("\n");
                }
                if (p.highlights() != null && !p.highlights().isEmpty()) {
                    for (String h : p.highlights()) {
                        sb.append("    · ").append(h).append("\n");
                    }
                }
            }
        }

        sb.append("</候选人简历>\n");
        return sb.toString();
    }
}
