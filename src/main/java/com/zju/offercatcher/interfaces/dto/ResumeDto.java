package com.zju.offercatcher.interfaces.dto;

import com.zju.offercatcher.application.agent.dto.ResumeAnalysisOutput;

import java.util.List;

public interface ResumeDto {

    record AnalysisResponse(
        List<ProjectItem> projects,
        List<String> techStack,
        String yearsOfExperience,
        String education
    ) {
        public static AnalysisResponse from(ResumeAnalysisOutput output) {
            return new AnalysisResponse(
                output.projects().stream().map(ProjectItem::from).toList(),
                output.techStack(), output.yearsOfExperience(), output.education()
            );
        }
    }

    record ProjectItem(
        String name, String role, List<String> techStack, List<String> highlights
    ) {
        static ProjectItem from(ResumeAnalysisOutput.ProjectItem p) {
            return new ProjectItem(p.name(), p.role(), p.techStack(), p.highlights());
        }
    }
}
