package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.application.agent.dto.ResumeAnalysisOutput;
import com.zju.offercatcher.domain.interview.aggregates.InterviewSession;
import com.zju.offercatcher.domain.shared.enums.DifficultyLevel;
import com.zju.offercatcher.infrastructure.file.ResumeParseService;
import io.github.cdimascio.dotenv.Dotenv;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@ActiveProfiles("test")
@Tag("api")
@DisplayName("简历分析 Agent 集成测试")
class ResumeAnalysisAgentTest {

    @Autowired
    private ResumeParseService parseService;
    @Autowired
    private ResumeAnalysisAgent analysisAgent;
    @Autowired
    private InterviewAgent interviewAgent;

    private static final Path PDF_PATH = Path.of("data/test_data/示例简历.pdf");

    private static boolean hasApiKey() {
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        return dotenv.get("DASHSCOPE_API_KEY") != null
                || dotenv.get("OPENAI_API_KEY") != null
                || dotenv.get("DEEPSEEK_API_KEY") != null;
    }

    @Test
    @DisplayName("PDF 解析 → 提取文本不为空")
    void parsePdfToText() throws IOException {
        assumeTrue(hasApiKey());
        byte[] bytes = Files.readAllBytes(PDF_PATH);
        MockMultipartFile file = new MockMultipartFile("file", "示例简历.pdf",
                "application/pdf", bytes);

        String text = parseService.parse(file);

        assertThat(text).isNotBlank();
        assertThat(text.length()).isGreaterThan(100);
    }

    @Test
    @DisplayName("LLM 分析简历 → 提取技术栈和项目经历")
    void analyzeResume() throws IOException {
        assumeTrue(hasApiKey());
        byte[] bytes = Files.readAllBytes(PDF_PATH);
        MockMultipartFile file = new MockMultipartFile("file", "示例简历.pdf",
                "application/pdf", bytes);

        String text = parseService.parse(file);
        ResumeAnalysisOutput output = analysisAgent.analyze(text);

        assertThat(output.techStack()).isNotEmpty();
        assertThat(output.projects()).isNotEmpty();
    }

    @Test
    @DisplayName("简历上下文注入面试 → system prompt 包含简历内容")
    void resumeContextInInterview() throws IOException {
        assumeTrue(hasApiKey());
        byte[] bytes = Files.readAllBytes(PDF_PATH);
        MockMultipartFile file = new MockMultipartFile("file", "示例简历.pdf",
                "application/pdf", bytes);

        String text = parseService.parse(file);
        ResumeAnalysisOutput output = analysisAgent.analyze(text);
        String resumeContext = output.toInterviewContext();

        InterviewSession session = interviewAgent.createSession(
                "test-resume-user", "字节跳动", "AI Agent开发",
                DifficultyLevel.MEDIUM, 5, null, resumeContext);

        assertThat(session).isNotNull();
        assertThat(session.getResumeContext()).isEqualTo(resumeContext);
    }
}
