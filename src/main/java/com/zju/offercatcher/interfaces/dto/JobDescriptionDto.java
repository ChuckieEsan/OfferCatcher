package com.zju.offercatcher.interfaces.dto;

import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;

import java.time.LocalDateTime;
import java.util.List;

public record JobDescriptionDto(
    Long id,
    String company,
    String position,
    String experienceRequirement,
    List<SkillRequirement> requiredSkills,
    List<SkillRequirement> preferredSkills,
    List<String> softSkills,
    String rawText,
    LocalDateTime createdAt
) {
    public static JobDescriptionDto from(JobDescription jd) {
        return new JobDescriptionDto(
            jd.getId(),
            jd.getCompany(),
            jd.getPosition(),
            jd.getExperienceRequirement(),
            jd.getRequiredSkills(),
            jd.getPreferredSkills(),
            jd.getSoftSkills(),
            jd.getRawText(),
            jd.getCreatedAt()
        );
    }

    public record ParseRequest(String jdText) {}
}
