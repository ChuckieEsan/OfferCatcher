# OfferCatcher：面经智能体系统

**版本**: v4.0 (Java Spring Boot + AgentScope 重构)
**核心定位**: 基于 Multi-Agent 架构与混合 RAG 的面经收集、结构化图谱分析与智能对练系统。

---

## 产品背景与核心价值

### 痛点分析

当前求职者在复习"大模型/Agent开发"面经时面临三大痛点：
1. **数据非结构化与噪音大**: 小红书/牛客网面经多为长截图，包含大量寒暄废话，难以提取结构化考点。
2. **缺乏时效性与标准答案**: LLM 技术迭代极快（如 MCP、Agentic RAG 等），依靠死记硬背或大模型幻觉生成的答案往往是过时或错误的。
3. **缺乏宏观统计与个性化追踪**: 无法得知"字节最近最爱考什么"，也无法追踪自己对某道题的掌握程度。

### 核心价值与解决方案

- **多模态面经提取**: 支持文本和图片输入，利用 Vision LLM + OCR 进行结构化提取，智能分类（knowledge/project/behavioral/scenario/algorithm），过滤无效内容。
- **异步解析流程**: 提交后立即返回任务 ID，Worker 后台解析，用户可随时查看进度和编辑结果，确认后入库。
- **两阶段检索架构**: ONNX 本地 Embedding 向量召回 + BGE-Reranker 交叉编码器精排，兼顾速度与准确率。
- **Multi-Agent 异步答疑**: RabbitMQ 削峰填谷，ReActAgent 调度带联网搜索能力的子 Agent 异步生成最新标准答案。
- **混合存储架构**: Qdrant 向量库（语义检索） + PostgreSQL（元数据） + Neo4j 图数据库（考频统计与知识点关联） + Redis（缓存）。
- **语音交互**: WebSocket 实时语音识别（讯飞 ASR），支持语音输入面经和对话。
- **智能聚类与岗位归一化**: KMeans 定时聚类 + LLM 岗位名归一化，自动发现考点群组。
- **DDD 架构**: 领域驱动设计，清晰的分层架构，高可维护性和可测试性。
- **多用户隔离**: 所有数据按用户隔离，支持多用户并行使用。

---

## 系统架构

### DDD 四层架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         interfaces/ (接口层)                                  │
│  REST Controllers: chat, interview, extract, questions, stats, favorites    │
│  DTOs: Java records 作为请求/响应模型                                          │
│  WebSocket: 实时语音识别                                                       │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      application/ (应用层)                                    │
│  Services: 用例编排（Chat, Interview, Question, Retrieval, Ingest, Stats）   │
│  Agents: ReActAgent 执行器（Chat, Interview, Vision, Answer, Scorer, Title） │
│  Workers: 后台任务（Answer, Extract, Clustering, Reembed, PositionNorm）     │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        domain/ (领域层)                                       │
│  question: Question, ExtractTask 聚合                                        │
│  interview: InterviewSession 聚合                                            │
│  chat: Conversation 聚合                                                     │
│  memory: Memory, SessionSummary 聚合                                          │
│  favorite: Favorite 聚合                                                     │
│  shared: Enums, Domain Exceptions, Events                                    │
│  Repository Protocol: 仓库接口定义                                            │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    infrastructure/ (基础设施层)                                │
│  persistence: PostgreSQL JPA, Qdrant gRPC, Neo4j driver                     │
│  messaging: RabbitMQ Producer, Consumer                                      │
│  adapters: ONNX Embedding/Reranker, Redis Cache, Tavily Search, OCR, ASR    │
│  tools: AgentScope @Tool（SearchQuestions, WebSearch, KnowledgeGraph）       │
│  config: Spring @ConfigurationProperties, Bean 配置                          │
│  observability: Health Indicators, OTLP Telemetry                            │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 项目结构

```text
OfferCatcher/
├── src/main/java/com/zju/offercatcher/
│   ├── domain/                         # 领域层 (DDD 核心)
│   │   ├── chat/                       # 对话领域
│   │   │   ├── aggregates/             # Conversation 聚合根
│   │   │   ├── entities/               # Message 实体
│   │   │   └── repositories/           # Repository 接口
│   │   ├── interview/                  # 模拟面试领域
│   │   ├── question/                   # 题库领域 (Question, ExtractTask)
│   │   ├── memory/                     # 记忆领域 (Memory, SessionSummary)
│   │   ├── favorite/                   # 收藏领域
│   │   └── shared/                     # 共享内核 (Enums, Exceptions)
│   │
│   ├── application/                    # 应用层
│   │   ├── agent/                      # Agent 执行器
│   │   │   ├── ChatAgentService        # 对话 ReActAgent
│   │   │   ├── InterviewAgentService   # 面试编排 Agent
│   │   │   ├── MemoryAgentService      # 记忆提取 Agent
│   │   │   ├── VisionExtractorAgent    # 面经提取 Agent
│   │   │   ├── AnswerSpecialistAgent   # 答案生成 Agent
│   │   │   ├── ScorerAgent             # 评分 Agent
│   │   │   ├── TitleGeneratorAgent     # 标题生成 Agent
│   │   │   └── PromptLoader            # Prompt 模板加载器
│   │   ├── service/                    # 应用服务 (用例编排)
│   │   │   ├── ChatApplicationService
│   │   │   ├── InterviewApplicationService
│   │   │   ├── QuestionApplicationService
│   │   │   ├── RetrievalApplicationService
│   │   │   ├── IngestFlowService
│   │   │   ├── ClusteringApplicationService
│   │   │   ├── PositionNormalizationService
│   │   │   └── StatsApplicationService
│   │   └── worker/                     # 后台任务
│   │       ├── AnswerGenerationWorker
│   │       ├── AnswerTaskConsumer      # RabbitMQ 消费者
│   │       ├── ExtractTaskWorker
│   │       ├── ClusteringWorker
│   │       ├── PositionNormalizationWorker
│   │       └── ReembedWorker
│   │
│   ├── infrastructure/                 # 基础设施层
│   │   ├── persistence/
│   │   │   ├── postgres/               # JPA Entity + Repository Impl
│   │   │   ├── qdrant/                 # QdrantVectorStore + QuestionRepository
│   │   │   └── neo4j/                  # Neo4jClient (图数据库)
│   │   ├── messaging/                  # RabbitMQ Producer, Consumer
│   │   ├── adapters/                   # 外部服务适配器
│   │   │   ├── embedding/              # ONNX BGE-M3 Embedding
│   │   │   ├── reranker/               # ONNX BGE-Reranker
│   │   │   ├── cache/                  # Redis Cache
│   │   │   ├── websearch/              # Tavily 联网搜索
│   │   │   ├── ocr/                    # EasyOCR 微服务
│   │   │   └── asr/                    # 讯飞语音识别
│   │   ├── tools/                      # AgentScope @Tool 函数
│   │   ├── config/                     # @ConfigurationProperties 配置
│   │   ├── observability/              # HealthIndicator, Telemetry
│   │   └── common/                     # SnowflakeId, CacheKeys
│   │
│   └── interfaces/                     # 接口层
│       ├── controller/                 # REST Controller (10 个)
│       ├── dto/                        # Request/Response DTO (9 组)
│       ├── websocket/                  # Speech WebSocket Handler
│       └── config/                     # WebMvcConfig, GlobalExceptionHandler
│
├── src/main/resources/
│   ├── application.properties          # 应用配置
│   └── prompts/                        # Agent Prompt 模板 (9 个)
│
├── src/test/java/                      # 测试 (226 个，全部通过)
├── compose.yaml                        # Docker Compose (基础设施服务)
├── pom.xml                             # Maven 配置
└── CLAUDE.md                           # AI 编码指导
```

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 21 |
| 框架 | Spring Boot 3.5.14 |
| Agent 框架 | AgentScope 1.0.11 |
| 向量数据库 | Qdrant (gRPC) |
| 关系数据库 | PostgreSQL (JPA) |
| 图数据库 | Neo4j |
| 消息队列 | RabbitMQ |
| 缓存 | Redis |
| Embedding | ONNX BGE-M3 (本地部署) |
| Reranker | ONNX BGE-Reranker-Base (本地部署) |
| LLM | DeepSeek / OpenAI / SiliconFlow / DashScope |
| Web 搜索 | Tavily |
| OCR | EasyOCR (Python 微服务) |
| ASR | 讯飞语音识别 (WebSocket) |
| 可观测性 | OpenTelemetry (OTLP) + Actuator |
| ID 生成 | Hutool Snowflake (64-bit) |

---

## 快速开始

### 环境要求

- JDK 21
- Docker & Docker Compose
- Maven Wrapper (项目自带 `./mvnw`)

### 1. 启动依赖服务

```bash
docker compose up -d

# 验证服务
# PostgreSQL:  localhost:5432  (root/root, offer_catcher_v2)
# Qdrant:      localhost:6333  (HTTP), localhost:6334 (gRPC)
# RabbitMQ:    localhost:5672  (guest/guest), http://localhost:15672
# Neo4j:       localhost:7687  (bolt), http://localhost:7474
# Redis:       localhost:6379
```

### 2. 配置环境变量

在项目根目录创建 `.env` 文件：

```bash
# LLM API Keys
DEEPSEEK_API_KEY=sk-xxxxx
OPENAI_API_KEY=
SILICONFLOW_API_KEY=sk-xxxxx
DASHSCOPE_API_KEY=sk-xxxxx

# Web Search
TAVILY_API_KEY=tvly-xxxxx

# 外部服务
QDRANT_HOST=localhost
REDIS_HOST=localhost
REDIS_PASSWORD=
RABBITMQ_HOST=localhost
NEO4J_URI=bolt://localhost:7687
NEO4J_USER=neo4j
NEO4J_PASSWORD=password
OCR_SERVICE_URL=http://localhost:8001
```

### 3. 编译并启动

```bash
# 编译
./mvnw clean compile

# 运行所有测试
./mvnw test

# 启动应用 (端口 8000)
./mvnw spring-boot:run
```

访问 http://localhost:8000/actuator/health 查看健康检查。

---

## API 接口

所有接口使用 `X-User-Id` 请求头进行用户隔离。完整 API 可通过 Swagger UI 查看（启动后访问 `/swagger-ui.html`）。

### 面经解析

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/extract/text` | POST | 文本同步提取 |
| `/api/v1/extract/image` | POST | 图片上传提取 (multipart) |
| `/api/v1/extract/image/base64` | POST | Base64/URL 图片提取 |
| `/api/v1/extract/submit` | POST | 提交异步解析任务 |
| `/api/v1/extract/tasks` | GET | 获取任务列表 |
| `/api/v1/extract/tasks/{id}` | GET | 获取任务详情 |
| `/api/v1/extract/tasks/{id}` | PUT | 编辑解析结果 |
| `/api/v1/extract/tasks/{id}/confirm` | POST | 确认入库 |
| `/api/v1/extract/tasks/{id}/cancel` | POST | 取消任务 |
| `/api/v1/extract/tasks/{id}` | DELETE | 删除任务 |

### 题目管理

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/questions` | GET | 题目列表（支持 company/position/type/mastery/keyword/clusterId 过滤） |
| `/api/v1/questions` | POST | 创建题目 |
| `/api/v1/questions/{id}` | GET/PUT/DELETE | 单个题目 CRUD |
| `/api/v1/questions/{id}/regenerate` | POST | 重新生成答案 |
| `/api/v1/questions/{id}/publish` | POST | 发布到公共库 |
| `/api/v1/questions/batch/answers` | POST | 批量获取答案 |

### 搜索

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/search` | POST | 语义搜索（向量召回 + Rerank 精排） |

### 对话

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/chat/stream` | POST | SSE 流式对话 |
| `/api/v1/conversations` | GET/POST | 会话列表 / 创建 |
| `/api/v1/conversations/{id}` | GET/DELETE | 会话详情 / 删除 |
| `/api/v1/conversations/{id}/title` | PUT | 更新标题 |
| `/api/v1/conversations/{id}/generate-title` | POST | AI 自动生成标题 |

### 模拟面试

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/interview/sessions` | GET/POST | 面试列表 / 创建 |
| `/api/v1/interview/sessions/{id}` | GET/DELETE | 面试详情 / 删除 |
| `/api/v1/interview/sessions/{id}/answer` | POST | SSE 流式：提交答案并获取反馈 |
| `/api/v1/interview/sessions/{id}/hint` | POST | SSE 流式：获取提示 |
| `/api/v1/interview/sessions/{id}/skip` | POST | 跳过当前题 |
| `/api/v1/interview/sessions/{id}/pause` | POST | 暂停面试 |
| `/api/v1/interview/sessions/{id}/resume` | POST | 恢复面试 |
| `/api/v1/interview/sessions/{id}/end` | POST | 结束面试 |
| `/api/v1/interview/sessions/{id}/report` | GET | 面试报告 |

### 统计

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/stats/overview` | GET | 总览统计 |
| `/api/v1/stats/companies` | GET | 公司维度统计 |
| `/api/v1/stats/positions` | GET | 岗位维度统计 |
| `/api/v1/stats/entities` | GET | 考点实体统计 |
| `/api/v1/stats/clusters` | GET | 聚类统计 |

### 收藏

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/favorites` | GET/POST | 收藏列表 / 添加 |
| `/api/v1/favorites/{id}` | DELETE | 取消收藏 |
| `/api/v1/favorites/by-question/{questionId}` | DELETE | 按题目取消收藏 |
| `/api/v1/favorites/check` | POST | 批量检查收藏状态 |

### 评分

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/score` | POST | 用户答案评分 |

### 记忆

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/v1/memory/me` | GET | 获取完整记忆画像 |
| `/api/v1/memory/me/content` | GET | 获取记忆内容 |
| `/api/v1/memory/me/preferences` | GET/PUT | 偏好管理 |
| `/api/v1/memory/me/behaviors` | GET/PUT | 行为模式管理 |

### WebSocket

| 端点 | 说明 |
|------|------|
| `ws://host/ws/speech` | 实时语音识别（讯飞 ASR） |

---

## 后台任务

应用启动后自动运行以下定时任务（可通过 `application.properties` 控制开关和频率）：

| Worker | 频率 | 说明 |
|--------|------|------|
| ExtractTaskWorker | 每 5s | 轮询处理未完成的异步解析任务 |
| AnswerGenerationWorker | 每 10s | 轮询未生成答案的题目，异步生成 |
| AnswerTaskConsumer | 实时 | RabbitMQ 消费者，接收答案生成任务 |
| ReembedWorker | 每 60s | 检测题目变更，重建 Qdrant 向量 |
| ClusteringWorker | 每小时 | KMeans 聚类 + Neo4j 图同步 |
| PositionNormalizationWorker | 每天 | LLM 岗位名归一化 |

---

## 开发

### 测试

```bash
# 全量测试
./mvnw test

# 单个测试类
./mvnw test -Dtest=ChatApplicationServiceTest

# 单个测试方法
./mvnw test -Dtest=ChatApplicationServiceTest#testMethod
```

### 静态分析

```bash
# SpotBugs 代码质量检查
./mvnw verify
```

### 项目特色

- **多用户隔离设计**: 所有 API 通过 `X-User-Id` 头标识用户，领域层全面支持用户级数据隔离。
- **Agent 高可用**: Agent 实例无状态，每次请求创建新实例，避免并发问题。
- **ONNX 本地推理**: Embedding 和 Reranker 均在本地 ONNX Runtime 运行，无需调用外部 API，降低延迟和成本。
- **懒加载 Neo4j**: 图数据库连接按需建立，不影响启动速度。

---

## 文档

- [AI 编码指导](CLAUDE.md)
- AgentScope: [官方文档](https://agentscope.io)
- Qdrant: [官方文档](https://qdrant.tech/documentation/)

---

## License

MIT
