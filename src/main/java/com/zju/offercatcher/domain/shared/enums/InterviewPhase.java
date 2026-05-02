package com.zju.offercatcher.domain.shared.enums;

/**
 * 面试阶段枚举。
 *
 * 定义面试题目的阶段归属，用于流程结构化排序。
 */
public enum InterviewPhase {
    OPENING("OPENING", "开场热身"),
    TECHNICAL("TECHNICAL", "技术深挖"),
    BEHAVIORAL("BEHAVIORAL", "行为考察"),
    CLOSING("CLOSING", "收尾");

    private final String value;
    private final String label;

    InterviewPhase(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() { return value; }
    public String getLabel() { return label; }
}
