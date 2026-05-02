package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("test")
@Tag("api")
class JobDescriptionParserAgentTest {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionParserAgentTest.class);
    private static final Path JD_FILE = Path.of("data/test_data/示例岗位JD.txt");

    @Autowired
    private JobDescriptionParserAgent parser;

    @BeforeAll
    static void loadEnvAndCheckApiKey() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        dotenv.entries().forEach(e -> {
            if (System.getProperty(e.getKey()) == null) {
                System.setProperty(e.getKey(), e.getValue());
            }
        });

        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isBlank()) {
            key = System.getProperty("DEEPSEEK_API_KEY");
        }
        assumeTrue(key != null && !key.isBlank(),
            "跳过：缺少 DEEPSEEK_API_KEY（既无环境变量，.env 文件中也未找到）");
    }

    @Test
    @DisplayName("解析蚂蚁智能体实习JD — 验证结构化提取不抛异常且返回有效数据结构")
    void parseAntGroupAgentInternJD() throws IOException {
        String rawText = Files.readString(JD_FILE);
        assertThat(rawText).isNotBlank();

        log.info("=== 原始 JD（前200字）===\n{}...", rawText.substring(0, Math.min(200, rawText.length())));

        JobDescription jd = parser.parseAndSave("test-user-jd-parser", rawText);
        assertThat(jd).isNotNull();
        assertThat(jd.getId()).isNotNull();
        assertThat(jd.getRawText()).isEqualTo(rawText);

        log.info("=== 解析结果 ===");
        log.info("公司: {}", jd.getCompany());
        log.info("岗位: {}", jd.getPosition());
        log.info("经验要求: {}", jd.getExperienceRequirement());

        // ---- 必须技能 ----
        List<SkillRequirement> required = jd.getRequiredSkills();
        log.info("必须技能 ({} 个):", Optional.of(required.size()));
        for (SkillRequirement s : required) {
            log.info("  - {} [{}] evidence={}", s.name(), s.level(), s.evidence());
        }

        assertThat(required)
            .as("必须技能不应为 null")
            .isNotNull();
        assertThat(required)
            .as("必须技能应在 3-7 个之间")
            .hasSizeBetween(3, 7);
        assertThat(required)
            .as("必须技能应包含 Agent/大模型/AI 相关")
            .anyMatch(s -> containsAny(s.name(), "Agent", "大模型", "LLM", "AI", "Prompt", "RAG", "工具"));
        assertThat(required)
            .as("每个必须技能应有原文证据")
            .allMatch(s -> s.evidence() != null && !s.evidence().isBlank());

        // ---- 加分技能 ----
        List<SkillRequirement> preferred = jd.getPreferredSkills();
        log.info("加分技能 ({} 个):", Optional.of(preferred.size()));
        for (SkillRequirement s : preferred) {
            log.info("  - {} [{}] evidence={}", s.name(), s.level(), s.evidence());
        }

        assertThat(preferred)
            .as("加分技能不应为 null")
            .isNotNull();
        assertThat(preferred)
            .as("加分技能应在 0-5 个之间")
            .hasSizeBetween(0, 5);

        // ---- 软技能 ----
        List<String> soft = jd.getSoftSkills();
        log.info("软技能 ({} 个): {}", Optional.of(soft.size()), soft);

        assertThat(soft)
            .as("软技能不应为 null")
            .isNotNull();
        assertThat(soft)
            .as("软技能应在 1-4 个之间")
            .hasSizeBetween(1, 4);

        // ---- 面试上下文 ----
        String ctx = jd.toInterviewContext();
        log.info("=== 面试上下文 ===\n{}", ctx);
        assertThat(ctx).isNotBlank();
    }

    private static boolean containsAny(String s, String... keywords) {
        if (s == null) return false;
        for (String kw : keywords) {
            if (s.contains(kw)) return true;
        }
        return false;
    }
}
