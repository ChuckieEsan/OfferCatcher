# OfferCatcher AgentScope 记忆系统设计

> 文档版本：v1.1
> 更新时间：2026-04-29
> 核心变更 v1.1：Short-term Memory 从 RAG 管道改为独立检索 Agent（MemoryRetrievalAgent）
> 基于 AgentScope Java 1.0.11 的三层记忆架构

---

## 目录

1. [设计理念](#一设计理念)
2. [AgentScope 记忆能力分析](#二agentscope-记忆能力分析)
3. [三层记忆架构](#三三层记忆架构)
4. [Working Memory](#四working-memory)
5. [Short-term Memory](#五short-term-memory)
6. [Long-term Memory](#六long-term-memory)
7. [记忆生命周期](#七记忆生命周期)
8. [Hook 驱动机制](#八hook-驱动机制)
9. [Tool 设计](#九tool-设计)
10. [存储设计](#十存储设计)
11. [并发控制](#十一并发控制)
12. [现有代码改造清单](#十二现有代码改造清单)
13. [实现优先级](#十三实现优先级)

---

## 一、设计理念

### 1.1 核心原则

> 记忆 = 渐进式披露的用户知识库，通过 AgentScope 原生机制实现三层记忆流转。
> 核心设计目的：**把重操作从同步路径上移走**——越靠近 Working Memory 越要求零延迟，越靠近 Long-term Memory 越允许以延迟换质量。

三层记忆的设计目的：

| 层级 | 时间尺度 | 解决的问题 | 延迟策略 |
|------|---------|-----------|---------|
| **Working Memory** | 单次 ReAct Loop（秒~分钟） | Token 窗口管理，长对话不崩 | 内存操作，零延迟 |
| **Short-term Memory** | 单次 Conversation（分钟~小时） | **异步预热替代同步检索，消除交互时延抖动** | 缓存读取 <1ms；miss 则同步降级 <10ms |
| **Long-term Memory** | 跨会话（天~永久） | 跨会话积累用户知识，渐进式注入 | 异步提取，允许秒级延迟 |

三层记忆对应三个时间尺度和三种存储机制：

| 层级 | 时间尺度 | 存储机制 | AgentScope 对应 |
|------|---------|---------|----------------|
| **Working Memory** | 单次 ReAct Loop（秒~分钟） | AgentScope `Memory` 接口 | `AutoContextMemory` |
| **Short-term Memory** | 单次 Conversation（分钟~小时） | Redis 缓存（Agent 检索结果） | 自定义上下文注入 |
| **Long-term Memory** | 跨会话（天~永久） | Postgres + Qdrant + SkillBox | `LongTermMemory` + `SkillBox` |

**核心思路：异步预热 + 同步降级**

```
请求到达时（同步路径，追求零延迟）：
  ├── MEMORY.md → 已在 system prompt 中，零开销
  ├── Short-term cache → Redis 读取，~1ms
  └── AutoContextMemory → 内存中已有的压缩历史，零开销

请求完成后（异步路径，允许延迟换质量）：
  ├── MemoryRetrievalAgent 启动（独立 Agent，自主检索 + 精炼）
  │     → 分析用户意图
  │     → search_session_history（多角度检索）
  │     → load_memory_reference（加载偏好/行为规则）
  │     → 整合精炼，输出上下文
  │     → 写入 Redis 缓存
  └── MemoryAgent 分析对话 → 更新 preferences/behaviors/summaries
```

**Short-term Memory 不是 RAG 管道，而是一个独立的检索 Agent**：
- 不做 embedding → Qdrant → 拼格式的机械流程
- 而是让 Agent 自主分析用户意图、决定搜什么、搜几次、如何整合
- 输出的是精炼上下文而非原始摘要堆砌

### 1.2 渐进式披露（继承 Python 方案）

```
MEMORY.md (~400 tokens)          ← 始终注入 System Prompt
    ↓ 按需加载
references/preferences.md        ← load_memory_reference tool
references/behaviors.md
references/skills/{name}/SKILL.md ← load_skill tool
    ↓ 按需检索
session_summaries (向量检索)      ← search_session_history tool
```

### 1.3 与 Python 方案的关系

Python 方案的 MEMORY.md + references + session_summaries 结构完全保留。本次设计将其映射到 AgentScope Java 的原生机制上，并新增 Working Memory 层（AgentScope `AutoContextMemory`）。

---

## 二、AgentScope 记忆能力分析

### 2.1 短期记忆：Memory 接口

```java
public interface Memory extends StateModule {
    void addMessage(Msg message);
    List<Msg> getMessages();
    void deleteMessage(int index);
    void clear();
}
```

| 实现 | 特点 | 适用场景 |
|------|------|---------|
| `InMemoryMemory` | 简单内存存储，无上下文管理 | 短对话、测试 |
| **`AutoContextMemory`** | 6级渐进压缩、智能摘要、大内容卸载 | **长对话、生产环境** |

**`AutoContextMemory` 核心能力**：
- 自动压缩：消息数/token 数超过阈值时触发
- 智能摘要：用 LLM 对历史对话进行摘要
- 内容卸载：大内容卸载到外部存储，按需重载（UUID）
- 双存储机制：压缩后的工作存储 + 完整的原始存储

### 2.2 长期记忆：LongTermMemory 接口

```java
public interface LongTermMemory {
    Mono<Void> record(List<Msg> msgs);   // 记录消息（Agent回复后自动调用）
    Mono<String> retrieve(Msg msg);       // 检索记忆（推理前自动调用）
}
```

AgentScope 提供三种实现：`Mem0LongTermMemory`、`ReMeLongTermMemory`、`BailianLongTermMemory`。

**我们选择自定义实现** `OfferCatcherLongTermMemory`，对接自己的 Postgres + Qdrant + MEMORY.md 体系，原因：
- Mem0/ReMe 是外部服务，引入额外运维成本
- 我们已有完善的 Postgres + Qdrant 存储层
- MEMORY.md 的渐进式披露机制比 Mem0 的黑盒记忆更适合面试辅导场景

### 2.3 Agent Skills：SkillBox

```java
SkillBox skillBox = new SkillBox(toolkit);
skillBox.registerSkill(skill);
ReActAgent agent = ReActAgent.builder()
    .skillBox(skillBox)  // 自动注册 skill 工具和 hook
    .build();
```

SkillBox 实现了渐进式披露：初始只注入 skill 元数据（~100 tokens），LLM 按需调用 `load_skill_through_path` 加载完整内容。

### 2.4 Hook 系统

AgentScope 提供完整的事件钩子，可用于记忆注入和提取：

| 事件 | 时机 | 记忆用途 |
|------|------|---------|
| `PreCallEvent` | Agent 调用前 | 注入 Long-term Memory 检索结果 |
| `PostCallEvent` | Agent 调用后 | 触发 MemoryAgent 异步提取 |
| `PreReasoningEvent` | LLM 推理前 | 注入 Short-term Memory 上下文 |

### 2.5 关键约束

- **Agent 是有状态的，不可并发调用** — 每个请求独立创建 Agent 实例
- **Memory 通过 Session 持久化** — `saveTo(session, key)` / `loadFrom(session, key)`
- **Toolkit 在 Agent 内共享** — 所有 Tool 通过 `ToolExecutionContext` 获取用户上下文

---

## 三、三层记忆架构

### 3.1 整体架构图

```
┌──────────────────────────────────────────────────────────────────┐
│                        用户请求 (Msg)                              │
└──────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│  PreCallEvent Hook（同步，~1ms）                                   │
│  ├── 注入 MEMORY.md（始终加载的 Long-term Memory 概要）            │
│  ├── 注入 Short-term Memory（Redis 读取上一轮 Agent 检索结果）     │
│  │     └── miss → 同步降级：SELECT * ORDER BY importance_score    │
│  │         LIMIT 5（纯 SQL，<10ms）                                │
│  └── 注入 Skill 元数据（用户自定义 Skill 列表）                    │
└──────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│                    Working Memory                                 │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │              AutoContextMemory                             │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────────────────┐     │  │
│  │  │ 用户消息  │  │ Tool调用  │  │ LLM 推理 → 响应      │     │  │
│  │  └──────────┘  └──────────┘  └──────────────────────┘     │  │
│  │                                                           │  │
│  │  自动压缩：消息数 > 30 或 token > 阈值 → LLM 摘要          │  │
│  │  内容卸载：大段 Tool 结果卸载到外部存储，按需 UUID 重载     │  │
│  └────────────────────────────────────────────────────────────┘  │
│                         │                                         │
│              ReAct Loop (reasoning ⇄ acting)                      │
└──────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌──────────────────────────────────────────────────────────────────┐
│  PostCallEvent Hook / after reply（异步，fire-and-forget）          │
│  ├── 保存消息到 messages 表                                       │
│  ├── Async: 启动 MemoryRetrievalAgent（独立 Agent，自主检索+精炼） │
│  │     ├── 分析用户意图，制定检索策略                               │
│  │     ├── search_session_history（多角度向量检索）                │
│  │     ├── load_memory_reference（加载偏好/行为规则）              │
│  │     ├── 整合精炼输出（≤500 tokens）                            │
│  │     └── 写入 Redis 缓存                                       │
│  └── Async: 启动 MemoryAgentService.extractMemories()             │
│       ├── 分析对话 → 提取偏好/行为/摘要                            │
│       ├── 调用 write_session_summary → Postgres + Qdrant          │
│       ├── 调用 update_preferences → MEMORY.md                     │
│       └── 调用 update_behaviors → MEMORY.md                       │
└──────────────────────────────────────────────────────────────────┘
```

### 3.2 三层数据流

```
                         ┌──────────────────────────────────┐
                         │        Long-term Memory           │
                         │                                  │
          ┌──────────────┤  MEMORY.md（始终注入）             │
          │              │  preferences.md（按需加载）        │
          │              │  behaviors.md（按需加载）          │
          │              │  skills/{name}/（SkillBox 管理）  │
          │              │  session_summaries（向量检索）     │
          ▼              └──────────────────────────────────┘
┌──────────────────┐                    ▲
│ Short-term Memory│                    │
│                  │                    │
│ Redis 缓存       │◄──── async ────────┤
│ - Agent 精炼的   │    MemoryRetrievalAgent
│   检索上下文     │    （独立 Agent，自主检索 + 精炼）
│ - miss → 降级    │
│   importance     │                    ▲
│   top-K（<10ms）  │                    │
└──────┬───────────┘     async extract (MemoryAgent)
       │
       │ inject context（缓存命中 ~1ms，降级 <10ms）
       ▼
┌──────────────────┐
│ Working Memory   │
│                  │
│ AutoContextMemory│──► LLM Reasoning
│ (当前消息队列)    │
└──────────────────┘
```

---

## 四、Working Memory

### 4.1 职责

- 存储当前 ReAct Loop 中的消息（用户输入、Tool 调用/结果、Agent 响应）
- 自动管理上下文窗口（压缩、摘要、卸载），避免 token 超限
- 生命周期：单次 Agent 调用（`agent.call()` / `agent.stream()`）

### 4.2 实现：AutoContextMemory

**当前状态**：使用 `InMemoryMemory`，长对话会撑爆上下文。

**目标**：替换为 `AutoContextMemory`，利用其 6 级压缩策略。

```java
import io.agentscope.core.memory.autocontext.AutoContextConfig;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.memory.autocontext.ContextOffloadTool;

// 配置
AutoContextConfig config = AutoContextConfig.builder()
    .msgThreshold(30)        // 消息数超过 30 触发压缩
    .lastKeep(10)            // 压缩后保留最近 10 条消息
    .tokenRatio(0.3)         // token 超过窗口 30% 触发压缩
    .build();

// 创建 Memory
AutoContextMemory memory = new AutoContextMemory(config, model);

// 注册内容重载 Tool（Agent 可按需重载被卸载的大内容）
Toolkit toolkit = new Toolkit();
toolkit.registerTool(new ContextOffloadTool(memory));

// 创建 Agent
ReActAgent agent = ReActAgent.builder()
    .name("offer-catcher-chat")
    .model(cachedModel)
    .memory(memory)          // 替换 InMemoryMemory
    .toolkit(toolkit)
    .build();
```

### 4.3 为什么不用 InMemoryMemory

| 场景 | InMemoryMemory | AutoContextMemory |
|------|---------------|-------------------|
| 10轮短对话 | OK | OK |
| 50轮长对话（含大量 Tool 结果） | ❌ token 爆表 | ✅ 自动压缩 |
| WebSearch 返回长文本 | ❌ 上下文污染 | ✅ 内容卸载 + 按需重载 |
| 连续多轮 tool 调用 | ❌ 消息堆积 | ✅ 智能摘要 |

面试辅导场景中，单次对话可能包含多轮搜索题目、知识图谱查询、Web 搜索，`AutoContextMemory` 的压缩能力是必需的。

### 4.4 何时不替换

当前 ChatAgentService 每次请求创建新的 `ReActAgent`，Memory 不跨请求复用（会话历史通过 `buildHistoryMessages()` 手动注入）。因此 `AutoContextMemory` 的持久化能力（Session）暂不需要，本次只利用其**上下文窗口管理**能力。

---

## 五、Short-term Memory

### 5.1 设计理念

**核心思路：用一个独立的检索 Agent 替代 RAG 管道。**

传统 RAG 检索流程是机械的：embedding → 向量搜索 → 拼格式。问题在于：
- 检索质量完全依赖向量相似度，无法理解"用户真正想问什么"
- 用户问"RAG 的阈值一般设多少"，只会搜到字面相关的摘要，不会搜"召回策略"、"相似度调优"等语义相关但向量距离不近的内容
- 结果是原始摘要堆砌，缺乏精炼

Agent 检索的优势：
- **意图翻译**：Agent 分析用户意图，构造多个不同角度的搜索查询
- **多跳检索**：先搜到相关摘要 → 发现涉及某个话题 → 再搜相关 → 整合输出
- **结果精炼**：Agent 自己判断哪些有用、哪些是噪音，输出整合后的上下文
- **工具组合**：同时使用 `search_session_history`（向量检索）+ `load_memory_reference`（偏好/行为规则），按场景组合

### 5.2 核心矛盾：异步 vs 同步

MemoryRetrievalAgent 存在一个根本性的时序矛盾：

| 方案 | 检索依据 | 延迟 | 记忆相关性 | 问题 |
|------|---------|------|-----------|------|
| **异步** | 上一轮 query | <1ms（预热命中） | 话题不变时 ✓，话题切换时 ✗ | 用旧信息猜新问题 |
| **同步** | 当前 query | ~200-400ms | 始终精准 | 增加同步路径延迟 |

**异步方案的信息论缺陷**：

```
消息 N 聊的是 RAG → 异步检索到 RAG 相关记忆 → 缓存到 Redis
消息 N+1 问的是 Java 多线程 → 注入的却是 RAG 记忆 → 完全跑偏
```

在用户发出消息 N+1 之前，没有任何信息能告诉系统"用户接下来要问什么"。

**同步方案的正确性保证**：

检索必须基于当前 query 执行，否则注入不相关的记忆比不注入更糟（污染上下文、误导 Agent）。这是正确性问题而非延迟优化问题。

### 5.3 分阶段策略

**第一阶段（当前实现）：异步预热**。MemoryRetrievalAgent 在 PostCall 异步执行，基于上一轮消息检索，结果缓存 Redis 供下一轮注入。在话题连续的场景中效果好（面试辅导场景天然话题集中），话题切换时降级兜底。延迟为零。

**第二阶段（目标架构）：同步检索**。MemoryRetrievalAgent 在 PreCall 同步执行，基于当前 query 检索。通过小模型 + maxIters=2 将延迟控制在 200-400ms，以换取精准的记忆上下文。

两个阶段使用完全相同的 MemoryRetrievalAgent 代码，差异仅在于**调用时机**和**传入的 query**。

---

### 5.4 第一阶段：异步预热（当前实现）

#### 5.4.1 时序

```
第 N 轮用户消息（query_N）
    ↓
主 Agent 响应 → 返回给用户
    ↓ （用户阅读回复的"免费时间"）
PostCall Hook → fire-and-forget：
    MemoryRetrievalAgent.retrieveAsync(userId, convId, query_N, recentMessages)
      ├── 分析 query_N，决定检索策略
      ├── search_session_history（基于 query_N 的多角度检索）
      ├── load_memory_reference（可选）
      ├── 整合结果，输出精炼上下文（≤500 tokens）
      └── 写入 Redis 缓存
    ↓
第 N+1 轮用户消息（query_{N+1}）
    ↓
PreCall Hook → Redis 读取（~1ms）
    ├── 命中 → 注入（内容基于 query_N，话题连续时精准匹配）
    └── miss → 同步降级：SELECT * ORDER BY importance_score LIMIT 5（<10ms）
```

**适用场景判断**：

| 场景 | 异步有效性 | 原因 |
|------|-----------|------|
| 面试辅导中的连续追问 | ✅ 高 | 话题高度集中，query_N 的检索结果大概率覆盖 query_{N+1} |
| 话题切换 | ❌ 低 | 旧话题摘要与新话题无关，但降级方案兜底 |
| 首条消息 | — | 无缓存，降级到 importance top-K |

#### 5.4.2 降级策略

**结论：不等待，降级。**

```
用户第 N 轮 → 触发异步 Agent
用户第 N+1 轮（<1 秒） → Agent 还在跑，Redis 未命中
```

如果设计等待 → 用户感知 500ms-1s 额外延迟，违背"异步预热"的初衷。

**降级方案**：缓存 miss → 同步查询 `SELECT * FROM session_summaries WHERE user_id = ? ORDER BY importance_score DESC LIMIT 5`，纯 SQL，<10ms。

| 场景 | 降级效果 |
|------|---------|
| **首条消息** | 按重要性取高价值摘要——"上次聊的是 RAG 召回阈值"作为开场上下文合理 |
| **连续追问** | 高重要性摘要集中在用户反复讨论的 topic 上，大概率命中当前话题 |
| **极速追问** | 最多丢一轮上下文，不丢两轮（第 1 条的 Agent 完成后，第 3 条大概率命中） |

#### 5.4.3 异步缓存策略

| 参数 | 值 | 说明 |
|------|---|------|
| 缓存 Key | `oc:memory:context:{userId}:{conversationId}` | 按会话隔离 |
| TTL | 3600 秒（1小时） | 超过后自动过期 |
| 容量上限 | 20KB | 超过时裁剪最早注入的记忆 |

---

### 5.5 第二阶段：同步检索（目标架构）

#### 5.5.1 时序

```
用户消息（当前 query）
    ↓
PreCall Hook（同步）：
  1. MEMORY.md → 零延迟，已在 prompt 模板中
  2. MemoryRetrievalAgent.retrieveSync(currentQuery)
     ├── 小模型，maxIters=2
     ├── 分析 currentQuery 意图，构造搜索词
     ├── search_session_history（基于当前 query）
     ├── load_memory_reference（可选，仅在需要时）
     └── 输出精炼上下文
  3. 注入上下文 → 主 Agent 推理
    ↓
主 Agent 响应 → 返回用户
    ↓
PostCall Hook（异步）：
  MemoryAgent.extractMemories() → 更新 preferences/behaviors/summaries
  （不再需要检索缓存，检索已在同步路径上完成）
```

#### 5.5.2 延迟优化

全功能 Agent（maxIters=5，主力模型）跑完需要 500ms-1s。但检索 Agent 有天然优势可以压缩延迟：

| 优化点 | 异步阶段 | 同步阶段 | 延迟收益 |
|--------|---------|---------|---------|
| **模型** | 小模型 | 更小更快的模型 | 每 token 延迟降低 50-70% |
| **maxIters** | 5 | 2 | 减少 LLM 往返 |
| **检索策略** | 多跳探索 | 分析意图 + 一次精准 search | 减少 tool 往返 |
| **流式截断** | 不适用 | Agent 输出上下文后立即终止 | 减少尾部等待 |

目标：同步检索 Agent 延迟控制在 **200-400ms**。

#### 5.5.3 延迟可接受性分析

| 方案 | 同步延迟 | 记忆相关性 | 判断 |
|------|---------|-----------|------|
| 异步预热 | <1ms | 话题切换时跑偏 | ❌ 正确性问题 |
| 同步 RAG（embedding+Qdrant） | ~50ms | 依赖向量相似度 | 可用但不是 Agent 范式 |
| **同步 Agent 检索** | **~200-400ms** | **精准匹配当前 query** | ✅ 正确性 + Agent 范式 |

如果主 Agent 推理通常需要 2-3 秒，额外 300ms 检索只增加约 10-15% 的端到端延迟，换取精准的记忆上下文——这是值得的。

#### 5.5.4 同步方法签名

```java
/**
 * 同步检索记忆上下文。
 *
 * 基于当前 query 检索，确保记忆与用户问题精准相关。
 * 使用小模型 + maxIters=2，目标延迟 200-400ms。
 *
 * @param userId          用户 ID
 * @param currentQuery    用户当前消息
 * @param recentMessages  最近几轮对话
 * @return 精炼的记忆上下文（≤500 tokens），失败时返回空字符串
 */
public String retrieveSync(String userId, String currentQuery,
                            List<Message> recentMessages) {
    try {
        ReActAgent agent = createRetrievalAgentSync(userId);  // maxIters=2, 更小的模型
        String taskPrompt = buildTaskPrompt(currentQuery, recentMessages);
        Msg result = agent.call(List.of(
            Msg.builder().role(MsgRole.USER).textContent(taskPrompt).build()
        )).block();
        return result.getTextContent();
    } catch (Exception e) {
        log.error("Sync retrieval failed: {}", e.getMessage(), e);
        return "";  // 检索失败不阻塞主流程
    }
}

private ReActAgent createRetrievalAgentSync(String userId) {
    // ... 与异步版本相同，但 maxIters=2，使用更小更快的模型
    return ReActAgent.builder()
        .name("memory-retrieval-sync")
        .model(smallFastModel)    // 更小更快的模型
        .maxIters(2)              // 限制迭代
        .toolkit(cachedToolkit)
        .toolExecutionContext(toolContext)
        .build();
}
```

#### 5.5.5 同步方案下的组件变更

| 取消 | 原因 |
|------|------|
| Redis 检索缓存（`oc:memory:context:*`） | 检索基于当前 query，无需跨轮缓存 |
| 异步 `retrieveAsync()` 调用 | 检索已在同步路径上完成 |
| 降级 `importance top-K` 查询 | 不再有缓存 miss |

| 保留 | 说明 |
|------|------|
| `search_session_history` tool | 底层向量检索能力，Agent 的工具 |
| `load_memory_reference` tool | 按需加载偏好/行为规则 |
| Redis 检索锁 | 防止同一会话并发检索 |
| Qdrant `session_summaries` 集合 | 向量检索的存储层 |
| Postgres `session_summaries` 表 | 元数据存储 |

---

### 5.6 两阶段共用：MemoryRetrievalAgent 核心实现

两个阶段使用相同的 Agent 创建逻辑，差异仅在于调用时机和参数（maxIters 和模型）。

```java
package com.zju.offercatcher.application.agent;

import com.zju.offercatcher.domain.chat.entities.Message;
import com.zju.offercatcher.infrastructure.common.CacheKeys;
import com.zju.offercatcher.infrastructure.tools.MemoryTools;
import com.zju.offercatcher.infrastructure.tools.UserToolContext;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 记忆检索 Agent（Short-term Memory 的核心）
 *
 * 不是 RAG 管道，而是一个独立的 ReActAgent。
 * 拥有 search_session_history 和 load_memory_reference 工具，
 * 自主分析用户意图、多角度检索、精炼输出。
 *
 * 支持两种模式：
 * - 异步模式（第一阶段）：PostCall 触发，基于上一轮 query 检索，结果缓存 Redis
 * - 同步模式（第二阶段）：PreCall 触发，基于当前 query 检索，直接返回上下文
 */
@Service
public class MemoryRetrievalAgent {

    private static final Logger log = LoggerFactory.getLogger(MemoryRetrievalAgent.class);
    private static final int CONTEXT_TTL_SECONDS = 3600;
    private static final int MAX_OUTPUT_TOKENS = 500;

    private final MemoryTools memoryTools;
    private final RedisTemplate<String, String> redisTemplate;
    private final OpenAIChatModel asyncModel;       // 异步：小模型
    private final OpenAIChatModel syncModel;        // 同步：更小更快的模型

    private final Toolkit cachedToolkit;

    public MemoryRetrievalAgent(MemoryTools memoryTools,
                                RedisTemplate<String, String> redisTemplate,
                                LLMProperties llmProperties) {
        this.memoryTools = memoryTools;
        this.redisTemplate = redisTemplate;

        LLMProperties.DeepSeek cfg = llmProperties.getDeepseek();
        this.asyncModel = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey()).modelName(cfg.getModel())
            .baseUrl(cfg.getBaseUrl()).stream(false).build();
        // 同步用更快的小模型
        this.syncModel = OpenAIChatModel.builder()
            .apiKey(cfg.getApiKey()).modelName(cfg.getSmallModel())
            .baseUrl(cfg.getBaseUrl()).stream(false).build();

        this.cachedToolkit = new Toolkit(ToolkitConfig.defaultConfig());
        this.cachedToolkit.registerTool(memoryTools);
    }

    // ==================== 异步模式（第一阶段） ====================

    /**
     * 异步检索并缓存。
     * 基于传入的 query（上一轮消息）检索，结果写入 Redis 供下一轮注入。
     */
    public void retrieveAsync(String userId, Long conversationId,
                               String query, List<Message> recentMessages) {
        CompletableFuture.runAsync(() -> {
            try {
                ReActAgent agent = createRetrievalAgent(userId, asyncModel, 5);
                String taskPrompt = buildTaskPrompt(query, recentMessages);
                Msg result = agent.call(List.of(
                    Msg.builder().role(MsgRole.USER).textContent(taskPrompt).build()
                )).block();

                String context = result.getTextContent();
                if (context != null && !context.isBlank()) {
                    String cacheKey = CacheKeys.memoryContext(userId, conversationId);
                    redisTemplate.opsForValue().set(
                        cacheKey, context, Duration.ofSeconds(CONTEXT_TTL_SECONDS));
                    log.info("Async retrieval cached: {} chars for conv {}", context.length(), conversationId);
                }
            } catch (Exception e) {
                log.error("Async retrieval failed for conv {}: {}", conversationId, e.getMessage(), e);
            }
        });
    }

    /**
     * 读取异步检索的缓存结果。
     */
    public String getCachedContext(String userId, Long conversationId) {
        return redisTemplate.opsForValue()
            .get(CacheKeys.memoryContext(userId, conversationId));
    }

    // ==================== 同步模式（第二阶段，目标架构） ====================

    /**
     * 同步检索记忆上下文。
     * 基于当前 query 检索，确保记忆与用户问题精准相关。
     * 小模型 + maxIters=2，目标延迟 200-400ms。
     * 检索失败返回空字符串，不阻塞主流程。
     */
    public String retrieveSync(String userId, String currentQuery,
                                List<Message> recentMessages) {
        try {
            ReActAgent agent = createRetrievalAgent(userId, syncModel, 2);
            String taskPrompt = buildTaskPrompt(currentQuery, recentMessages);
            Msg result = agent.call(List.of(
                Msg.builder().role(MsgRole.USER).textContent(taskPrompt).build()
            )).block();
            String context = result.getTextContent();
            return context != null ? context : "";
        } catch (Exception e) {
            log.error("Sync retrieval failed: {}", e.getMessage(), e);
            return "";
        }
    }

    // ==================== Agent 创建（两阶段共用） ====================

    private ReActAgent createRetrievalAgent(String userId, OpenAIChatModel model, int maxIters) {
        ToolExecutionContext toolContext = ToolExecutionContext.builder()
            .register("userContext", new UserToolContext(userId))
            .build();

        return ReActAgent.builder()
            .name("memory-retrieval")
            .sysPrompt("""
                你是记忆检索专家。你的任务：
                1. 分析用户问题，理解其核心意图
                2. 使用 search_session_history 从多个角度检索相关历史记忆
                3. 如果用户问题涉及偏好或行为规则，使用 load_memory_reference
                4. 整合搜索结果，输出一段精炼的上下文，供主 Agent 使用

                原则：
                - 从 2-3 个不同角度构造搜索查询，覆盖不同表述方式
                - 只提取与当前问题真正相关的内容，无关信息不要包含
                - 如果有用户偏好或行为模式与问题相关，务必引用
                - 输出控制在 500 tokens 以内
                - 如果搜索结果相关性低，尝试换个角度重新搜索
                """)
            .model(model)
            .toolkit(cachedToolkit)
            .toolExecutionContext(toolContext)
            .maxIters(maxIters)
            .generateOptions(GenerateOptions.builder()
                .temperature(0.1)
                .maxTokens(MAX_OUTPUT_TOKENS)
                .build())
            .build();
    }

    private String buildTaskPrompt(String query, List<Message> recentMessages) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 任务\n");
        sb.append("检索与用户问题相关的历史记忆，"
            + "整合为一段精炼的上下文供主 Agent 使用。\n\n");
        sb.append("## 用户问题\n");
        sb.append(query).append("\n\n");

        if (recentMessages != null && !recentMessages.isEmpty()) {
            sb.append("## 最近对话\n");
            for (int i = Math.max(0, recentMessages.size() - 6);
                 i < recentMessages.size(); i++) {
                Message m = recentMessages.get(i);
                String role = m.isUserMessage() ? "用户" : "AI";
                sb.append(role).append(": ")
                    .append(truncate(m.getContent(), 200)).append("\n\n");
            }
        }

        sb.append("## 检索策略\n");
        sb.append("1. 分析核心意图，构造多个不同角度的搜索查询\n");
        sb.append("2. 涉及\"怎么设置\"、\"推荐\"等，同时检查 preferences\n");
        sb.append("3. 结果不足时换个角度重新搜索\n");
        sb.append("4. 只提取真正相关的内容，输出 ≤ 500 tokens\n");
        return sb.toString();
    }

    private String truncate(String s, int maxLen) {
        return s != null && s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
```

### 5.7 与 RAG 检索对比

| | RAG 管道方案 | Agent 检索方案 |
|---|---|---|
| **检索方式** | embedding → 向量搜索 | Agent 分析意图 → 多角度多轮检索 |
| **结果处理** | 原始摘要拼接 | Agent 精炼整合 |
| **意图理解** | 依赖向量相似度（字面匹配） | Agent 语义理解 + 意图翻译 |
| **工具组合** | 单一向量检索 | search_session_history + load_memory_reference |
| **多跳检索** | 不支持 | Agent 自主决定多跳 |

### 5.8 两阶段迁移路径

```
Phase 2（当前）                         Phase 4（目标）
异步预热 + 降级                         同步 Agent 检索
┌──────────────────┐                  ┌──────────────────┐
│ retrieveAsync()  │ ──代码复用──→     │ retrieveSync()   │
│ maxIters=5       │                  │ maxIters=2       │
│ 异步模型          │                  │ 更小的同步模型     │
│ PostCall 触发     │                  │ PreCall 触发     │
│ Redis 缓存 + 降级  │                  │ 直接返回          │
└──────────────────┘                  └──────────────────┘

迁移条件：
1. 小模型延迟验证通过（单次 search 场景 <200ms）
2. AgentScope 小模型集成稳定
3. 异步方案在话题连续场景下效果得到验证
```

### 5.9 Short-term → Long-term 升级机制

当 SessionSummary 满足以下条件时，从 STM 升级为 LTM：
- `importanceScore >= 0.7`
- `accessCount >= 3`（被多次检索命中）
- 用户主动标记为重要

```java
// SessionSummary 领域方法
public void upgradeToLtm() {
    this.memoryLayer = MemoryLayer.LTM;
    this.importanceScore = Math.max(this.importanceScore, 0.7);
    this.decayFactor = 1.0;  // LTM 不衰减
}

---

## 六、Long-term Memory

### 6.1 职责

- 跨会话持久化的用户知识
- MEMORY.md（概要）+ references（详情）的渐进式披露
- 用户自定义 Skill 管理
- 会话摘要的向量检索

### 6.2 自定义 LongTermMemory 实现

实现 AgentScope 的 `LongTermMemory` 接口，对接自己的存储体系：

```java
package com.zju.offercatcher.infrastructure.memory;

import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.application.service.MemoryRetrievalService;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.message.Msg;
import reactor.core.publisher.Mono;

/**
 * OfferCatcher 自定义长期记忆实现
 *
 * 对接自己的 Postgres + Qdrant + MEMORY.md 体系。
 * 不依赖 Mem0/ReMe 等外部服务。
 */
public class OfferCatcherLongTermMemory implements LongTermMemory {

    private final MemoryApplicationService memoryService;
    private final MemoryRetrievalService retrievalService;
    private final String userId;

    @Override
    public Mono<Void> record(List<Msg> msgs) {
        // AgentScope 在 Agent reply 后自动调用。
        // 我们使用 MemoryAgentService.extractMemories() 异步处理，
        // 而不是在 record() 中同步阻塞。
        // 这里只标记需要提取，实际提取由 PostCallEvent hook 触发。
        return Mono.empty();
    }

    @Override
    public Mono<String> retrieve(Msg msg) {
        // AgentScope 在推理前自动调用。
        // 返回 MEMORY.md 概要 + 最近会话摘要。
        return Mono.fromCallable(() -> {
            String query = msg.getTextContent();
            String memoryContent = memoryService.getMemoryContent(userId);
            // Short-term context 由 MemoryRetrievalService 异步管理，
            // 这里只返回静态的 Long-term Memory 概要
            return memoryContent != null ? memoryContent : "";
        });
    }
}
```

### 6.3 LongTermMemoryMode 选择

使用 `LongTermMemoryMode.STATIC_CONTROL`：框架自动在推理前 recall、回复后 record。

```java
ReActAgent agent = ReActAgent.builder()
    .name("offer-catcher-chat")
    .model(model)
    .longTermMemory(new OfferCatcherLongTermMemory(memoryService, retrievalService, userId))
    .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
    .build();
```

**为什么不用 AGENT_CONTROL**：面试辅导场景中，记忆检索应该是确定性行为而非 Agent 自主决策。Agent 不需要判断"要不要查记忆"——应该总是注入。

### 6.4 SkillBox 集成：MEMORY.md 作为始终激活的 Skill

将 MEMORY.md 和用户自定义 Skill 统一通过 SkillBox 管理：

```java
// 1. 从 Postgres 加载 MEMORY.md
String memoryContent = memoryService.getMemoryContent(userId);

// 2. 构建为 AgentSkill
AgentSkill memorySkill = AgentSkill.builder()
    .name("user-memory")
    .description("用户特定的偏好和行为规则。始终加载此文档。"
        + "当概要信息不足以回答问题时，使用 load_memory_reference Tool"
        + "或 search_session_history Tool 查询详情。")
    .skillContent(memoryContent)
    .addResource("references/preferences.md",
        memoryService.getPreferences(userId))
    .addResource("references/behaviors.md",
        memoryService.getBehaviors(userId))
    .build();

// 3. 加载用户自定义 Skill
List<AgentSkill> userSkills = loadUserSkills(userId);

// 4. 注册到 SkillBox
SkillBox skillBox = new SkillBox(toolkit);
skillBox.registerSkill(memorySkill);
for (AgentSkill skill : userSkills) {
    skillBox.registerSkill(skill);
}

// 5. 创建 Agent
ReActAgent agent = ReActAgent.builder()
    .name("offer-catcher-chat")
    .skillBox(skillBox)
    .toolkit(toolkit)
    .build();
```

**渐进式披露效果**：
- System Prompt 中只注入 skill 元数据（name + description，~100 tokens/skill）
- MEMORY.md 概要始终在上下文中（~400 tokens）
- references 详情按需通过 `load_memory_reference` 加载
- 用户自定义 Skill 通过 `load_skill_through_path` 按需加载

### 6.5 用户自定义 Skill 架构

```
memory/{user_id}/
├── MEMORY.md                          # 始终加载的概要
└── references/
    ├── preferences.md                 # 用户偏好详情
    ├── behaviors.md                   # 行为模式详情
    └── skills/                        # 用户自定义 Skill
        ├── interview_tips/
        │   ├── SKILL.md               # name + description + 指令
        │   └── references/            # 可选详情
        │       └── common_questions.md
        └── rag_deep/
            ├── SKILL.md
            └── references/
                ├── architecture.md
                └── implementation.md
```

**SkillBox 管理方式**：

```java
// SkillBox 内置了 Skill 加载和渐进式披露机制
// 注册用户自定义 Skill 后，Agent 会在 system prompt 中看到 skill 描述
// 当任务匹配时，Agent 调用 load_skill_through_path(skillId, resourcePath) 加载详情

// 将 MEMORY.md 设为第一个 skill（始终描述）
skillBox.registration()
    .skill(memorySkill)           // MEMORY.md 内容
    .apply();

// 注册用户自定义 Skill
for (AgentSkill userSkill : userSkills) {
    skillBox.registration()
        .skill(userSkill)
        .apply();
}
```

### 6.6 session_summaries 表设计（保持不变）

当前设计已经完善，无需修改：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 雪花 ID |
| conversation_id | BIGINT | FK → conversations |
| user_id | VARCHAR | 用户隔离 |
| summary | TEXT | 3句话摘要 |
| embedding | float[] (1024维) | Qdrant 向量检索 |
| importance_score | DOUBLE | 0.0-1.0 |
| memory_layer | STM / LTM | 短期/长期 |
| access_count | INT | 检索命中次数 |
| decay_factor | DOUBLE | STM 衰减因子 |

**STM → LTM 升级**：`importanceScore >= 0.7` 且 `accessCount >= 3` 时升级。

**衰减**：STM 每条摘要 `decayFactor *= (1 - decayRate)`，低于 0.1 时标记删除。

---

## 七、记忆生命周期

### 7.1 对话开始（PreCall Hook）

```
1. 读取 MEMORY.md → 注入 system prompt
2. 读取 Redis 缓存的 Short-term Memory → 注入 system prompt
3. 加载用户自定义 Skill 元数据 → SkillBox 注入
4. 加载最近 N 条历史消息 → buildHistoryMessages()
5. 创建 ReActAgent → agent.call() / agent.stream()
```

### 7.2 对话中（ReAct Loop）

```
Reasoning:
  1. AgentScope 从 Memory 读取消息历史
  2. LongTermMemory.retrieve() 返回 MEMORY.md 概要
  3. Formatter 组装 → LLM API 调用
  4. LLM 返回 reasoning/thinking 或 tool_call

Acting:
  1. 如有 tool_call → 执行 Tool（search_questions, search_web, memory tools 等）
  2. Tool 结果写入 Memory
  3. 回到 Reasoning

AutoContextMemory 自动管理：
  - 消息数 > msgThreshold → 触发压缩
  - 大内容 → 卸载到外部存储
  - 历史消息 → LLM 摘要
```

### 7.3 对话结束（PostCall Hook）

```
1. 保存消息到 messages 表
2. 保存对话状态到 Session（可选，用于跨重启恢复）
3. Async (workerExecutor):
   a. MemoryRetrievalAgent.retrieveAsync()
      → 启动独立检索 Agent，分析用户意图
      → Agent 自主调用 search_session_history（多角度向量检索）
      → Agent 自主调用 load_memory_reference（偏好/行为规则）
      → Agent 整合精炼输出（≤500 tokens）
      → 写入 Redis 缓存
   b. MemoryAgentService.extractMemories()
      → 分析新消息 → 调用 MemoryTools → 更新 MEMORY.md + session_summaries
```

### 7.4 记忆提取流程（MemoryAgent）

```
输入：游标之后的新消息 + 现有 preferences + 现有 behaviors

MemoryAgent (ReActAgent, maxIters=8) 自主决策：

  有偏好反馈？
    → update_preferences(content)
  
  有行为模式？
    → update_behaviors(content)
  
  有值得记录的讨论？
    → write_session_summary(summary, conversation_id)
  
  有更新？
    → update_memory_index(user_id)  // 刷新 MEMORY.md 概要
  
  最后：
    → update_cursor(conversation_id, cursor_uuid)
```

### 7.5 游标互斥机制（保持不变）

与 Python 方案相同的游标机制：

```
┌─────────────────────────────────────────────┐
│  已处理区域（游标之前）  │  待处理（游标之后）│
│  last_memory_message_uuid │  新消息          │
└─────────────────────────────────────────────┘
```

- 主 Agent 直接写入记忆时，响应中添加 `<memory_write>` 标记
- 后台 MemoryAgent 检查游标之后是否有主 Agent 写入 → 有则跳过

---

## 八、Hook 驱动机制

### 8.1 自定义 MemoryHook

```java
package com.zju.offercatcher.infrastructure.memory;

import com.zju.offercatcher.application.service.MemoryApplicationService;
import com.zju.offercatcher.application.service.MemoryRetrievalService;
import io.agentscope.core.hook.*;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

/**
 * 记忆注入 Hook
 *
 * 在 Agent 调用前注入 Long-term + Short-term Memory 上下文。
 * 优先级设为 50（早于默认 100），确保记忆在其他 Hook 之前注入。
 */
public class MemoryInjectionHook implements Hook {

    private final MemoryApplicationService memoryService;
    private final MemoryRetrievalAgent retrievalAgent;
    private final SessionSummaryRepository sessionSummaryRepository;
    private final String userId;
    private final Long conversationId;

    @Override
    public int priority() {
        return 50; // 高优先级，先于 ObservabilityHook (200)
    }

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PreCallEvent e) {
            String memoryContext = buildFullMemoryContext();
            if (memoryContext != null && !memoryContext.isBlank()) {
                List<Msg> messages = new ArrayList<>(e.getInputMessages());
                messages.add(0, Msg.builder()
                    .role(MsgRole.SYSTEM)
                    .content(List.of(TextBlock.builder().text(memoryContext).build()))
                    .build());
                e.setInputMessages(messages);
            }
            return Mono.just(event);
        }
        return Mono.just(event);
    }

    private String buildFullMemoryContext() {
        StringBuilder sb = new StringBuilder();

        // 1. MEMORY.md 概要（始终注入，~400 tokens）
        String memoryMd = memoryService.getMemoryContent(userId);
        if (memoryMd != null && !memoryMd.isBlank()) {
            sb.append(memoryMd);
        }

        // 2. Short-term Memory（上一轮 MemoryRetrievalAgent 精炼的上下文）
        //    命中（~1ms）：agent 精炼的上下文
        //    miss → 降级（<10ms）：SELECT * ORDER BY importance_score LIMIT 5
        String shortTerm = retrievalAgent.getCachedContext(userId, conversationId);
        if (shortTerm == null || shortTerm.isBlank()) {
            // 降级：纯 SQL，无 embedding 计算
            shortTerm = buildDegradedContext(userId);
        }
        if (shortTerm != null && !shortTerm.isBlank()) {
            if (!sb.isEmpty()) sb.append("\n\n---\n\n## 相关历史会话\n");
            sb.append(shortTerm);
        }

        return sb.toString();
    }

    private String buildDegradedContext(String userId) {
        List<SessionSummary> topK = sessionSummaryRepository.findTopKByImportance(userId, 5);
        if (topK.isEmpty()) return null;
        StringBuilder sb = new StringBuilder();
        for (SessionSummary s : topK) {
            sb.append("- ").append(s.getSummary()).append("\n");
        }
        return sb.toString();
    }
}
```

### 8.2 自定义 PostProcessingHook

```java
/**
 * 对话后处理 Hook
 *
 * 在 Agent 完成响应后触发两项异步任务：
 * 1. MemoryRetrievalAgent：独立 Agent 检索历史 + 精炼输出 → Redis 缓存
 * 2. MemoryAgentService：分析对话内容 → 提取偏好/行为/摘要
 */
public class MemoryPostProcessingHook implements Hook {

    private final MemoryAgentService memoryAgent;
    private final MemoryRetrievalAgent retrievalAgent;
    private final ChatApplicationService chatService;
    private final Executor workerExecutor;
    private final String userId;
    private final Long conversationId;

    @Override
    public <T extends HookEvent> Mono<T> onEvent(T event) {
        if (event instanceof PostCallEvent e) {
            CompletableFuture.runAsync(() -> {
                // 1. 启动检索 Agent 预热 Short-term Memory
                String lastMsg = e.getFinalMessage().getTextContent();
                Conversation conv = chatService.getConversation(userId, conversationId)
                    .orElse(null);
                List<Message> recentMessages = conv != null ? conv.getMessages() : List.of();
                retrievalAgent.retrieveAsync(userId, conversationId, lastMsg, recentMessages);

                // 2. 启动记忆提取 Agent 更新 Long-term Memory
                if (conv != null) {
                    memoryAgent.extractMemories(userId, conversationId, conv.getMessages());
                }
            }, workerExecutor);

            return Mono.just(event);
        }
        return Mono.just(event);
    }
}
```

### 8.3 Hook 注册

```java
ReActAgent agent = ReActAgent.builder()
    .name("offer-catcher-chat")
    .model(cachedModel)
    .memory(autoContextMemory)
    .longTermMemory(offerCatcherLongTermMemory)
    .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
    .skillBox(skillBox)
    .toolkit(toolkit)
    .hooks(List.of(
        new MemoryInjectionHook(memoryService, userId, conversationId),
        new MemoryPostProcessingHook(memoryAgent, retrievalAgent, chatService,
            workerExecutor, userId, conversationId),
        agentObservabilityHook  // 优先级 200，最后执行
    ))
    .build();
```

---

## 九、Tool 设计

### 9.1 Agent 全景

系统中存在三类与记忆相关的 Agent，各司其职：

| Agent | 角色 | 执行时机 | 模型 | 工具 |
|-------|------|---------|------|------|
| **ChatAgent** | 主对话 Agent | 同步（用户请求时） | 主力模型 | search_questions, search_web, knowledge_graph, memory tools |
| **MemoryRetrievalAgent** | 短期记忆检索 | 异步（PostCall Hook） | 小模型 | search_session_history, load_memory_reference |
| **MemoryAgent** | 长期记忆提取 | 异步（PostCall Hook） | 小模型 | update_preferences, update_behaviors, write_session_summary, update_memory_index |

### 9.2 Tool 全景

| Tool | 所属 Agent | 层级 | 说明 |
|------|-----------|------|------|
| `search_session_history` | ChatAgent, RetrievalAgent, MemoryAgent | Short/Long-term | 向量检索历史会话 |
| `load_memory_reference` | ChatAgent, RetrievalAgent, MemoryAgent | Long-term | 加载 preferences/behaviors 详情 |
| `update_preferences` | MemoryAgent | Long-term | 更新偏好（带 `<memory_write>` 标记） |
| `update_behaviors` | MemoryAgent | Long-term | 更新行为模式 |
| `write_session_summary` | MemoryAgent | Long-term | 写入会话摘要到 Postgres + Qdrant |
| `update_memory_index` | MemoryAgent | Long-term | 刷新 MEMORY.md 概要 |
| `ContextOffloadTool` | ChatAgent | Working | AutoContextMemory 内容重载 |
| `search_questions` | ChatAgent | — | 面试题检索（非记忆） |
| `search_web` | ChatAgent | — | 网络搜索（非记忆） |
| `get_knowledge_relations` | ChatAgent | — | 知识图谱（非记忆） |

### 9.3 MemoryRetrievalAgent 的 Prompt 设计

### 9.2 Tool 用户隔离

所有 Tool 通过 `ToolExecutionContext` 获取当前用户：

```java
// 创建 Agent 时注入
ToolExecutionContext toolContext = ToolExecutionContext.builder()
    .register("userContext", new UserToolContext(userId))
    .build();

// Tool 中获取
String userId = context.get(UserToolContext.KEY, UserToolContext.class).userId();
```

### 9.3 MemoryTools 改进

当前 `MemoryTools` 设计已基本合理，需要增加：
- `update_memory_index` tool：MemoryAgent 更新 MEMORY.md 概要
- `write_session_summary` tool：MemoryAgent 写入会话摘要（当前由 MemoryAgentService 直接操作 repository，应改为通过 tool 调用）

```java
@Tool(name = "write_session_summary",
      description = "将会话摘要写入数据库（用于语义检索历史）。"
          + "当对话中有值得记录的讨论内容时调用。")
public String writeSessionSummary(
    @ToolParam(name = "summary", required = true,
               description = "会话摘要（简洁描述关键内容，如'用户询问了 RAG 的召回阈值设置'）")
    String summary,
    @ToolParam(name = "conversation_id", required = true)
    Long conversationId,
    ToolExecutionContext context
) {
    String userId = getUserId(context);
    float[] embedding = embeddingAdapter.embed(summary);
    SessionSummary ss = SessionSummary.createWithEmbedding(
        conversationId, userId, summary, embedding, 0.5, List.of());
    sessionSummaryRepository.save(ss);
    qdrantVectorStore.upsertSessionSummary(ss);
    return "会话摘要已写入";
}

@Tool(name = "update_memory_index",
      description = "更新 MEMORY.md 概要，同步 preferences 和 behaviors 的概要信息。"
          + "当 preferences 或 behaviors 有更新时必须调用。")
public String updateMemoryIndex(ToolExecutionContext context) {
    String userId = getUserId(context);
    memoryService.rebuildMemoryIndex(userId);
    return "MEMORY.md 概要已更新";
}
```

---

## 十、存储设计

### 10.1 存储总览

| 数据 | 存储位置 | 生命周期 | 用途 |
|------|---------|---------|------|
| 当前消息（Working） | AutoContextMemory（内存） | 单次 Agent 调用 | ReAct Loop 上下文 |
| 检索缓存（Short-term） | Redis `oc:memory:context:*` | TTL 1小时 | 本轮会话的记忆上下文 |
| 检索锁（Short-term） | Redis `oc:memory:retrieval-lock:*` | TTL 30秒 | 防止重复检索 |
| MEMORY.md + references | Postgres `memories` 表 | 永久 | 用户偏好/行为规则 |
| session_summaries | Postgres `session_summaries` 表 | 永久（STM 可衰减） | 会话摘要元数据 |
| session_summaries 向量 | Qdrant `session_summaries` 集合 | 永久 | 语义检索 |
| 完整消息历史 | Postgres `messages` 表 | 永久 | 对话回放、MemoryAgent 分析 |
| 对话元数据 | Postgres `conversations` 表 | 永久 | 对话管理 |

### 10.2 Session 持久化（可选）

当需要跨应用重启恢复 Agent 状态时，使用 AgentScope Session：

```java
// 保存
Session session = new JsonSession(Path.of("sessions"));
agent.saveTo(session, userId + ":" + conversationId);

// 恢复
agent.loadIfExists(session, userId + ":" + conversationId);
```

当前架构每次请求重建 Agent（从 Postgres 加载历史消息），Session 持久化暂非必需。

### 10.3 Qdrant 集合

| 集合 | 维度 | 距离 | 用途 |
|------|------|------|------|
| `questions` | 1024 | Cosine | 面试题向量检索 |
| `session_summaries` | 1024 | Cosine | 会话摘要语义检索 |

---

## 十一、并发控制

### 11.1 并发模型

| 场景 | 控制机制 | 说明 |
|------|---------|------|
| 同会话重复检索 | Redis 锁 `oc:memory:retrieval-lock:{userId}:{convId}` | TTL 30s，fire-and-forget 前加锁 |
| 同用户 Preferences 更新 | Redis 锁 `oc:memory:update-lock:{userId}:preferences` | 防止多个 MemoryAgent 并发写 |
| Agent 实例并发 | 每次请求创建新 ReActAgent | AgentScope 约束：Agent 不可并发调用 |
| 游标互斥 | `<memory_write>` 标记 + message_cursor | 主 Agent 写入后后台 Agent 跳过 |

### 11.2 一致性保证

```
主 Agent 写入记忆（用户显式请求）
    ↓ 响应中带 <memory_write> 标记
    ↓
PostCall Hook → 后台 MemoryAgent 检查游标
    ↓ 发现 <memory_write> → 跳过本次更新
    ↓ 推进游标到最新消息
```

---

## 十二、现有代码改造清单

### 12.1 ChatAgentService 改造

| 改造项 | 说明 | 优先级 |
|--------|------|--------|
| InMemoryMemory → AutoContextMemory | 用 AutoContextMemory 管理上下文窗口 | P0 |
| 注册 ContextOffloadTool | Agent 可按需重载被卸载的大内容 | P0 |
| 注册 MemoryInjectionHook | PreCall 注入记忆上下文（替代手动 buildMemoryContext） | P0 |
| 注册 MemoryPostProcessingHook | PostCall 触发异步 MemoryRetrievalAgent + MemoryAgent | P0 |
| 集成 OfferCatcherLongTermMemory | 对接 LongTermMemory 接口 | P1 |
| 集成 SkillBox | MEMORY.md + 用户 Skill 通过 SkillBox 管理 | P1 |

### 12.2 MemoryRetrievalAgent（新增，替代 MemoryRetrievalService 的核心逻辑）

| 改造项 | 说明 | 优先级 |
|--------|------|--------|
| 实现 `MemoryRetrievalAgent` | 独立检索 Agent，分析意图 → 多角度检索 → 精炼输出 | P0 |
| `MemoryRetrievalService` 职责收缩 | 退化为工具提供者（底层 search + Redis 缓存管理），不再包含检索决策 | P0 |
| 降级查询接口 | `findTopKByImportance(userId, limit)` 同步降级查询 | P0 |

### 12.3 MemoryAgentService 改造

| 改造项 | 说明 | 优先级 |
|--------|------|--------|
| 增加 `write_session_summary` tool | MemoryAgent 通过 tool 写入摘要（替代直接操作 repository） | P0 |
| 增加 `update_memory_index` tool | MemoryAgent 通过 tool 刷新 MEMORY.md | P0 |
| 对接游标互斥 | 检查 `<memory_write>` 标记再决定是否写入 | P1 |

### 12.4 新增类

| 类 | 位置 | 说明 |
|------|------|------|
| `MemoryRetrievalAgent` | `application/agent/` | 独立检索 Agent，Short-term Memory 核心 |
| `OfferCatcherLongTermMemory` | `infrastructure/memory/` | 自定义 LongTermMemory 实现 |
| `MemoryInjectionHook` | `infrastructure/memory/` | PreCall/PreReasoning 记忆注入 Hook |
| `MemoryPostProcessingHook` | `infrastructure/memory/` | PostCall 记忆后处理 Hook |
| `SkillBoxConfiguration` | `infrastructure/config/` | SkillBox 初始化配置 |

### 12.5 不变更

| 模块 | 说明 |
|------|------|
| `SessionSummary` 实体 | 设计已完善 |
| `Memory` 聚合根 | 设计已完善 |
| `MemoryReference` 值对象 | 设计已完善 |
| `MemoryRetrievalService` | 职责收缩为工具提供者，保留 Redis 缓存管理和底层检索实现 |
| `MemoryTools`（load/save/search） | Tool 设计合理，增加 write_session_summary、update_memory_index |
| `MemoryApplicationService` | CRUD 编排合理 |
| Postgres + Qdrant 存储 | 双写机制已实现 |
| `UserToolContext` | 用户隔离机制合理 |

---

## 十三、实现优先级

### Phase 1：Working Memory 升级（P0，~2天）

1. 引入 `AutoContextMemory` 依赖（`agentscope-extensions-autocontext-memory`）
2. ChatAgentService 替换 `InMemoryMemory` → `AutoContextMemory`
3. 注册 `ContextOffloadTool`
4. 测试：长对话（50+轮）场景验证压缩效果

### Phase 2：异步 Agent 检索 + Hook 驱动（P0，~3天）

1. 实现 `MemoryRetrievalAgent`（异步模式：`retrieveAsync` + `getCachedContext`）
2. `MemoryRetrievalService` 职责收缩为工具提供者（底层 search + Redis 缓存管理）
3. 实现降级查询逻辑（`findTopKByImportance`，<10ms）
4. 实现 `MemoryInjectionHook` + `MemoryPostProcessingHook`
5. ChatAgentService 注册 Hook，替换手动 `buildMemoryContext()` 和 `postProcess()`
6. 测试：异步检索缓存、降级逻辑、话题连续与切换场景

### Phase 3：Long-term Memory 标准化（P1，~3天）

1. 实现 `OfferCatcherLongTermMemory`
2. 实现 `SkillBoxConfiguration`（MEMORY.md + 用户 Skill 注册）
3. ChatAgentService 集成 `SkillBox`
4. MemoryAgentService 增加 `write_session_summary` 和 `update_memory_index` tool
5. 测试：MEMORY.md 渐进式加载、Tool 按需调用

### Phase 4：同步 Agent 检索 + Session 持久化（P2，~3天）

1. 实现 `MemoryRetrievalAgent.retrieveSync()`（同步模式，maxIters=2，更小模型）
2. 验证小模型同步延迟（目标 <400ms）
3. 实现 Agent 状态 Session 保存/恢复（`JsonSession` 或自定义 Postgres Session）
4. 测试：同步检索延迟、重启后对话连续性

---

## 附录 A：AgentScope 版本兼容性

| 组件 | 版本 | 注意 |
|------|------|------|
| AgentScope Core | 1.0.11 | `ReActAgent`, `Memory`, `LongTermMemory`, `Hook` |
| AgentScope Extensions | 1.0.11 | `AutoContextMemory` 在 extensions 包中 |
| Spring Boot | 3.5.14 | 管理 AgentScope Bean 生命周期 |

## 附录 B：与 Python 方案对应关系

| Python 方案概念 | Java 实现 |
|----------------|----------|
| MEMORY.md | `AgentSkill` 注册到 `SkillBox` |
| references/*.md | `AgentSkill.resources` |
| session_summaries 表 | `SessionSummary` JPA Entity + Qdrant |
| MemoryAgent | `MemoryAgentService` (ReActAgent) |
| MemoryRetrievalService | `MemoryRetrievalAgent`（独立检索 Agent，非 RAG 管道） |
| load_memory_reference tool | `MemoryTools.loadMemoryReference()` |
| search_session_history tool | `MemoryTools.searchSessionHistory()` |
| 游标互斥 | `<memory_write>` 标记 + message_cursor |
| 检索锁 Redis | Redis lock（由 MemoryRetrievalAgent 持有） |
| Stop Hook → MemoryAgent | `MemoryPostProcessingHook` (PostCallEvent) |
