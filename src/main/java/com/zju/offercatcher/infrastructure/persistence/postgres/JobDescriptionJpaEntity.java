package com.zju.offercatcher.infrastructure.persistence.postgres;

import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
// SkillRequirementListConverter is in the same package
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "job_descriptions", indexes = {
    @Index(name = "idx_jd_user_created", columnList = "user_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class JobDescriptionJpaEntity {

    @Id
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "raw_text", columnDefinition = "text", nullable = false)
    private String rawText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_skills", columnDefinition = "jsonb")
    @Convert(converter = SkillRequirementListConverter.class)
    private List<SkillRequirement> requiredSkills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "preferred_skills", columnDefinition = "jsonb")
    @Convert(converter = SkillRequirementListConverter.class)
    private List<SkillRequirement> preferredSkills;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "soft_skills", columnDefinition = "jsonb")
    @Convert(converter = StringListConverter.class)
    private List<String> softSkills;

    @Column(name = "company")
    private String company;

    @Column(name = "position")
    private String position;

    @Column(name = "experience_requirement")
    private String experienceRequirement;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static JobDescriptionJpaEntity fromDomain(JobDescription jd) {
        JobDescriptionJpaEntity e = new JobDescriptionJpaEntity();
        e.setId(jd.getId());
        e.setUserId(jd.getUserId());
        e.setRawText(jd.getRawText());
        e.setRequiredSkills(jd.getRequiredSkills());
        e.setPreferredSkills(jd.getPreferredSkills());
        e.setSoftSkills(jd.getSoftSkills());
        e.setCompany(jd.getCompany());
        e.setPosition(jd.getPosition());
        e.setExperienceRequirement(jd.getExperienceRequirement());
        e.setCreatedAt(jd.getCreatedAt());
        return e;
    }

    public JobDescription toDomain() {
        return JobDescription.rebuild(
            id, userId, rawText,
            requiredSkills, preferredSkills, softSkills,
            company, position, experienceRequirement,
            createdAt
        );
    }
}
