# ai_chat（聊天记录表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：聊天模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：存储每一次问答交互的原始问题、AI 回答、检索命中知识、Token/成本计量与用户反馈，是 RAG 链路核心流水表。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，聊天记录 ID |
| user_id | BIGINT | 否 | — | 用户 ID |
| conversation_id | BIGINT | 是 | NULL | 会话 ID（可空，单轮未入会话） |
| user_question | TEXT | 否 | — | 用户问题 |
| ai_answer | TEXT | 是 | NULL | AI 回答 |
| knowledge_match | JSON | 是 | NULL | 匹配的知识数据（JSON） |
| token_usage_input | INT | 是 | NULL | 输入 Token 数 |
| token_usage_output | INT | 是 | NULL | 输出 Token 数 |
| estimated_cost | DECIMAL(10,6) | 是 | NULL | 预估成本（元） |
| feedback | INT | 是 | NULL | 用户反馈（见枚举） |
| feedback_content | VARCHAR(500) | 是 | NULL | 反馈文字说明 |
| feedback_status | VARCHAR(20) | 是 | NULL | 审核状态（见枚举） |
| reviewed_by | BIGINT | 是 | NULL | 审核人 ID（关联 user.id） |
| reviewed_at | DATETIME | 是 | NULL | 审核时间 |
| review_notes | VARCHAR(500) | 是 | NULL | 审核备注 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_user_id | 普通 | user_id | 按用户查聊天记录 |
| idx_conversation_id | 普通 | conversation_id | 按会话拉取一轮对话 |
| idx_feedback_status | 普通 | feedback_status | 审核队列筛选（待审） |
| idx_create_time | 普通 | create_time DESC | 时间倒序分页浏览 |

## 枚举

`feedback`（INT）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| NULL | — | 未反馈 | 用户未点赞/点踩 |
| 1 | LIKE | 赞 | 用户点「有帮助」 |
| -1 | DISLIKE | 踩 | 用户点「没帮助」 |

`feedback_status`（VARCHAR(20)）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| NULL | — | 未进入审核 | 默认（未反馈或不需审核） |
| PENDING_REVIEW | PENDING_REVIEW | 待审核 | 用户点踩后进入审核队列 |
| REVIEWED | REVIEWED | 已审核 | 审核人处理完成，回填 reviewed_by/reviewed_at |
| DISMISSED | DISMISSED | 已驳回 | 审核判定无需整改 |

## 业务规则

- **状态流转（反馈审核）**：`NULL → PENDING_REVIEW → REVIEWED | DISMISSED`；点踩触发 PENDING_REVIEW，审核通过置 REVIEWED 并回填审核人/时间/备注，驳回置 DISMISSED。
- **计量**：`token_usage_input/output` 与 `estimated_cost`（DECIMAL(10,6)）由 LLM 调用响应回填，供成本核算与 rag_metrics_daily 聚合。
- **关系**：conversation_id → conversation.id（可空）；user_id → user.id；reviewed_by → user.id；review_log.chat_id → ai_chat.id。
- **无软删除**：流水数据物理保留，历史全量可审计。
- **用途**：RAG 问答流水 + 反馈闭环 + 成本计量 + 人工审核依据。
