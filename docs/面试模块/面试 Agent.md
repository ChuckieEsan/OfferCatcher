# Interview Agent 设计文档

> 文档版本：v2.1
> 更新时间：2026-05-02
> 核心变更 v2.0：从掌握度驱动出题改为 JD 驱动出题，新增面试流程结构化，实现记忆隔离
> 核心变更 v2.1：吸收 interview-guide 的 Skill→Category→Reference 体系、JD 匹配 Reference、简历感知出题、历史去重、多层降级、PDF 解析等设计

---

## 目录

1. [概述](#一概述)
2. [核心设计原则](#二核心设计原则)
3. [记忆隔离：面试模式 vs 对话模式](#三记忆隔离面试模式-vs-对话模式)
4. [JD 解析与技能提取](#四jd-解析与技能提取)
5. [JD 驱动的题目匹配](#五jd-驱动的题目匹配)
6. [面试流程结构化](#六面试流程结构化)
7. [掌握度模型与评分闭环](#七掌握度模型与评分闭环)
8. [追问与交互机制](#八追问与交互机制)
9. [面试风格适配](#九面试风格适配)
10. [实现优先级](#十实现优先级)

---

## 一、概述

Interview Agent 是 AI 模拟面试官，核心设计目标是实现**JD 驱动的结构化面试引擎**——从岗位描述出发，精准出题、合理排序、客观评分，形成"面试即评估，复盘即学习"的闭环。

与旧版（v1.x）的核心差异：

| | v1.x（掌握度驱动） | v2.0（JD 驱动） |
|---|---|---|
| **出题依据** | 用户 mastery_level（60:30:10 加权） | JD 提取的 required_skills |
| **题目顺序** | shuffle 随机打乱 | 面试阶段机（Opening → Technical → Behavioral → Closing） |
| **Agent 记忆** | 可能注入用户偏好/行为 | 面试模式完全隔离，不注入用户记忆 |
| **JD 利用** | 无 | LLM 解析 JD → 结构化 skills → 驱动匹配 |
| **评分闭环** | 更新 mastery_level | 保留，面试后更新 mastery_level 用于学习推荐 |

---

## 二、核心设计原则

1. **面试官不应该"认识"候选人**：面试 Agent 不注入用户记忆（preferences、behaviors、历史对话摘要），只基于 JD 和当前回答做判断
2. **JD 比题库标签更精准**：一个 JD 中写"熟悉 RocketMQ 事务消息、有过 ShardingSphere 分库分表经验"，比题库的 `position="后端开发"` 标签精准一个数量级
3. **面试有自然节奏**：开场热身 → 技术深挖 → 行为考察 → 收尾，题目顺序应符合真实面试的认知流
4. **评分客观独立**：基于标准答案 + 评分维度，不与用户历史表现对比
5. **面试后反哺学习**：面试结束后，评分结果更新 mastery_level，用于学习模式的个性化推荐

---

## 三、记忆隔离：面试模式 vs 对话模式

### 3.1 问题

当前 ChatAgent 创建时会注入 MEMORY.md（用户偏好、行为模式、历史会话摘要）。如果面试也走同一套流程，Agent 会"知道"用户弱于 RAG、擅长 Agent 架构，从而在出题和评分时产生无意识偏差。

### 3.2 解决方案

**同一个 Agent 创建流程，根据场景切换注入内容**：

```
对话模式（ChatAgent）：
  PreCall Hook → 注入 MEMORY.md + Short-term Memory（查看 docs/记忆模块/AgentScope记忆设计.md）
  用途：学习辅导、面经讨论、知识点答疑

面试模式（InterviewAgent）：
  PreCall Hook → 不注入用户记忆
  注入：JD 解析结果 + 面试阶段上下文 + 已答题目的评分历史
  用途：模拟面试
```

### 3.3 面试模式注入内容

```java
// 面试场景：不注入用户记忆
String interviewContext = buildInterviewContext(jdId, sessionId);
// 包含：
// 1. JD 解析结果（required_skills、preferred_skills）
// 2. 当前面试阶段描述
// 3. 已答题目的得分（不含 mastery 历史）
// 4. 公司面试风格
// 不包含 MEMORY.md、preferences、behaviors、session_summaries
```

### 3.4 边界划分

| 环节 | 用户记忆参与？ | 原因 |
|------|-------------|------|
| **面试中** | ❌ | 面试应基于 JD 客观评估 |
| **题目选择** | ❌ | 基于 JD skills，不基于用户 mastery_level |
| **评分** | ❌ | 基于标准答案 + 评分维度 |
| **Follow-up 决策** | ❌ | 基于当前得分 + JD 要求深度 |
| **面试后复盘** | ✅ | 评分结果 → 更新 mastery_level → 推荐学习计划 |
| **学习模式** | ✅ | MEMORY.md + Short-term Memory 照常注入 |

---

## 四、JD 解析与技能提取

### 4.1 流程

```
原始 JD 文本（用户粘贴或从招聘链接抓取）
  ↓
JD Parser Agent（一次 LLM 调用，maxIters=0, Structured Output）
  ↓ 提取结构化摘要
{
  "company": "字节跳动",
  "position": "AI Agent 应用开发",
  "required_skills": [
    { "name": "分布式事务", "level": "proficient",
      "evidence": "熟悉 RocketMQ 消息事务" },
    { "name": "MySQL 分库分表", "level": "familiar",
      "evidence": "有过分库分表经验，熟悉 ShardingSphere" },
    { "name": "系统降级设计", "level": "familiar",
      "evidence": "具备系统降级与容错设计能力" },
    { "name": "LangGraph", "level": "proficient",
      "evidence": "熟悉 LangGraph 工作流编排与 checkpointer 机制" }
  ],
  "preferred_skills": [
    { "name": "多模态 RAG", "level": "familiar" },
    { "name": "Agent 评测体系", "level": "familiar" }
  ],
  "soft_skills": ["团队协作", "技术方案推进", "跨团队沟通"],
  "experience_requirement": "3-5年"
}
```

### 4.2 为什么用 Agent 不用正则/规则

- JD 文本格式极度不统一（段落式、列表式、纯关键词混排）
- 需要语义理解：区分"必须"和"加分"，识别隐含要求
- 一次 LLM 调用即可，延迟 ~500ms，创建面试时执行一次，不在热路径上

### 4.3 数据模型

```java
// 新增领域实体
public class JobDescription {
    private Long id;
    private String userId;
    private String company;
    private String position;
    private String rawText;           // 原始 JD 文本
    private List<SkillRequirement> requiredSkills;
    private List<SkillRequirement> preferredSkills;
    private List<String> softSkills;
    private String experienceRequirement;
    private LocalDateTime createdAt;
}

public record SkillRequirement(
    String name,      // 技能名，如 "分布式事务"
    String level,     // proficient / familiar / beginner
    String evidence   // JD 原文证据
) {}
```

---

## 五、JD 驱动的题目匹配

### 5.1 核心改变

**不再用用户 query 来检索题目，用 JD 提取的 skills 来检索**。

```
旧方案（v1.x）：
  embedding("公司：X | 岗位：Y | 面试题") → Qdrant → 按 mastery 加权

新方案（v2.0）：
  JD.requiredSkills[i] → skill_to_query(name, level, evidence) → Qdrant → 去重 → 排序
```

### 5.2 匹配算法

```
1. 对每个 required_skill + preferred_skill：
   skill_to_query(skill) → 构造检索字符串
   例如：
     skill: { name: "分布式事务", level: "proficient", evidence: "熟悉 RocketMQ 消息事务" }
     query: "面试题：分布式事务 | RocketMQ | 事务消息 | 最终一致性"

2. 每个 skill → Qdrant 检索 top 3 候选题目
   共 N 个 skills → 最多 3N 个候选题目

3. 去重（MD5 question_id）

4. 相关性排序（reranker 精排）

5. 覆盖检查：确保每个 required_skill 至少有一题
   ├─ 某 skill 无匹配题目 → 降低阈值重新搜索
   └─ 仍无匹配 → 标记为"缺失覆盖"，可考虑 LLM 生成题目

6. 按面试阶段分组排列（见第六章）
```

### 5.3 覆盖分析

| JD Skill | 要求等级 | 匹配题目 | 覆盖 |
|----------|---------|---------|------|
| 分布式事务 | proficient | "RocketMQ 事务消息的实现原理？" | ✅ |
| MySQL 分库分表 | familiar | "分库分表后跨库查询怎么处理？" | ✅ |
| 系统降级设计 | familiar | "如何设计一个服务降级开关？" | ✅ |
| LangGraph | proficient | "LangGraph 的 checkpointer 机制？" | ✅ |
| 多模态 RAG | familiar | — | ❌ 缺失 |

### 5.4 与旧方案的本质区别

| | v1.x（掌握度驱动） | v2.0（JD 驱动） |
|---|---|---|
| **检索输入** | 公司 + 岗位名称 | JD 中的具体技能要求 |
| **加权依据** | 用户 mastery_level | JD 中 skill 的 level（proficient > familiar） |
| **覆盖目标** | 无 | 确保每个 required_skill 至少一题 |
| **用户依赖** | 依赖用户历史数据 | 完全独立于用户 |

---

## 六、面试流程结构化

### 6.1 问题

旧方案中所有候选题目 shuffle 打乱，导致"自我介绍"可能出现在第 7 题，高难度技术题可能在开场第一题。真实面试有自然的节奏和认知流。

### 6.2 阶段机设计

```
┌─────────────────────────────────────────────────────────┐
│                  Interview Flow                         │
│                                                        │
│  Phase 1: Opening (开场热身, ~10% of questions)          │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 题型: behavioral + simple_knowledge               │  │
│  │ 典型题目:                                         │  │
│  │   1. "请做个简单的自我介绍"                         │  │
│  │   2. "介绍一下你最近做过的项目"                     │  │
│  │   3. "你对这个岗位的理解"                          │  │
│  │ 目的: 让候选人放松，建立基本印象                     │  │
│  └──────────────────────────────────────────────────┘  │
│                        ↓                                │
│  Phase 2: Technical Deep-Dive (技术深挖, ~60%)          │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 题型: knowledge + scenario + algorithm            │  │
│  │ 按 JD skill 分组，组内由浅入深:                     │  │
│  │   skill: 分布式事务                                │  │
│  │     4. "RocketMQ 的事务消息是怎么实现的？" (基础)   │  │
│  │     5. "分布式事务的最终一致性怎么保证？" (深入)    │  │
│  │     6. "设计一个跨银行转账的分布式事务方案" (场景)  │  │
│  │   skill: LangGraph                                │  │
│  │     7. "LangGraph 的 StateGraph 怎么设计？"        │  │
│  │     8. "checkpointer 的持久化策略？"               │  │
│  │ 目的: 验证 JD 核心技能的真实掌握深度                │  │
│  └──────────────────────────────────────────────────┘  │
│                        ↓                                │
│  Phase 3: Behavioral & Scenario (行为考察, ~20%)        │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 题型: behavioral + scenario                       │  │
│  │ 按 JD soft_skills 匹配:                            │  │
│  │   9.  "讲一个你推动过的技术方案" (方案推进)          │  │
│  │   10. "技术分歧怎么处理？" (团队协作)               │  │
│  │   11. "QPS 翻 10 倍怎么排查？" (应变能力)           │  │
│  │ 目的: 软技能 + 实际问题解决能力                     │  │
│  └──────────────────────────────────────────────────┘  │
│                        ↓                                │
│  Phase 4: Closing (收尾, ~10%)                          │
│  ┌──────────────────────────────────────────────────┐  │
│  │ 题型: behavioral                                  │  │
│  │ 典型题目:                                         │  │
│  │   12. "你有什么想问我的吗？"                        │  │
│  │   13. "对团队或工作方式有什么期望？"               │  │
│  │ 目的: 双向选择，自然收尾                           │  │
│  └──────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 6.3 题目排序规则

| 规则 | 说明 |
|------|------|
| **Phase 固定顺序** | Opening → Technical → Behavioral → Closing，不可跳阶段 |
| **Technical 内按 skill 分组** | 同一 skill 的题目连续出，不跳跃 |
| **组内由浅入深** | 概念理解 → 原理深挖 → 场景应用，difficulty 递增 |
| **Behavioral 按 JD soft_skills 匹配** | 从 JD 提取的软技能要求筛选行为题 |
| **动态调整** | 某 skill 得分持续低 → 增加该 skill 的 follow-up；得分高 → 缩短该 skill 的题目数 |

### 6.4 固定开场题

Phase 1 (Opening) 的前三道题不依赖 JD，是固定题库：

```java
static final List<String> OPENING_QUESTIONS = List.of(
    "请做个简单的自我介绍",
    "能介绍一下你最近做过的一个项目吗？",
    "你对这个岗位的理解是什么？"
);
```

这些题目不参与 shuffle，始终排在面试最前面。

### 6.5 题目数量分配

```java
int openingCount = Math.max(2, totalQuestions * 10 / 100);       // ≥2 题
int behavioralCount = Math.max(2, totalQuestions * 20 / 100);    // ≥2 题
int closingCount = Math.max(1, totalQuestions * 10 / 100);       // ≥1 题
int technicalCount = totalQuestions - openingCount - behavioralCount - closingCount;
```

---

## 七、掌握度模型与评分闭环

### 7.1 掌握度三级模型（保留）

```python
class MasteryLevel(int, Enum):
    LEVEL_0 = 0  # 未掌握/未复习
    LEVEL_1 = 1  # 比较熟悉
    LEVEL_2 = 2  # 已掌握
```

**v2.0 变化**：掌握度不再用于驱动出题，仅用于面试后追踪学习进度和推荐学习计划。

### 7.2 评分状态机（保留）

```
LEVEL_0 → LEVEL_2: score >= 85 (跨级升级)
LEVEL_0 → LEVEL_1: score >= 60
LEVEL_1 → LEVEL_2: score >= 85
LEVEL_2 保持不变
score < 60 保持当前等级
```

### 7.3 评分流程（保留）

```
process_answer(answer)
  ↓
ScorerAgent.score(questionId, answer)
  ├── 获取题目标准答案
  ├── LLM 评分（四维度：完整性30% + 准确性30% + 逻辑20% + 深度广度20%）
  ├── 解析 ScoreResult
  ├── 计算新 mastery_level
  └── 更新 Question 的 mastery_level（面试后用于学习推荐）
  ↓
得分 >= 70 → 进入下一题
得分 < 70 → 追问（检查追问上限）
```

### 7.4 面试后复盘

面试结束后，根据评分结果生成学习推荐：

- **所有 score >= 80 的 skill** → 标记为"已掌握"
- **所有 score < 60 的 skill** → 标记为"薄弱项"，推荐学习资源
- **JD 要求 proficient 但 score < 70** → 高优先级提升项

这部分的 mastery_level 更新和推荐逻辑**与面试中 Agent 的行为完全隔离**——面试 Agent 不知道用户的 mastery 历史。

---

## 八、追问与交互机制（保留）

### 8.1 追问次数限制

配置项 `interview_max_follow_ups`，默认值 3。

```
回答评分 < 70 时：
  ├─ follow_up_count < 3: 继续追问
  │   ├── 生成追问/提示
  │   └── 返回 follow_up 类型消息
  └─ follow_up_count >= 3: 强制进入下一题
      ├── 生成总结性诊断反馈
      └── 标记题目状态为 SCORED
```

### 8.2 总结性反馈

| 最终评分 | 反馈策略 |
|---------|---------|
| < 40 | 建议系统学习相关知识点，从基础开始 |
| 40-59 | 建议加强核心原理和实际应用场景学习 |
| 60-69 | 建议巩固细节，确保清晰完整表达 |

---

## 九、面试风格适配（保留）

```python
COMPANY_STYLES = {
    "字节跳动": "务实、注重细节和深度",
    "阿里巴巴": "注重价值观匹配和系统性思维",
    "腾讯": "温和但有深度，注重实际应用",
    "百度": "注重技术细节和底层原理",
    "美团": "务实、注重业务理解",
    "京东": "注重工程实践和系统稳定性",
    "快手": "注重实时系统和高并发经验",
    "拼多多": "注重业务增长和数据驱动",
    "小红书": "注重用户体验和内容理解",
}
```

公司风格注入 Interview Agent 的 system prompt，影响追问风格和交互语气。

---

## 十、实现优先级

### Phase 1：记忆隔离（P0，~1天）

1. InterviewAgentService 创建 Agent 时跳过 MEMORY.md 和 Short-term Memory 注入
2. 实现 `buildInterviewContext(jdId, sessionId)` → 注入 JD + 阶段 + 评分历史
3. 验证：面试 Agent 的 system prompt 中不包含用户偏好/行为信息

### Phase 2：JD 解析（P0，~2天）

1. 新增 `JobDescription` 领域实体 + JPA 持久化
2. 实现 `JDParserService`（一次 LLM 调用 + Structured Output）
3. 实现 JD 创建 API（用户粘贴 JD 文本 → 解析 → 存储）
4. 测试：多种 JD 格式的解析准确率

### Phase 3：JD 驱动题目匹配（P1，~2天）

1. 实现 `skill_to_query()` → 构造检索字符串
2. 重写 `preloadQuestions()`：JD skills → Qdrant 检索 → 去重 → 覆盖检查
3. 移除 mastery_level 在出题中的权重逻辑
4. 测试：覆盖分析，确保 required_skills 全覆盖

### Phase 4：面试流程结构化（P1，~2天）

1. 实现 `InterviewFlowPhaser`：阶段机 + 题目排序规则
2. 实现固定开场题库
3. 实现题目数量按阶段分配
4. 测试：题目顺序符合面试认知流

### Phase 5：简历解析与简历感知出题（P2，~2天）

1. 引入 Apache Tika 依赖（PDF/DOCX/TXT 文档解析）
2. 实现 `ResumeParseService`（PDF 解析 + 文本清洗）
3. 实现简历分析 API（LLM 提取项目经历、技术栈）
4. 实现简历感知并行出题：60% 简历题 + 40% 方向题

---

## 十一、参考项目 absorb：interview-guide 可吸收设计

> 参考项目：[Snailclimb/interview-guide](https://github.com/Snailclimb/interview-guide)
> Spring Boot 4.0 + Java 21 + Spring AI + PostgreSQL + pgvector

### 11.1 Skill → Category → Reference 三级体系

interview-guide 将面试方向建模为三层结构，比我们的 company+position 标签精细得多：

```
Skill (java-backend)
  ├── SKILL.md           ← 面试官 persona（怎么问、问多深、什么风格）
  ├── skill.meta.yml     ← 分类定义（考哪些领域、优先级、参考资料映射）
  │   categories:
  │     - key: JAVA,   label: Java,    priority: CORE,   ref: java.md
  │     - key: MYSQL,  label: MySQL,   priority: CORE,   ref: mysql.md
  │     - key: REDIS,  label: Redis,   priority: CORE,   ref: redis.md
  │     - key: SPRING, label: Spring,  priority: NORMAL, ref: spring.md
  │     - key: PROJECT,label: 项目经历, priority: ALWAYS_ONE
  └── references/        ← 参考知识库（出题的"标准答案素材"）
        ├── java.md, mysql.md, redis.md, spring.md ...
```

**对我们启发**：Category 作为 JD 和题库之间的中间层。JD 要求 Redis → 匹配到 Category:REDIS → 从题库检索 Redis 相关题目。这样题目覆盖是确定的——JD 要求考什么就一定会出什么，不会因向量相似度不够而漏掉。

### 11.2 Category 优先级驱动的题目分配

```java
// calculateAllocation — 按优先级分配题目数量
enum Priority { CORE, NORMAL, ALWAYS_ONE }

// CORE: 确保至少 1 题，通常 2+ 题
// NORMAL: 题目充裕时覆盖
// ALWAYS_ONE: 固定 1 题（如项目经历，不随总题数变化）
```

**对我们启发**：JD skills 中 `level=proficient` → CORE 优先级，`level=familiar` → NORMAL。替换当前 mastery_level 的 60:30:10 加权，题目分配依据从"用户掌握得怎么样"变成"JD 要求得多重要"。

### 11.3 JD 解析匹配本地 Reference

他们的 JD 解析不是自由提取关键词，而是**匹配到已有 Reference 文件**：

```
JD 文本
  ↓ LLM 解析（Prompt 中注入所有 Reference 文件清单）
{
  categories: [
    { key: "DISTRIBUTED_TRANSACTION", ref: "distributed.md", shared: true },
    { key: "MYSQL_SHARDING",          ref: "mysql.md",       shared: true },
    { key: "LANGGRAPH",               ref: null }  ← 本地无匹配
  ]
}
```

匹配到的 → 注入 reference 内容给出题 Prompt（保证出题质量）。未匹配的 → 仅靠 JD 原文出题（可接受）。完全没有 reference 覆盖 → 考虑 LLM 生成新题补充题库。

**对我们启发**：JD skill → 匹配 Reference → 从 Qdrant 题库检索该 Reference 对应的题目。未匹配的 skill 标记为"缺失覆盖"，后续补充题目或 LLM 生成。

### 11.4 简历感知的并行出题

```java
boolean hasResume = resumeText != null && !resumeText.isBlank();
if (!hasResume) {
    return generateDirectionOnly(...);  // 100% 方向题
}
// 有简历：60% 简历专项题 + 40% 技能方向题，CompletableFuture 并行
int resumeCount = Math.max(1, Math.round(questionCount * 0.6));
int directionCount = questionCount - resumeCount;
CompletableFuture.supplyAsync(generateResumeQuestions, executor);   // 简历题
CompletableFuture.supplyAsync(generateDirectionOnly, executor);     // 方向题
```

简历题 Prompt 聚焦候选人项目经历（"你在简历中提到的 XX 项目..."），方向题 Prompt 聚焦技能考察（"请解释 Redis 的..."）。两者并行生成，一方失败时自动降级到另一方。

**对我们启发**：如果用户上传了简历，面试题目的 60% 应从简历项目经验出发，40% 从 JD 技能要求出发。这比全部用 JD 方向题更真实——真实面试中面试官一定会根据简历提问。

### 11.5 历史去重

```java
// buildHistoricalSection — 注入"已考过的知识点"列表
已考过的知识点（避免重复出题）：
- JAVA: 线程池原理, JVM 内存模型, synchronized 底层实现
- MYSQL: 索引优化, 分库分表方案
```

每次出题时注入已考过的话题 summary，LLM 主动避开。不仅在单次面试内去重，跨面试也可去重——用户上周面过"RAG 检索精度"，这周同一岗位不再出同样题目。

**对我们启发**：面试题目选择时，从 Qdrant 检索结果中过滤掉与已考 topic 向量距离过近的题目（或由 LLM 判断是否重复）。

### 11.6 多层降级链

```
LLM 正常响应 → 使用 LLM 生成的题目
  ↓ 失败
简历题失败 → 降级为全方向题
  ↓ 失败
方向题失败 → 降级为硬编码默认题库（GENERIC_FALLBACK_QUESTIONS）
  ↓ 失败
默认题库也空 → 基于 categories 构造最低保证题
```

**对我们启发**：JD 驱动匹配 → Qdrant 题库检索 → LLM 生成新题 → 通用默认题。每一层都有兜底。

### 11.7 Prompt 安全边界

```java
// 用户提供的简历、JD 用 XML 标签包裹，防 prompt injection
promptSanitizer.wrapWithDelimiters("resume", sanitizedResumeText);
promptSanitizer.wrapWithDelimiters("jd", sanitizedJdText);
// 系统指令和数据之间加边界标记
PromptSecurityConstants.DATA_BOUNDARY_INSTRUCTION
```

**对我们启发**：JD 解析和出题时会注入用户提供的 JD 文本、简历文本，需要用分隔符包裹防止 prompt 注入。

### 11.8 Redis 会话管理

```java
// 面试会话完全 Redis 优先，数据库异步同步
sessionCache.saveSession(sessionId, resumeText, resumeId, questions, 0, CREATED);
persistenceService.saveSession(...);  // 异步，失败不影响
// 缓存 miss → 从数据库恢复 → 回写 Redis
restoreSessionFromDatabase(sessionId);
```

**对我们启发**：面试是实时交互（提交答案 → 评分 → 下一题），Redis 保证低延迟。Postgres 做最终持久化。

### 11.9 简历 PDF 解析

使用 Apache Tika 作为统一文档解析引擎：

```java
// DocumentParseService — 核心解析逻辑
AutoDetectParser parser = new AutoDetectParser();
BodyContentHandler handler = new BodyContentHandler(5 * 1024 * 1024); // 5MB 上限
Metadata metadata = new Metadata();
ParseContext context = new ParseContext();
context.set(Parser.class, parser);
context.set(EmbeddedDocumentExtractor.class, new NoOpEmbeddedDocumentExtractor()); // 禁用嵌入资源

// PDF 专用配置
PDFParserConfig pdfConfig = new PDFParserConfig();
pdfConfig.setExtractInlineImages(false);   // 关闭图片提取
pdfConfig.setSortByPosition(true);         // 按坐标排序文本

parser.parse(inputStream, handler, metadata, context);
return textCleaningService.cleanText(handler.toString());
```

依赖：`org.apache.tika:tika-core` + `org.apache.tika:tika-parsers`

支持的格式：PDF、DOCX、DOC、TXT、MD、HTML 等。

**对我们启发**：OfferCatcher 需要用户能上传简历 PDF，解析为文本后由 LLM 提取项目经历和技术栈，用于简历感知出题。

### 11.10 吸收优先级总结

| 吸收点 | 优先级 | 复杂度 | 说明 |
|--------|--------|--------|------|
| Category 优先级分配 | P0 | 低 | 修改 preloadQuestions 的加权逻辑 |
| JD 匹配 Reference | P0 | 中 | 新增 Reference 映射层 |
| 历史去重 | P0 | 低 | 出题时注入已考 topic |
| 多层降级链 | P1 | 中 | 题库检索 → LLM 生成 → 默认题 |
| 简历感知出题 | P1 | 中 | 60% 简历题 + 40% 方向题 |
| Prompt 安全边界 | P1 | 低 | XML 包裹 JD/简历文本 |
| Redis 会话管理 | P1 | 中 | 简化——当前已有 InterviewSession JPA |
| 简历 PDF 解析 | P2 | 中 | 引入 Apache Tika |
| Skill→Category→Reference 体系 | P2 | 高 | 新建 Skill 领域模型，从面试方向映射到题库分类 |
