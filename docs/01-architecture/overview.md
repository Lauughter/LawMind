# LawMind 系统架构总览

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`D:\develop\Code\LawMind\docs\06-reference\project-tech-details.md`、`D:\develop\Code\LawMind\docs\06-reference\rag-gap-analysis.md`、源码 `D:\develop\Code\LawMind\src\main\java\com\lhs\lawmind\`
> 关联：`rag-retrieval.md`、`agent-orchestration.md`、`memory-system.md`、`evaluation.md`、`security-safety.md`、`observability.md`

---

## 一、模块范围

LawMind 是一个面向法律领域的智能问答系统，核心链路为**意图门控 → 三通道分流（Fast / Hybrid / Agent）→ 多级 RAG 检索管道 → LLM 生成 → 安全后处理**。本文档定义系统整体分层与模块地图，是各模块设计文档的入口。

**技术栈**：Java 17 + Spring Boot 3.5 + LangChain4j 0.36 + Redis Stack（RediSearch）+ MySQL 8 + MyBatis + Vue 3（独立 `frontend/`）+ JWT。LLM 统一走阿里云 DashScope：`qwen-plus`（对话）、`qwen-turbo`（查询改写）、`text-embedding-v2`（1536 维向量）、`qwen3-rerank`（精排）。

| 分层 | 职责 | 关键类 |
|------|------|--------|
| Web 层 | 鉴权、SSE 流式、日志 | `AiChatController`、`AgentController`、`JwtInterceptor` |
| 门控层 | 领域/意图/复杂度/路由四级判定 | `IntentGate`、`DomainGate`、`IntentClassifierEnhanced`、`ComplexityAssessor`、`IntentRouter` |
| 通道层 | Fast/Hybrid/Agent 三通道 | `FastChannelHandler`、`AgentRunner` |
| RAG 检索层 | 预处理、召回、融合、精排 | `RagServiceImpl`、`HybridSearchServiceImpl`、`RerankServiceImpl`、`SearchResultDiversifier` |
| 增强层 | 上下文压缩、记忆 | `ContextCompressor`、`KnowledgeState`、`MemoryManager` |
| 数据层 | MySQL 主存储 + Redis 向量 | MyBatis Mapper、`RedisVectorUtil`、`RedisIndexInitializer` |
| 质量/可观测 | 评估、指标、审计 | `GoldenDatasetEvaluator`、`RagMetricsServiceImpl`、`AgentMetricsCollector`、`SecurityAuditAspect` |

---

## 二、核心业务流程

### 2.1 主链路：意图门控 → 三通道 → RAG 管道 → LLM 生成

```
用户问题
  │
  ▼
[安全前哨] SensitiveTopicFilter 敏感话题拦截 → 法律相关性判定（非法律→拒答 non_legal_reject）
  │
  ▼
[Layer1] DomainGate         ：是否法律问题？（规则 + LLM 兜底，fail-open）
  │
  ▼
[Layer2] IntentClassifier   ：6 种意图（法条查询/金额计算/案例检索/文书起草/知识问答/法律咨询）
  │
  ▼
[Layer3a] ComplexityAssessor：四因子加权 → SIMPLE / MEDIUM / COMPLEX
  │
  ▼
[Layer3b] IntentRouter      ：路由决策 → FAST / AGENT / HYBRID
  │
  ├── FAST ──→ FastChannelHandler ──→ 单次 LLM 调用（无工具）
  ├── HYBRID ─→ 模板填充 + 可选检索（文书/合同）
  └── AGENT ──→ AgentRunner（ReAct 循环 ≤5 轮，6 个工具，上下文压缩 + 记忆注入）
                     │
                     ▼
               RAG 检索管道（Agent 工具 searchLawKnowledge 亦复用同一管道）
  │
  ▼
[多级 RAG 检索] 预处理 → LLM 改写+向量化 → 混合召回 → RRF → Rerank → MMR → 双阈值
  │
  ▼
[LLM 生成] 有知识 → 知识增强回答；无知识 → llm_direct
  │
  ▼
[后处理] 引用验证(UNVERIFIED 警告) + 合规声明 + 异步（访问记录/指标采集/记忆提取）
  │
  ▼
返回 AIChatResponse / SSE(token|knowledge|done|error)
```

### 2.2 快慢分流收益

| 维度 | Fast Channel | Agent Channel |
|------|-------------|---------------|
| LLM 调用次数 | 1 次 | 2–6 次 |
| 平均耗时 | < 2 秒 | 3–15 秒 |
| Token 消耗 | ~500 | ~2000–6000 |
| 可用工具 | 0 | 6 个 |
| 适用占比 | ~60% 请求 | ~30% 请求 |

---

## 三、模块地图

> 接口文档与表文档由其它分层生成（`../03-api/`、`../02-db/`），此处列关联路径。

| 模块 | 状态 | 设计文档 | 接口文档 | 数据表 |
|------|:---:|----------|----------|--------|
| 意图门控 + Agent 编排 | ✅ | `agent-orchestration.md` | `../03-api/modules/chat.md` | `ai_chat` |
| 多级 RAG 检索 | ✅ | `rag-retrieval.md` | `../03-api/modules/chat.md` | `law_knowledge`、`knowledge_chunk`、`law_vector_task` |
| 上下文压缩 | ✅ | `agent-orchestration.md` | — | — |
| 记忆系统 | ✅（衰减定时 🚧） | `memory-system.md` | `../03-api/modules/memory.md` | `ai_memory` |
| 质量评估 | ✅ | `evaluation.md` | `../03-api/modules/ops.md` | `evaluation_report`、`review_log` |
| 安全守卫 | ✅ | `security-safety.md` | — | `security_audit_log` |
| 可观测性 | 🚧 部分 | `observability.md` | `../03-api/modules/ops.md` | `rag_metrics_daily` |
| 用户/会话 | ✅ | — | `../03-api/modules/auth.md` | `user`、`conversation` |

---

## 四、关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 三通道分流 | Fast / Hybrid / Agent 按意图+复杂度路由 | 简单问题（法条查询）无需工具链，省 3–10 倍 Token 与延迟；复杂问题保质量 |
| 门控 fail-open | 门控异常时降级放行到 Agent 通道 | 「宁可错放，不可错拦」，避免误拦法律咨询 |
| 多级检索管道 | 混合召回→RRF→Rerank→MMR→双阈值 | 每级解决不同问题：召回(向量+全文)→融合(RRF)→精度(Rerank)→多样性(MMR)→严格度(阈值) |
| 渐进式降级 | 任一外部服务失败即降级，不中断请求 | Embedding 挂→纯全文；全文不可用→纯向量；两者都挂→LLM 直答 |
| Redis 作向量库 | RediSearch FLAT/COSINE 索引，FLOAT32 小端存储 | 复用现有 Redis，小规模（<50 万条）够用，避免引入 Milvus/ES |
| 模型分工 | qwen-plus 生成 / qwen-turbo 改写 / v2 向量 / rerank 精排 | 按任务复杂度选型，改写用便宜模型降本 |
| 可插拔增强 | MemoryManager/ContextCompressor 均可空/可关 | 关闭增强时行为与原版完全一致，便于灰度 |

---

## 五、实现进度

| 项 | 状态 |
|----|:---:|
| 意图门控四级流水线（DomainGate→IntentClassifier→ComplexityAssessor→IntentRouter） | ✅ |
| Fast/Hybrid/Agent 三通道分流 | ✅ |
| 多级 RAG 检索（混合召回 + RRF + Rerank + MMR + 双阈值） | ✅ |
| 上下文压缩（4 层渐进 + KnowledgeState） | ✅ |
| 记忆系统（四类型 + 两级检索 + LLM 提取） | ✅ |
| 记忆归并定时任务（类型衰减/合并清理） | 🚧（配置就绪，无调度实现） |
| 质量评估（Golden Dataset + RAGAS 双维度 + CI 回归门禁） | ✅ |
| 五层安全守卫 + 引用验证闭环 | ✅ |
| 指标采集 + RAG 指标日报 + 访问统计 | ✅ |
| Micrometer/Prometheus/Grafana、Langfuse 追踪、告警 | 🚧（规划中） |
