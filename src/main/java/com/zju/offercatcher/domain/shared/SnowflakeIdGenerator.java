package com.zju.offercatcher.domain.shared;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import org.springframework.stereotype.Component;

/**
 * Snowflake ID 生成器
 *
 * 基于 Hutool 的 Snowflake 算法实现，生成 64 位趋势递增 Long ID。
 * 相比 UUID，Snowflake ID 在 B-tree 索引上性能更好（写入有序、占用空间小）。
 *
 * 64 位结构：1 位保留 + 41 位时间戳 + 5 位 workerId + 5 位 datacenterId + 12 位序列号
 */
@Component
public class SnowflakeIdGenerator {

    private static final Snowflake SNOWFLAKE;

    static {
        // workerId 和 datacenterId 后续可从配置中读取
        SNOWFLAKE = IdUtil.getSnowflake(1, 1);
    }

    /**
     * 生成下一个 Snowflake ID
     */
    public long nextId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 静态便捷方法（供领域工厂方法使用）
     */
    public static long generate() {
        return SNOWFLAKE.nextId();
    }
}
