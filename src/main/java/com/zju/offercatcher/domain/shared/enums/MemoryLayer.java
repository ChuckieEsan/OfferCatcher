package com.zju.offercatcher.domain.shared.enums;

/**
 * 记忆层级枚举
 * <p>
 * STM (Short-term Memory): 短期记忆，可能随时间衰减
 * LTM (Long-term Memory): 长期记忆，长期保留，不衰减
 */
public enum MemoryLayer {
    /**
     * 短期记忆：随时间衰减，重要性较低时可能被删除
     */
    STM("short_term", "短期记忆"),

    /**
     * 长期记忆：长期保留，不衰减
     */
    LTM("long_term", "长期记忆");

    private final String value;
    private final String description;

    MemoryLayer(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String getValue() {
        return value;
    }

    public String getDescription() {
        return description;
    }

    public static MemoryLayer fromValue(String value) {
        for (MemoryLayer layer : values()) {
            if (layer.value.equals(value)) {
                return layer;
            }
        }
        throw new IllegalArgumentException("Unknown memory layer: " + value);
    }

    /**
     * 判断是否为长期记忆
     */
    public boolean isLongTerm() {
        return this == LTM;
    }

    /**
     * 判断是否为短期记忆
     */
    public boolean isShortTerm() {
        return this == STM;
    }
}