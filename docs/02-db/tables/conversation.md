# conversation（会话表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：聊天模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：组织用户的对话会话（一次多轮法律咨询），为 ai_chat 记录分组提供标题与时间轴，含软删除。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，会话 ID |
| user_id | BIGINT | 否 | — | 用户 ID（所属用户） |
| title | VARCHAR(200) | 是 | '新对话' | 会话标题（默认「新对话」，可由首问生成） |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| is_deleted | INT | 是 | 0 | 软删除标记：0-正常 1-已删除 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_user_id | 普通 | user_id | 按用户列出会话 |
| idx_update_time | 普通 | (user_id, update_time DESC) | 按用户按更新时间倒序展示会话列表 |

## 枚举

`is_deleted`（INT）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| 0 | NOT_DELETED | 正常 | 默认 |
| 1 | DELETED | 已删除（软删） | 用户删除会话时置 1，历史聊天保留 |

## 业务规则

- **归属**：每个会话属于一个 `user_id`；无外键约束，应用层保证。
- **软删除**：会话删除仅置 `is_deleted=1`，其下 ai_chat 记录不级联删除，由业务决定展示过滤。
- **关系**：1 对多 → ai_chat.conversation_id；ai_chat.conversation_id 可空（单轮未入会话）。
- **列表查询**：依赖 `idx_user_id` + `idx_update_time(user_id, update_time DESC)` 高效取出某用户最近会话。
- **用途**：对话历史分组容器，标题默认「新对话」，可基于首条问题自动命名。
