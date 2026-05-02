# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建命令

```bash
# 编译项目
./mvnw clean compile

# 运行所有测试
./mvnw test

# 运行单个测试类
./mvnw test -Dtest=OfferCatcherApplicationTests

# 运行单个测试方法
./mvnw test -Dtest=OfferCatcherApplicationTests#contextLoads

# 打包（跳过测试）
./mvnw clean package -DskipTests

# 启动应用
./mvnw spring-boot:run
```

## 编码规范

1. 编码开始前，你需要先定义具体的规范，即 Spec-Driven
2. 编码进行中，你需要以 DDD 驱动开发
3. 编码结束后，你需要通过测试用例来保证功能可用，即 Test-Driven

### 其他细节
1. 如果遇到字符串，你需要考虑这部分是否可以抽离为枚举


## 项目架构

这是一个基于 **AgentScope Java** + **Spring Boot 3.5.14** 的 AI Agent 应用，采用 **DDD（领域驱动设计）** 架构。

我们之前有完整的 Python 项目，位于 app 文件夹当中，你在实现时可以参考原有项目的设计思路。我们新项目的最终设计目标是，
（1）支持多用户隔离设计
（2）确保 Agent 生产级别的高可用


### 核心依赖

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 运行时环境 |
| Spring Boot | 3.5.14 | 应用框架 |
| AgentScope | 1.0.11 | Agent 框架（BOM 管理） |
| OpenAI SDK | 0.42.0 | LLM 集成（可选） |
| AssertJ | (managed) | 流畅断言（TDD） |
| TestContainers | (managed) | 容器化集成测试 |

### DDD 分层结构

```
src/main/java/com/zju/offercatcher/
├── domain/          # 领域层：实体、值对象、领域服务、仓储接口
├── application/     # 应用层
│   ├── agent/       #   Agent 类（创建 ReActAgent 调 LLM），统一命名 XxxAgent
│   ├── service/     #   应用服务（纯编排，不调 LLM），统一命名 XxxApplicationService
│   └── worker/      #   后台 Worker（@Scheduled 轮询 / MQ 消费）
├── infrastructure/  # 基础设施层：Tool 实现、Session/Memory、外部集成
├── interfaces/      # 接口层：REST Controller、WebSocket 处理器
└── shared/          # 共享内核：异常、工具类
```

### AgentScope 集成模式

**Agent 属于应用层** - 通过 Tool 编排领域服务，不直接实现业务逻辑。

**Tool 应委托给领域服务** - 不要在 Tool 类中实现业务逻辑：

```java
// 基础设施层 - Tool 作为代理
public class OfferDomainTools {
    private final OfferAnalysisService domainService;  // 注入领域服务

    @Tool(name = "analyze_offer", description = "分析 offer")
    public String analyze(@ToolParam(name = "offer_id") String id) {
        return domainService.analyze(id).toJson();  // 委托给领域服务
    }
}
```

**Agent 实例是有状态的，不可并发调用** - 每次请求创建新实例或使用对象池。

**多 Agent 模式选择（参考 AgentScope 文档）**：
- Pipeline：顺序/并行流程（如：收集 → 分析 → 报告）
- Routing：分类后路由到专业 Agent
- Supervisor：一个协调器 + 专业 Tool
- Skills：单 Agent 按需加载领域知识

**如果遇到结构化输出场景，尽可能依赖 AgentScope 的 getStructuredData 方法，并做好二次检查和降级、重试处理
```java
try {
    Msg response = agent.call(userMsg, ProductInfo.class).block();
    ProductInfo data = response.getStructuredData(ProductInfo.class);

    // 业务验证
    if (data.price < 0) {
        throw new IllegalArgumentException("价格无效");
    }
} catch (Exception e) {
    System.err.println("处理失败: " + e.getMessage());
}
```

### 应用层包结构与命名规范

应用层分为三个子包，职责边界如下：

#### `agent/` — Agent 类（统一命名 `XxxAgent`）

**硬标准：内部创建了 `ReActAgent` 并调用 LLM 的类。**

- 所有 Agent 类统一使用 `Agent` 后缀，**禁止**使用 `Service` 后缀
- 包含两类：
  - **编排型 Agent**：带 Tool 的 ReActAgent，如 `ChatAgent`、`InterviewAgent`
  - **单轮调用 Agent**：`maxIters=0` 的 ReActAgent，如 `ScorerAgent`、`TitleGeneratorAgent`

```java
// 正确的命名
ChatAgent                    // 编排型，ReActAgent + 多 Tool
InterviewAgent               // 编排型，ReActAgent + Scorer
ScorerAgent                  // 单轮 LLM 调用
TitleGeneratorAgent          // 单轮 LLM 调用
VisionExtractorAgent         // 单轮 LLM 调用
```

#### `service/` — 应用服务（统一命名 `XxxApplicationService`）

**硬标准：不创建 ReActAgent，不直接调用 LLM。**

- 只做 Repository 调用、流程编排、事务管理
- 可以依赖 Agent 类完成任务
- 统一使用 `ApplicationService` 后缀

```java
// 正确的职责
ChatApplicationService       // 编排 Conversation/Message CRUD，依赖 TitleGeneratorAgent
InterviewApplicationService  // 编排 InterviewSession 生命周期
QuestionApplicationService   // 编排 Question CRUD，依赖 AnswerSpecialistAgent
```

#### 判断归属的硬标准

| 特征 | 归属 |
|------|------|
| 创建了 `ReActAgent` 并调用 LLM | `agent/` |
| 只做 Repository 调用 + 事务编排 | `service/` |

#### `worker/` — 后台 Worker

定时任务或消息消费者，命名区分模式：

- **轮询 Worker**：`XxxWorker`（`@Scheduled` 轮询数据库）
- **MQ Consumer**：`XxxMQConsumer`（`@RabbitListener` 消费消息）

#### `PromptLoader` — 不属于 application 层

`PromptLoader` 是 Markdown 模板加载工具，应放在 `infrastructure/common/`，不属于 `application/agent/`。

#### `UserLongTermMemory` — 不属于 application 层

`UserLongTermMemory` 实现了 AgentScope 的 `LongTermMemory` 接口，是**基础设施适配器**，应放在 `infrastructure/adapters/memory/`。

### 测试结构（TDD）

```
src/test/java/com/zju/offercatcher/
├── domain/          # 领域层单元测试
├── application/     # Agent/命令处理器测试
├── infrastructure/  # Tool/Session 测试
└── integration/     # TestContainers 集成测试
```

使用 AssertJ 断言：`assertThat(result).isEqualTo(expected)`

### 测试安全策略

**禁止在生产数据库上运行测试用例**，所有测试必须在测试数据库中运行。

| 层级 | 测试类型 | 环境 |
|------|----------|------|
| Domain | 单元测试 | 纯 Java（不需要数据库） |
| Infrastructure | 集成测试 | TestContainers（隔离容器） |
| Application | 单元 + 集成 | Mock + TestContainers |
| Interfaces | 集成测试 | TestContainers + MockMvc |

集成测试使用 TestContainers 启动隔离的 Docker 容器：

```java
@Testcontainers
class QdrantQuestionRepositoryImplTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static GenericContainer<?> qdrant = new GenericContainer<>("qdrant/qdrant:latest")
        .withExposedPorts(6333, 6334);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("offercatcher.qdrant.host", qdrant::getHost);
    }
}
```

## 环境变量

LLM 集成所需：
- `DASHSCOPE_API_KEY` - DashScope/Qwen 模型（AgentScope 默认）
- `OPENAI_API_KEY` - OpenAI 模型（可选）

## Docker Compose

`compose.yaml` 当前为空占位符。按需添加服务：
- Session 持久化：MySQL / Redis
- RAG 向量存储：Qdrant / Elasticsearch
- 长期记忆存储