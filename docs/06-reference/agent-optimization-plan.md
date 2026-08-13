# Agent 子系统优化方案（工作文档）

> 版本：V0.1 | 日期：2026-08-13 | 状态：🚧 规划中（逐项实施）
> 事实源：`agent/` 包源码、`config/AgentConfig.java`、`application.yml`
> 关联：`01-architecture/agent-orchestration.md`、`01-architecture/rag-retrieval.md`、`01-architecture/observability.md`

## 一、目标

在不改变对外行为的前提下，提升 Agent 子系统的**可维护性、可观测性、响应体验**。核心问题：LLM 调用重复散落、AgentRunner 单方法过大、Agent 无流式体验、多轮对话能力弱。

## 二、优化项清单

| # | 优化项 | 优先级 | 现状问题 | 优化方案 | 影响面 | 状态 |
|---|--------|:---:|---------|---------|--------|:---:|
| 1 | LLM 调用统一封装层 | P0 | `chatLanguageModel.generate()` 20 处散落各文件，异常/token/超时/日志各自处理 | 提取 `LLMInvoker`（重试、超时、token 统计、日志、降级） | 新增 `llm/LLMInvoker.java`；迁移 RagServiceImpl/AgentRunner/DomainGate/FastChannelHandler/IntentClassifierEnhanced/MemoryExtractor/SummarizingCompressor/AutoLearn/AutoLearningScheduler/LegalMetadataExtractor/2 评估器 | ✅ |
| 2 | AgentRunner.execute 拆分 | P0 | 300 行单方法，含记忆注入/ReAct 循环/全局压缩/最终答案/记忆提取 | 拆分为 `buildInitialMessages`/`handleToolCalls`/`generateFinalAnswer` | AgentRunner.java | ✅ |
| 3 | 流式 Agent 推理 | P0 | AgentController 用 SSE 但 `execute` 同步，用户等完整循环 | Thought/ToolCall/结果通过 `AgentEvent` 回调 + SseEmitter 逐步推送 | AgentRunner（新增 `execute(..., Consumer<AgentEvent>)`）、AgentController、前端 sse.js/Consultation.vue | ✅ |
| 4 | 多轮对话上下文 | P1 | Agent 每次全新 messages，不加载历史会话，多轮追问弱 | `execute` 增加 `conversationHistory` 参数；AgentController 用 `selectByConversationId` 构建历史注入 | AgentRunner、AgentController | ✅ |
| 5 | 记忆注入独立消息 | P1 | 记忆拼进 systemPrompt，混淆系统指令与用户画像 | 拆为独立 `SystemMessage` | AgentRunner.buildInitialMessages | ✅ |
| 6 | 门控降级统计持久化 | P1 | `recordGateProcess` 只计数不入库，降级率无 KPI | AgentController 记录 `gate_reject`/`gate_degraded` 到 rag_metrics；rag_metrics_daily 新增 2 列 | AgentController、RagMetricsDaily、init_schema.sql | ✅ |
| 7 | FastChannelHandler 复用 RAG 管道 | P1 | Fast 通道与 RagServiceImpl 核心路径重复 | Fast 复用 `RagRetrievalService`（混合检索）+ `RagPromptBuilder`（系统提示词） | FastChannelHandler | ✅ |
| 8 | maxIterations 配置化 | P2 | `MAX_ITERATIONS=5` 硬编码 | 提到 `lawmind.agent.max-iterations` | AgentConfig、application.yml | ✅ |
| 9 | Agent 总耗时超时 | P2 | 多轮循环无总超时，可能拖死线程 | 整体超时兜底（`lawmind.agent.max-duration-ms`，默认 60s），超时强制收尾 | AgentRunner、AgentConfig | ✅ |
| 10 | 工具参数解析增强 | P2 | 仅支持简单类型，复杂对象可能失败且静默 | `parseArg` 支持 String/数值/布尔/List/复杂对象，类型不匹配明确报错 | AgentRunner.resolveArgs | ✅ |
| 11 | 参数匹配只按名 | P2 | 位置兜底匹配逻辑脆弱 | 移除按位置兜底，只按参数名匹配（缺失保持 null 兼容可选参数） | AgentRunner.resolveArgs | ✅ |
| 12 | TokenEstimator 校准 | P2 | 中文场景字符/token 估算可能偏差 | 中文 1.5→1.2 字符/token，其他字符 0.3→0.25 | TokenEstimator | ✅ |
| 13 | RagServiceImpl 拆分 | P1 | 1424 行上帝类，违反 <800 行规范 | 拆出 `rag/` 子包 6 个类：RagRetrievalService / RagPromptBuilder / QueryEnhancer / CitationVerifier / LegalQuestionClassifier / SseStreamHelper | RagServiceImpl → 620 行；新增 `rag/` 包 | ✅ |

状态图例：⬜ 待做 / 🔵 进行中 / ✅ 完成

## 三、实施顺序

1. **P0-1 LLM 统一封装层** → 改动最大、收益最高，先行（影响全链路）
2. **P0-2 AgentRunner 拆分** → 配合封装层重构，提升可读性
3. **P0-3 流式 Agent 推理** → 体验优化，需配合前端
4. **P1 项**（4-7）→ 能力补齐 + 可观测性
5. **P2 项**（8-12）→ 健壮性/细节

每个优化项完成后：`mvn test-compile` + 受影响单元测试，更新本文档状态列为 ✅，同步更新 `01-architecture/agent-orchestration.md`。

## 四、变更记录

| 版本 | 日期 | 变更 |
|------|------|------|
| V0.1 | 2026-08-13 | 初版：基于 Agent 设计评审产出 12 项优化 |
