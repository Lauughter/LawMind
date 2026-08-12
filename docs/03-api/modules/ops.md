# 接口文档 — ops（RAG 指标 / Redis 信息）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：controller/RagMetricsController.java、RedisInfoController.java | 关联：01-architecture 可观测性与监控设计、01-architecture/evaluation.md

---

## 一、接口清单

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/admin/metrics/today` | 管理员 | RAG 今日概览 |
| GET | `/api/admin/metrics/trend` | 管理员 | 最近 N 天趋势 |
| GET | `/api/admin/metrics/quality/today` | 管理员 | 质量指标今日概览 |
| GET | `/api/admin/metrics/quality/trend` | 管理员 | 质量指标趋势 |
| GET | `/api/admin/metrics/eval/reports` | 管理员 | 评估报告历史列表 |
| POST | `/api/admin/metrics/persist/{dateStr}` | 管理员 | 持久化指定日期指标到 MySQL |
| POST | `/api/admin/metrics/persist/catchup` | 管理员 | 补漏持久化最近 N 天缺失指标 |
| GET | `/api/api/redis/search-version` | 需登录 | Redis Search 模块版本 |
| GET | `/api/api/redis/info` | 需登录 | Redis 服务器信息 |

> `/api/api/redis/*` 的双 `/api` 前缀为实际映射结果（见 common.md 第八节）。

---

# 二、RagMetricsController（RAG 指标仪表盘）

> 全部接口通过 `RequestContext.isAdmin()`（角色 `role=="admin"`）判定管理员，非管理员返回 `403, "无权访问，仅限管理员"`。

## 2.1 今日概览

### GET /api/admin/metrics/today

- 鉴权：管理员（角色式）
- 成功响应：`R<Map<String,Object>>`（`ragMetricsService.getTodayOverview()`，含缓存命中率、来源占比、延迟分布等）

## 2.2 最近 N 天趋势

### GET /api/admin/metrics/trend

- 鉴权：管理员

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| days | Query | 否 | 7 | 天数，范围 1–90 |

- 成功响应：`R<Map<String,Object>>`
- 错误：`400, "天数范围为 1-90"`

## 2.3 质量指标今日概览

### GET /api/admin/metrics/quality/today

- 鉴权：管理员
- 成功响应：`R<Map<String,Object>>`（各来源点赞率、反馈原因分布、LLM 兜底率）

## 2.4 质量指标趋势

### GET /api/admin/metrics/quality/trend

- 鉴权：管理员

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| days | Query | 否 | 7 | 天数，范围 1–90 |

- 成功响应：`R<Map<String,Object>>`
- 错误：`400, "天数范围为 1-90"`

## 2.5 评估报告历史

### GET /api/admin/metrics/eval/reports

- 鉴权：管理员

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| limit | Query | 否 | 20 | 返回最近 N 条 |

- 成功响应：`R<List<EvalReportRecord>>`

`EvalReportRecord` 关键字段：`id`、`runId`、`datasetPath`、`datasetVersion`、`totalCases`、`passedCases`、`failedCases`、`avgKeywordRecall`、`avgSourceMatch`、`avgLawTypeMatch`、`avgAnswerLength`、`avgTotalScore`、`avgFaithfulness`、`avgAnswerRelevance`、`reportJson`、`createdAt`。

## 2.6 持久化指定日期指标

### POST /api/admin/metrics/persist/{dateStr}

- 鉴权：管理员
- 参数：`dateStr`（Path，`yyyy-MM-dd`）
- 成功响应：`R<Map<String,Object>>`
- 错误：`400, "日期格式错误，请使用 yyyy-MM-dd: ..."`

**业务规则**：手动将 Redis 中指定日期的指标持久化到 MySQL。

## 2.7 补漏持久化

### POST /api/admin/metrics/persist/catchup

- 鉴权：管理员

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| days | Query | 否 | 14 | 回溯天数，范围 1–14（受 Redis TTL 限制） |

- 成功响应：`R<Map<String,Object>>`
- 错误：`400, "回溯天数范围为 1-14（受 Redis TTL 限制）"`

---

# 三、RedisInfoController（Redis 信息）

> 未做管理员校验，任何登录用户可查看。

## 3.1 Redis Search 模块版本

### GET /api/api/redis/search-version

- 鉴权：需登录
- 成功响应：`R<Map<String,Object>>`：`success`、`modules`（MODULE LIST 结果）、`message`；失败时含 `error` 字段

## 3.2 Redis 服务器信息

### GET /api/api/redis/info

- 鉴权：需登录
- 成功响应：`R<Map<String,String>>`：`redis_version`、`redis_mode`、`os`、`arch_bits`、`tcp_port`、`uptime_in_seconds`、`message`；失败时含 `error` 字段

---

> 通用约定（R 响应体 / 错误码 / 鉴权）见 [../common.md](../common.md)。
