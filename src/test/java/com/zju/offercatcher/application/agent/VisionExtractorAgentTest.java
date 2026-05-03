package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.domain.question.valueobjects.ExtractedQuestionItem;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("test")
@Tag("api")
class VisionExtractorAgentTest {

    private static final Logger log = LoggerFactory.getLogger(VisionExtractorAgentTest.class);
    private static final Path MJ_FILE = Path.of("data/test_data/示例面经内容.txt");

    @Autowired
    private VisionExtractorAgent extractor;

    @BeforeAll
    static void checkApiKey() {
        String key = System.getenv("DEEPSEEK_API_KEY");
        assumeTrue(key != null && !key.isBlank(),
                "跳过：缺少 DEEPSEEK_API_KEY 环境变量");
    }

    @Test
    @DisplayName("解析阿里国际AI应用研发面经 — 验证题目提取与改写质量")
    void parseAlibabaInterviewExperience() throws IOException {
        String rawText = Files.readString(MJ_FILE);
        assertThat(rawText).isNotBlank();

        log.info("=== 面经原文（前300字）===\n{}...", rawText.substring(0, Math.min(300, rawText.length())));

        ExtractedQuestionItem result = extractor.extract(rawText);

        // ---- 先全部输出再断言，方便诊断 ----
        log.info("=== 提取结果 ===");
        log.info("公司: {}", result.company());
        log.info("岗位: {}", result.position());

        List<ExtractedQuestionItem.QuestionItem> questions = result.questions();
        log.info("提取题目 ({} 个):", questions.size());
        for (int i = 0; i < questions.size(); i++) {
            ExtractedQuestionItem.QuestionItem q = questions.get(i);
            log.info("  [{}] type={} | {} ", i + 1, q.questionType(), q.questionText());
            log.info("      entities={} | meta={}", q.coreEntities(), q.metadata());
        }

        long knowledgeCount = questions.stream()
                .filter(q -> "knowledge".equals(q.questionType())).count();
        long projectCount = questions.stream()
                .filter(q -> "project".equals(q.questionType())).count();
        long algorithmCount = questions.stream()
                .filter(q -> "algorithm".equals(q.questionType())).count();
        long behavioralCount = questions.stream()
                .filter(q -> "behavioral".equals(q.questionType())).count();
        long scenarioCount = questions.stream()
                .filter(q -> "scenario".equals(q.questionType())).count();
        log.info("分类统计: knowledge={}, project={}, algorithm={}, behavioral={}, scenario={}",
                knowledgeCount, projectCount, algorithmCount, behavioralCount, scenarioCount);

        long withOriginalText = questions.stream()
                .filter(q -> q.metadata() != null && q.metadata().containsKey("original_text"))
                .count();
        log.info("含 original_text 的题目: {}/{}", withOriginalText, questions.size());

        // ---- 题目提取数量 ----
        assertThat(questions)
                .as("应提取出至少 10 道题目（原文有 14 道）")
                .hasSizeGreaterThanOrEqualTo(10);

        // ---- 题目不重复 ----
        long uniqueCount = questions.stream()
                .map(ExtractedQuestionItem.QuestionItem::questionHash)
                .distinct()
                .count();
        assertThat(uniqueCount)
                .as("所有题目的 questionHash 不应重复")
                .isEqualTo(questions.size());

        // ---- 题目分类覆盖 ----
        assertThat(knowledgeCount)
                .as("至少应有 3 道 knowledge 类题目（HashMap、JVM、Zset等八股文）")
                .isGreaterThanOrEqualTo(3);

        assertThat(projectCount)
                .as("至少应有 1 道 project 类题目（项目背景、RabbitMQ流程等）")
                .isGreaterThanOrEqualTo(1);

        assertThat(algorithmCount)
                .as("至少应有 1 道 algorithm 类题目（合并重叠区间）")
                .isGreaterThanOrEqualTo(1);

        // ---- 题目改写质量（每条都有非空文本）----
        assertThat(questions)
                .as("所有题目 text 不为空")
                .allMatch(q -> q.questionText() != null && !q.questionText().isBlank());

        assertThat(questions)
                .as("所有题目 entities 不为 null")
                .allMatch(q -> q.coreEntities() != null);

        // ---- 核心实体覆盖 ----
        long withEntities = questions.stream()
                .filter(q -> !q.coreEntities().isEmpty())
                .count();
        log.info("含 core_entities 的题目: {}/{}", withEntities, questions.size());
        assertThat(withEntities)
                .as("至少一半题目应有核心实体标注")
                .isGreaterThanOrEqualTo(questions.size() / 2);

        // ---- 公司/岗位：尽力提取，至少不为 null ----
        assertThat(result.company()).isNotNull();
        assertThat(result.position()).isNotNull();
    }
}
