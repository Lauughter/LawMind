# 可观测性

> 版本：V1.0 | 日期：2026-08-12 | 状态：🚧 部分实现（指标/日报/AOP 已实现；Prometheus/Langfuse/告警 规划中）
> 事实源：`（旧文档《可观测性优化方案》已分解合并入本文）`、`D:\develop\Code\LawMind\docs\06-reference\rag-gap-analysis.md` §2.4、源码 `RagMetricsServiceImpl.java`、`MetricsPersistenceScheduler.java`、`RagMetricsController.java`、`AgentMetricsCollector.java`、AOP 切面
> 关联：`overview.md`、`evaluation.md`（在线质量指标）、`agent-orchestration.md`（AgentMetricsCollector）

---

## 一、模块范围

覆盖 **指标采集 → 持久化日报 → 访问统计 → AOP 日志**。当前已实现 Redis 实时指标 + MySQL 日聚合 + 管理端 API；Prometheus/Grafana/Langfuse/告警属路线图规划项。

| 子模块 | 关键类 | 涉及表 | 说明 |
|--------|--------|--------|------|
| RAG 指标采集 | `RagMetricsServiceImpl` | Redis（14 天 TTL） | 请求/延迟/来源/反馈聚合 |
| 指标日报持久化 | `MetricsPersistenceScheduler` | MySQL `rag_metrics_daily` | 每日 01:07 同步 + 启动补漏 14 天 |
| 指标 API | `RagMetricsController` | — | /admin/metrics/today、/trend、/quality/* |
| Agent 监控 | `AgentMetricsCollector` | — | Token/工具/压缩/门控统计 |
| AOP 日志 | `Controller/Service/SchedulerLogAspect` | — | 三层切面 + @Log/@NoLog |
| 链路追踪 | `LogTraceInterceptor` | — | reqId 写入 MDC |
| 审计 | `SecurityAuditAspect` | `security_audit_log` | 见 `security-safety.md` |

---

## 二、核心业务流程

### 2.1 指标采集与持久化

```
RagServiceImpl.processQuestion() 每轮请求
  │  RagMetricsServiceImpl.recordRequest(source, pre/embed/search/gen/total ms, retrieved, topScore, hydeEnabled, feedback)
  ▼
Redis Hash  rag:metrics:daily:{yyyyMMdd}:hash     ← total / src:{source} / latency_sum / pre/embed/search/gen_sum / top_score_max / hyde_count / feedback_up|down
Redis ZSet  rag:metrics:daily:{yyyyMMdd}:latency  ← 延迟样本（P50/P95 分位）
  ▼（TTL 14 天）
MetricsPersistenceScheduler 每日 01:07 cron
  ▼
MySQL rag_metrics_daily（upsert）+ 启动 @PostConstruct 补漏最近 14 天
  ▼
RagMetricsController 查询（Redis 实时优先，MySQL 回退）→ 质量看板 / 趋势
```

### 2.2 日报字段（`rag_metrics_daily`）

`total_requests`、`knowledge_hits`/`llm_direct_count`/`non_legal_count`（来源分布）、`avg/p50/p95_latency_ms`、`hyde_count`、`total_likes`/`total_dislikes`、`avg_top_score`、`llm_fallback_rate`、`feedback_inaccurate/wrong_citation/irrelevant/too_vague/other`。

### 2.3 AOP 日志三层切面

| 切面 | 切入 | 特点 |
|------|------|------|
| `ControllerLogAspect` | `controller..*(..)`，@Order(10) | 支持 @NoLog 跳过 / @Log 定制；MultipartFile 只记文件名+大小 |
| `ServiceLogAspect` | service 层 | 记录方法与耗时 |
| `SchedulerLogAspect` | `@Scheduled` 任务 | 执行时间 + 成功/失败 |
| `LogTraceInterceptor` | 请求进入 | 生成 `reqId` 写 MDC（userId/convId 待补全，见 🚧） |

---

## 三、业务规则

- 指标 Redis TTL 14 天，`rag_metrics_daily` 为长期历史层（每日覆盖 upsert）。
- P50/P95 由 ZSet 样本按 index 计算（`latSize*50/100`、`latSize*95/100`）。
- LLM 兜底率 = `src:llm_direct / total`，是知识库覆盖不足的核心信号。
- 质量趋势接口在 Redis 全空时回退 MySQL（`qualityTrend` 的 MySQL fallback）。
- 管理端 API 受 `lawmind.admin-user-id`（默认 4）权限控制。

---

## 四、关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| Redis 实时 + MySQL 历史双层 | 14 天 TTL 实时层 + 每日快照层 | 兼顾实时查询与长期趋势，双保险 |
| 延迟分阶段采集 | pre/embed/search/gen 分桶 | 定位 RAG 各子步骤瓶颈 |
| 反馈原因结构化 | 预设类别（inaccurate/wrong_citation/…）计数 | 回答质量 vs 检索 vs 知识库问题快速归类 |
| Agent 监控独立 | `AgentMetricsCollector` AtomicLong + ConcurrentHashMap | 工具/压缩/门控独立于 RAG 指标，线程安全 |

---

## 五、实现进度

| 项 | 状态 |
|----|:---:|
| RagMetricsServiceImpl（Redis Hash+ZSet，P50/P95，来源/延迟/反馈） | ✅ |
| MetricsPersistenceScheduler 日报持久化 + 启动补漏 | ✅ |
| RagMetricsController 管理端指标 API | ✅ |
| AgentMetricsCollector（Agent/工具/压缩/门控统计） | ✅ |
| AOP 三层日志切面 + LogTraceInterceptor(reqId) | ✅ |
| **Micrometer + Prometheus + Grafana 指标标准化** | 🚧（Actuator 仅暴露 health/info） |
| **Langfuse / 分布式链路追踪** | 🚧 |
| **告警规则**（LLM 兜底率 / P99 / Rerank 失败率） | 🚧 |
| 异步 MDC 全量补全（vectorize 线程池）+ RequestContext 跨线程传播 | 🚧 |
| logback-spring.xml 统一格式 + MyBatis SQL 走 SLF4J | 🚧 |
| Token 成本追踪（sys_config 热更新价格 + rag_cost_total） | 🚧 |
