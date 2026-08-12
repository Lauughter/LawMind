# review_log（反馈审核日志表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：反馈审核模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：记录用户反馈（点踩）后的人工/系统处理动作，关联聊天记录，跟踪审核处理状态。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，审核日志 ID |
| chat_id | BIGINT | 否 | — | 聊天记录 ID（来源 ai_chat） |
| question | TEXT | 是 | NULL | 关联问题（冗余快照） |
| action_type | VARCHAR(32) | 是 | NULL | 操作类型 |
| action_detail | VARCHAR(500) | 是 | NULL | 操作详情 |
| feedback_reason | VARCHAR(500) | 是 | NULL | 反馈原因 |
| processed | INT | 是 | 0 | 处理状态（见枚举） |
| processed_at | DATETIME | 是 | NULL | 处理时间 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_processed | 普通 | processed | 待处理审核队列扫描 |
| idx_chat_id | 普通 | chat_id | 按聊天记录反查审核记录 |

## 枚举

`processed`（INT）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| 0 | UNPROCESSED | 未处理 | 默认，反馈进入待审 |
| 1 | PROCESSED | 已处理 | 审核动作完成，回填 processed_at |

## 业务规则

- **触发**：用户对 ai_chat 反馈点踩（feedback=-1）时，创建审核记录并同步 ai_chat.feedback_status=PENDING_REVIEW。
- **处理流转**：`processed: 0 → 1`；处理时回填 `action_type`/`action_detail`/`feedback_reason`/`processed_at`，并联动 ai_chat.feedback_status → REVIEWED/DISMISSED。
- **冗余快照**：`question` 从 ai_chat 冗余复制，审核时以 ai_chat.user_question 为准。
- **关系**：`chat_id` → ai_chat.id（逻辑关联，无物理外键）。
- **用途**：反馈闭环的审核台账，驱动回答质量改进与 rag_metrics_daily 反馈指标。
