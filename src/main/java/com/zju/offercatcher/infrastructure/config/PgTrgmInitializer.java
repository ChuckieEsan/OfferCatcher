package com.zju.offercatcher.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 确保 pg_trgm 扩展和索引存在。
 *
 * pg_trgm 基于三元组（trigram）做模糊匹配，比 ILIKE '%keyword%' 更精准。
 * JD skill "分布式事务" 能通过 similarity() 匹配到标签 "分布式系统"。
 */
@Component
public class PgTrgmInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PgTrgmInitializer.class);

    private final JdbcTemplate jdbc;

    public PgTrgmInitializer(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(String... args) {
        try {
            jdbc.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm");
            log.info("pg_trgm extension enabled");
        } catch (Exception e) {
            log.warn("Failed to create pg_trgm extension: {}", e.getMessage());
            return;
        }

        try {
            jdbc.execute("""
                CREATE INDEX IF NOT EXISTS idx_question_entities_trgm
                ON question_entities USING GIN (core_entities gin_trgm_ops)
                """);
            log.info("pg_trgm index created on question_entities.core_entities");
        } catch (Exception e) {
            log.warn("Failed to create pg_trgm index: {}", e.getMessage());
        }
    }
}
