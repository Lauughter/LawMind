# rag_metrics_daily（RAG 指标日报表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：可观测性/评估模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：按日聚合 RAG 系统运行指标（请求量、命中路径、延迟、成本、反馈分布），是系统健康度与质量观测的事实表。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键 |
| metric_date | DATE | 否 | — | 统计日期（唯一，每日一条） |
| total_requests | BIGINT | 是 | 0 | 总请求数 |
| knowledge_hits | BIGINT | 是 | 0 | 知识库命中数 |
| llm_direct_count | BIGINT | 是 | 0 | LLM 直接回答数 |
| non_legal_count | BIGINT | 是 | 0 | 非法律问题拦截数 |
| avg_latency_ms | BIGINT | 是 | 0 | 平均延迟（毫秒） |
| p50_latency_ms | BIGINT | 是 | 0 | P50 延迟 |
| p95_latency_ms | BIGINT | 是 | 0 | P95 延迟 |
| total_likes | BIGINT | 是 | 0 | 点赞总数 |
| total_dislikes | BIGINT | 是 | 0 | 点踩总数 |
| avg_top_score | DECIMAL(5,4) | 是 | NULL | 平均 Top-1 得分 |
| llm_fallback_rate | DECIMAL(5,4) | 是 | NULL | LLM 兜底率 |
| hyde_count | BIGINT | 是 | 0 | HyDE 调用次数 |
| feedback_inaccurate | BIGINT | 是 | 0 | 反馈：不准确 |
| feedback_wrong_citation | BIGINT | 是 | 0 | 反馈：法条引用错误 |
| feedback_irrelevant | BIGINT | 是 | 0 | 反馈：答非所问 |
| feedback_too_vague | BIGINT | 是 | 0 | 反馈：回答太笼统 |
| feedback_other | BIGINT | 是 | 0 | 反馈：其他问题 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_metric_date | 唯一 | metric_date | 每日一条，按日期 upsert/查询 |

## 枚举

无枚举字段（均为数值指标）。反馈分布字段语义：

| 字段 | 含义 |
|------|------|
| feedback_inaccurate | 反馈分类「不准确」计数 |
| feedback_wrong_citation | 反馈分类「法条引用错误」计数 |
| feedback_irrelevant | 反馈分类「答非所问」计数 |
| feedback_too_vague | 反馈分类「回答太笼统」计数 |
| feedback_other | 反馈分类「其他问题」计数 |

## 业务规则

- **每日一条**：按 `metric_date` 唯一（`idx_metric_date`），定时任务聚合当日 ai_chat/反馈数据 upsert，可重跑覆盖。
- **指标口径**：
  - 命中路径：`total_requests = knowledge_hits + llm_direct_count + non_legal_count` 的近似拆分（按各自触发点计数）。
  - 延迟：`avg_latency_ms`/`p50_latency_ms`/`p95_latency_ms` 由请求埋点分位统计。
  - 质量：`avg_top_score`（检索 Top-1 得分均值）、`llm_fallback_rate`（兜底率，[0,1]）、`hyde_count`（HyDE 查询改写调用次数）。
- **反馈分类**：5 个 `feedback_*` 字段来自用户点踩时选择的具体原因（见 review_log/ai_chat.feedback_content）。
- **关系**：独立聚合表，无外键；数据来源于 ai_chat、review_log 等运行事实。
- **用途**：系统可观测仪表盘与质量评估（可被 evaluation_report 交叉引用）。
