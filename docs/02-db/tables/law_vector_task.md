# law_vector_task（向量任务表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：知识库模块（向量化）
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：跟踪法律知识条目向量化任务的执行状态，含失败原因、重试次数与 Redis 检索同步状态，是异步向量化的任务队列台账。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，任务 ID |
| knowledge_id | BIGINT | 否 | — | 关联知识 ID |
| vector_status | INT | 是 | 0 | 向量状态（见枚举） |
| redis_search_sync | INT | 是 | 0 | Redis 同步状态（见枚举） |
| error_msg | VARCHAR(500) | 是 | NULL | 失败原因 |
| retry_count | INT | 是 | 0 | 重试次数 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_knowledge_id | 普通 | knowledge_id | 按知识条目查任务 |
| idx_vector_status | 普通 | vector_status | 待处理/失败任务批量调度 |

## 枚举

`vector_status`（INT）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| 0 | PENDING | 待处理 | 默认，任务入队 |
| 1 | COMPLETED | 已完成 | 向量化完成 |
| 2 | FAILED | 失败 | 向量化异常，error_msg 记录原因 |

`redis_search_sync`（INT）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| 0 | NOT_SYNCED | 未同步 | 默认，向量完成但未同步 |
| 1 | SYNCED | 已同步 | 向量已同步至 Redis 检索索引 |

## 业务规则

- **状态流转**：`vector_status: 0 → 1`（完成）或 `0 → 2`（失败，可重试，retry_count 累加）；完成后 `redis_search_sync: 0 → 1` 将检索数据同步到 Redis。
- **关系**：`knowledge_id` → law_knowledge.id（一个知识条目对应一条/多条向量任务）；与 knowledge_chunk 按 knowledge_id 关联，但任务粒度按条目而非分块。
- **重试**：失败任务由调度器按 `retry_count` 与 `idx_vector_status` 重扫重跑，超过阈值告警/人工介入。
- **用途**：异步向量化 + Redis 检索同步的进度与失败台账。
