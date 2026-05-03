package com.zju.offercatcher.domain.interview.aggregates;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
import com.zju.offercatcher.domain.shared.SnowflakeIdGenerator;
import com.zju.offercatcher.domain.shared.exception.DomainException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 岗位描述（JD）聚合根
 * <p>
 * 存储原始 JD 文本和 LLM 解析后的结构化技能要求。
 * 一个 JD 可以被多次面试复用，关联一对一 InterviewSession。
 */
public class JobDescription {

    private final Long id;
    private final String userId;
    private final String rawText;
    private List<SkillRequirement> requiredSkills;
    private List<SkillRequirement> preferredSkills;
    private List<String> softSkills;
    private String company;
    private String position;
    private String experienceRequirement;
    private final LocalDateTime createdAt;

    public static JobDescription create(String userId, String rawText) {
        validate(userId, rawText);
        Long id = SnowflakeIdGenerator.generate();
        return new JobDescription(id, userId, rawText, new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), null, null, null, LocalDateTime.now());
    }

    public static JobDescription rebuild(Long id, String userId, String rawText,
                                         List<SkillRequirement> requiredSkills,
                                         List<SkillRequirement> preferredSkills,
                                         List<String> softSkills,
                                         String company, String position,
                                         String experienceRequirement,
                                         LocalDateTime createdAt) {
        return new JobDescription(id, userId, rawText,
                requiredSkills != null ? new ArrayList<>(requiredSkills) : new ArrayList<>(),
                preferredSkills != null ? new ArrayList<>(preferredSkills) : new ArrayList<>(),
                softSkills != null ? new ArrayList<>(softSkills) : new ArrayList<>(),
                company, position, experienceRequirement, createdAt);
    }

    public void updateParsedResult(List<SkillRequirement> requiredSkills,
                                   List<SkillRequirement> preferredSkills,
                                   List<String> softSkills,
                                   String company, String position,
                                   String experienceRequirement) {
        this.requiredSkills = requiredSkills != null ? requiredSkills : new ArrayList<>();
        this.preferredSkills = preferredSkills != null ? preferredSkills : new ArrayList<>();
        this.softSkills = softSkills != null ? softSkills : new ArrayList<>();
        this.company = company;
        this.position = position;
        this.experienceRequirement = experienceRequirement;
    }

    @JsonIgnore
    public String toInterviewContext() {
        StringBuilder sb = new StringBuilder();
        if (company != null && !company.isBlank()) {
            sb.append("目标公司：").append(company).append("\n");
        }
        if (position != null && !position.isBlank()) {
            sb.append("目标岗位：").append(position).append("\n");
        }
        if (experienceRequirement != null && !experienceRequirement.isBlank()) {
            sb.append("经验要求：").append(experienceRequirement).append("\n");
        }
        if (!requiredSkills.isEmpty()) {
            sb.append("必须掌握的技能：\n");
            for (SkillRequirement s : requiredSkills) {
                sb.append("  - ").append(s.name())
                        .append("（").append(s.level()).append("）");
                if (s.evidence() != null && !s.evidence().isBlank()) {
                    sb.append("：").append(s.evidence());
                }
                sb.append("\n");
            }
        }
        if (!preferredSkills.isEmpty()) {
            sb.append("加分技能：\n");
            for (SkillRequirement s : preferredSkills) {
                sb.append("  - ").append(s.name())
                        .append("（").append(s.level()).append("）\n");
            }
        }
        if (!softSkills.isEmpty()) {
            sb.append("软技能要求：").append(String.join("、", softSkills)).append("\n");
        }
        return sb.toString();
    }

    public boolean isOwnedBy(String requestingUserId) {
        return this.userId.equals(requestingUserId);
    }

    private static void validate(String userId, String rawText) {
        if (userId == null || userId.isBlank()) {
            throw new DomainException("userId 不能为空", "INVALID_USER_ID");
        }
        if (rawText == null || rawText.isBlank()) {
            throw new DomainException("JD 文本不能为空", "INVALID_JD_TEXT");
        }
    }

    // ==================== Getters ====================

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getRawText() {
        return rawText;
    }

    public List<SkillRequirement> getRequiredSkills() {
        return Collections.unmodifiableList(requiredSkills);
    }

    public List<SkillRequirement> getPreferredSkills() {
        return Collections.unmodifiableList(preferredSkills);
    }

    public List<String> getSoftSkills() {
        return Collections.unmodifiableList(softSkills);
    }

    public String getCompany() {
        return company;
    }

    public String getPosition() {
        return position;
    }

    public String getExperienceRequirement() {
        return experienceRequirement;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    // ==================== Constructor ====================

    private JobDescription(Long id, String userId, String rawText,
                           List<SkillRequirement> requiredSkills,
                           List<SkillRequirement> preferredSkills,
                           List<String> softSkills,
                           String company, String position,
                           String experienceRequirement, LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.rawText = rawText;
        this.requiredSkills = requiredSkills;
        this.preferredSkills = preferredSkills;
        this.softSkills = softSkills;
        this.company = company;
        this.position = position;
        this.experienceRequirement = experienceRequirement;
        this.createdAt = createdAt;
    }
}
