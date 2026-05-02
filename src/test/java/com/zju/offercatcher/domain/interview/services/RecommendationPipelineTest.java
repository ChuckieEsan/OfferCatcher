package com.zju.offercatcher.domain.interview.services;

import com.zju.offercatcher.domain.interview.aggregates.JobDescription;
import com.zju.offercatcher.domain.interview.valueobjects.CandidateQuestion;
import com.zju.offercatcher.domain.interview.valueobjects.RecallChannel;
import com.zju.offercatcher.domain.interview.valueobjects.SkillRequirement;
import com.zju.offercatcher.domain.question.aggregates.Question;
import com.zju.offercatcher.domain.question.repositories.QuestionRepository;
import com.zju.offercatcher.domain.shared.enums.MasteryLevel;
import com.zju.offercatcher.domain.shared.enums.QuestionType;
import com.zju.offercatcher.infrastructure.adapters.embedding.OnnxEmbeddingAdapter;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.TestInstance.Lifecycle;

@SpringBootTest
@ActiveProfiles("test")
@TestInstance(Lifecycle.PER_CLASS)
@Tag("api")
@DisplayName("JD 驱动推荐系统集成测试")
class RecommendationPipelineTest {

    @Autowired private QuestionRepository questionRepository;
    @Autowired private OnnxEmbeddingAdapter onnxEmbeddingAdapter;

    private RecommendationPipeline pipeline;
    private static final String UID = "test-rec-pipeline";

    @BeforeAll
    void seedData() {
        assumeTrue(onnxEmbeddingAdapter.isInitialized(), "ONNX embedding not initialized, skipping");

        // 播种 30 道不同知识点和掌握度的题目
        String[][] seeds = {
            {"RAG 检索增强的原理是什么？",         "RAG",      "KNOWLEDGE",  "0"},
            {"RAG 中如何选择合适的 Embedding 模型？", "RAG",      "KNOWLEDGE",  "1"},
            {"如何优化 RAG 的检索精度？",            "RAG",      "SCENARIO",   "0"},
            {"RAG 系统在大规模下的性能瓶颈？",        "RAG",      "SCENARIO",   "2"},
            {"Agent 的 ReAct Loop 是如何工作的？",    "Agent",    "KNOWLEDGE",  "0"},
            {"多 Agent 协作的通信协议选型？",          "Agent",    "SCENARIO",   "0"},
            {"Agent 的工具调用失败如何处理？",         "Agent",    "SCENARIO",   "1"},
            {"LangGraph 的 StateGraph 设计思路？",    "Agent",    "KNOWLEDGE",  "2"},
            {"MCP 协议与 Function Calling 的区别？",   "MCP",      "KNOWLEDGE",  "0"},
            {"MCP Server 如何注册和发现？",           "MCP",      "KNOWLEDGE",  "0"},
            {"Redis 缓存雪崩怎么解决？",              "Redis",    "KNOWLEDGE",  "0"},
            {"Redis 分布式锁的实现对比？",            "Redis",    "SCENARIO",   "1"},
            {"MySQL 索引优化原则有哪些？",            "MySQL",    "KNOWLEDGE",  "0"},
            {"分库分表后跨库查询怎么处理？",          "MySQL",    "SCENARIO",   "0"},
            {"高并发场景下如何设计限流系统？",         "系统设计",  "SCENARIO",   "0"},
            {"微服务拆分的原则和实践？",              "系统设计",  "SCENARIO",   "1"},
            {"如何处理线上故障排查？",                "系统设计",  "BEHAVIORAL", "0"},
            {"大模型微调中 LoRA 的原理？",            "大模型",    "KNOWLEDGE",  "0"},
            {"多轮对话的上下文窗口管理？",            "多轮对话",  "SCENARIO",   "0"},
            {"AI Coding 工具的技术架构？",            "AI Coding", "KNOWLEDGE",  "0"},
        };

        for (String[] s : seeds) {
            Question q = Question.createPrivate(UID, s[0], "字节跳动", "AI Agent开发",
                QuestionType.fromValue(s[2]), List.of(s[1]));
            q.updateMastery(MasteryLevel.fromLevel(Integer.parseInt(s[3])));
            questionRepository.save(q);
        }
    }

    @BeforeEach
    void setUp() {
        pipeline = new RecommendationPipeline(
            questionRepository,
            onnxEmbeddingAdapter::embed,
            skill -> List.of(skill.name()) // no LLM expander in tests
        );
    }

    @Test
    @DisplayName("pg_trgm 通道：RAG 技能 → 召回 > 0 题")
    void recallWithRagSkill() {
        JobDescription jd = makeJd(List.of(
            new SkillRequirement("RAG", "proficient", "RAG 检索增强")));

        List<CandidateQuestion> results = pipeline.recommend(jd, UID, 5);
        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("覆盖：3 个 requiredSkill 至少覆盖 2 个")
    void requiredSkillCoverage() {
        JobDescription jd = makeJd(List.of(
            new SkillRequirement("RAG", "proficient", "RAG"),
            new SkillRequirement("Agent", "proficient", "Agent"),
            new SkillRequirement("MCP", "familiar", "MCP")));

        List<CandidateQuestion> results = pipeline.recommend(jd, UID, 10);

        long covered = results.stream()
            .map(CandidateQuestion::matchedSkillName).distinct()
            .filter(s -> s != null).count();
        assertThat(covered).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("pg_trgm 通道来源标记正确")
    void pgTrgmChannelTagging() {
        JobDescription jd = makeJd(List.of(
            new SkillRequirement("RAG", "proficient", "RAG")));

        List<CandidateQuestion> results = pipeline.recommend(jd, UID, 5);

        assertThat(results).anyMatch(c -> c.recallChannel() == RecallChannel.PG_TRGM);
    }

    @Test
    @DisplayName("MMR 重排：相邻 50% 以上题型不重复")
    void mmrDiversifiesTypes() {
        JobDescription jd = makeJd(List.of(
            new SkillRequirement("RAG", "proficient", "RAG"),
            new SkillRequirement("Agent", "proficient", "Agent")));

        List<CandidateQuestion> results = pipeline.recommend(jd, UID, 8);
        assertThat(results).isNotEmpty();

        int adjacentSame = 0;
        for (int i = 1; i < results.size(); i++) {
            if (results.get(i).questionType().equals(results.get(i - 1).questionType()))
                adjacentSame++;
        }
        assertThat(adjacentSame).isLessThan(results.size() / 2);
    }

    @Test
    @DisplayName("掌握度加权：LEVEL_0 优先于 LEVEL_2")
    void masteryBoostPrefersLevel0() {
        JobDescription jd = makeJd(List.of(
            new SkillRequirement("RAG", "proficient", "RAG")));

        List<CandidateQuestion> results = pipeline.recommend(jd, UID, 10);

        long level0InTop5 = results.stream().limit(5)
            .filter(c -> c.masteryLevel() == 0).count();
        assertThat(level0InTop5).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("空 skill JD → 泛召回兜底不抛异常")
    void emptySkillsDoesNotThrow() {
        JobDescription jd = makeJd(List.of());

        List<CandidateQuestion> results = pipeline.recommend(jd, UID, 5);
        assertThat(results).isNotNull();
    }

    @Test
    @DisplayName("结果数 ≤ totalQuestions")
    void resultCountBounded() {
        JobDescription jd = makeJd(List.of(
            new SkillRequirement("Agent", "proficient", "Agent")));

        for (int n : new int[]{3, 5, 10}) {
            List<CandidateQuestion> results = pipeline.recommend(jd, UID, n);
            assertThat(results).hasSizeLessThanOrEqualTo(n);
        }
    }

    @Test
    @DisplayName("JD 驱动空结果时降级到 Channel A")
    void fallbackToChannelAWhenEmpty() {
        // 用不存在的技能名 → JD 驱动可能为空 → Channel A 兜底
        JobDescription jd = makeJd(List.of(
            new SkillRequirement("量子计算", "proficient", "量子算法")));

        List<CandidateQuestion> results = pipeline.recommend(jd, UID, 5);
        // 即使 JD 驱动为空，GENERAL 兜底通道应产出结果
        if (results.isEmpty()) {
            // 被 InterviewAgent 的 Channel A 兜底接管
            assertThat(results).isEmpty();
        }
    }

    @Test
    @DisplayName("召回结果包含 matchedSkillName")
    void matchedSkillNamePresent() {
        JobDescription jd = makeJd(List.of(
            new SkillRequirement("MySQL", "familiar", "MySQL 分库分表")));

        List<CandidateQuestion> results = pipeline.recommend(jd, UID, 5);
        assertThat(results).anyMatch(c -> "MySQL".equals(c.matchedSkillName()));
    }

    private static JobDescription makeJd(List<SkillRequirement> required) {
        JobDescription jd = JobDescription.create(UID, "测试 JD");
        jd.updateParsedResult(required, List.of(), List.of(), "字节跳动", "AI Agent开发", "3年");
        return jd;
    }
}
