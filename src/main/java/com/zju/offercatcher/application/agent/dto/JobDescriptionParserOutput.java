package com.zju.offercatcher.application.agent.dto;

import java.util.List;

public record JobDescriptionParserOutput(
        String company,
        String position,
        String experienceRequirement,
        List<SkillItem> requiredSkills,
        List<SkillItem> preferredSkills,
        List<String> softSkills
) {
    public static final JobDescriptionParserOutput DEFAULT = new JobDescriptionParserOutput(
            null, null, null, List.of(), List.of(), List.of());

    public record SkillItem(
            String name,
            String level,
            String evidence
    ) {
        public SkillItem() {
            this(null, "familiar", "");
        }
    }
}
