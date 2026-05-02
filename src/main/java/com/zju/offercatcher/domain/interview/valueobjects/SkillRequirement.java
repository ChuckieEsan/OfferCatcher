package com.zju.offercatcher.domain.interview.valueobjects;

/**
 * JD 技能要求值对象
 *
 * @param name     技能名称，如 "分布式事务"
 * @param level    要求等级：proficient / familiar / beginner
 * @param evidence JD 原文证据
 */
public record SkillRequirement(
    String name,
    String level,
    String evidence
) {
    public SkillRequirement {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (level == null || level.isBlank()) {
            level = "familiar";
        }
    }

    public boolean isProficient() {
        return "proficient".equalsIgnoreCase(level);
    }

    public boolean isFamiliar() {
        return "familiar".equalsIgnoreCase(level);
    }
}
