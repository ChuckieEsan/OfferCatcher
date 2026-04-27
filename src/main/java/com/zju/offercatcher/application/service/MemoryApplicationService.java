package com.zju.offercatcher.application.service;

import com.zju.offercatcher.domain.memory.aggregates.Memory;
import com.zju.offercatcher.domain.memory.entities.MemoryReference;
import com.zju.offercatcher.domain.memory.entities.SessionSummary;
import com.zju.offercatcher.domain.memory.repositories.MemoryRepository;
import com.zju.offercatcher.domain.memory.repositories.SessionSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * 记忆应用服务
 *
 * 编排用户记忆的获取、更新等用例。
 * 对应 Python: app/application/services/memory_service.py
 */
@Service
public class MemoryApplicationService {

    private static final Logger log = LoggerFactory.getLogger(MemoryApplicationService.class);

    private final MemoryRepository memoryRepository;
    private final SessionSummaryRepository sessionSummaryRepository;

    public MemoryApplicationService(MemoryRepository memoryRepository,
                                     SessionSummaryRepository sessionSummaryRepository) {
        this.memoryRepository = memoryRepository;
        this.sessionSummaryRepository = sessionSummaryRepository;
    }

    public Memory getMemory(String userId) {
        return memoryRepository.findByUserId(userId)
            .orElseGet(() -> initializeMemory(userId));
    }

    public String getMemoryContent(String userId) {
        return getMemory(userId).getContent();
    }

    public String getPreferences(String userId) {
        return getMemory(userId).getReference("preferences")
            .map(MemoryReference::getContent).orElse("");
    }

    public String getBehaviors(String userId) {
        return getMemory(userId).getReference("behaviors")
            .map(MemoryReference::getContent).orElse("");
    }

    @Transactional
    public void updatePreferences(String userId, String content) {
        Memory memory = getMemory(userId);
        memory.addReference(MemoryReference.create("preferences", content));
        memoryRepository.save(memory);
        log.info("preferences.md updated for user {}", userId);
        syncMemoryIndex(userId);
    }

    @Transactional
    public void updateBehaviors(String userId, String content) {
        Memory memory = getMemory(userId);
        memory.addReference(MemoryReference.create("behaviors", content));
        memoryRepository.save(memory);
        log.info("behaviors.md updated for user {}", userId);
        syncMemoryIndex(userId);
    }

    @Transactional
    public void syncMemoryIndex(String userId) {
        Memory memory = getMemory(userId);
        String preferences = memory.getReference("preferences")
            .map(MemoryReference::getContent).orElse("");
        String behaviors = memory.getReference("behaviors")
            .map(MemoryReference::getContent).orElse("");

        List<SessionSummary> recentSessions = sessionSummaryRepository
            .findTopKByImportance(userId, 5);

        String content = buildMemoryContent(userId, preferences, behaviors, recentSessions);
        memory.updateContent(content);
        memoryRepository.save(memory);
        log.info("MEMORY.md index synced for user {}", userId);
    }

    // ==================== Private Helpers ====================

    private Memory initializeMemory(String userId) {
        String content = buildMemoryContent(userId, null, null, List.of());
        Memory memory = Memory.create(userId, content);
        memoryRepository.save(memory);
        log.info("Memory initialized for user {}", userId);
        return memory;
    }

    private String buildMemoryContent(String userId, String preferences, String behaviors,
                                       List<SessionSummary> recentSessions) {
        String prefsSummary = extractPrefsSummary(preferences);
        String behaviorsSummary = extractBehaviorsSummary(behaviors);
        String sessionsSummary = buildSessionsSummary(recentSessions);

        return """
            ---
            name: user-memory-%s
            description: 用户特定的偏好和行为规则。始终加载此文档。
            ---

            # 用户记忆

            ## 偏好概要
            %s

            ## 行为模式概要
            %s

            ## 会话历史概要
            %s

            ## 可用 References
            | Reference | 描述 |
            |-----------|------|
            | `preferences` | 完整的用户偏好设置 |
            | `behaviors` | 观察到的行为模式详情 |

            ## 使用指南
            1. 本文档始终加载，提供概要信息
            2. 概要不够详细时，调用 load_memory_reference 加载详情
            3. 需要语义检索历史时，调用 search_session_history 搜索
            """.formatted(userId, prefsSummary, behaviorsSummary, sessionsSummary);
    }

    private static String extractPrefsSummary(String preferences) {
        if (preferences == null || preferences.isBlank()) {
            return "- 语言：中文\n- 解释深度：适中\n- 代码示例：根据问题需要";
        }
        return preferences.lines().limit(5).reduce((a, b) -> a + "\n" + b).orElse("- 语言：中文");
    }

    private static String extractBehaviorsSummary(String behaviors) {
        if (behaviors == null || behaviors.isBlank()) {
            return "（暂无观察到的行为模式）";
        }
        return behaviors.lines().limit(4).reduce((a, b) -> a + "\n" + b)
            .orElse("（暂无观察到的行为模式）");
    }

    private static String buildSessionsSummary(List<SessionSummary> sessions) {
        if (sessions.isEmpty()) {
            return "（暂无历史会话）";
        }
        StringBuilder sb = new StringBuilder("最近 ").append(sessions.size()).append(" 次会话：\n");
        for (SessionSummary s : sessions) {
            String summary = s.getSummary();
            String shortSummary = summary.length() > 50 ? summary.substring(0, 50) : summary;
            sb.append("- ").append(shortSummary).append("\n");
        }
        return sb.toString();
    }
}
