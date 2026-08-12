# ai_memory（统一记忆表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：记忆系统模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：LawMind 跨会话记忆的唯一存储，采用四类型记忆模型（USER/FEEDBACK/PROJECT/REFERENCE），记录用户偏好、反馈、项目背景与知识引用，支持向量检索与生命周期管理。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，记忆 ID |
| user_id | BIGINT | 否 | — | 用户 ID |
| type | ENUM('USER','FEEDBACK','PROJECT','REFERENCE') | 否 | — | 记忆类型（见枚举） |
| title | VARCHAR(100) | 是 | NULL | 简短标题（用于列表展示和一级检索） |
| body | TEXT | 否 | — | 记忆正文（一句话事实或简短摘要） |
| summary | VARCHAR(300) | 是 | NULL | 摘要（从 body 提炼，用于注入时节省 token） |
| origin_session_id | BIGINT | 是 | NULL | 产生此记忆的会话 ID |
| source_session_ids | JSON | 是 | NULL | 支撑此记忆的所有会话 ID |
| confidence | DOUBLE | 是 | 0.5 | 置信度 0-1（多证据来源则升高） |
| importance | DOUBLE | 是 | 0.5 | 重要性 0-10 归一化到 0-1 |
| access_count | INT | 是 | 0 | 被检索命中次数 |
| embedding | JSON | 是 | NULL | 1536 维向量（JSON 数组） |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| updated_at | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| last_accessed_at | DATETIME | 是 | CURRENT_TIMESTAMP | 最后访问时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_user_type | 普通 | (user_id, type) | 按用户+类型筛选记忆 |
| idx_user_importance | 普通 | (user_id, importance DESC) | 按用户按重要性取高价值记忆 |
| idx_user_decay | 普通 | (user_id, last_accessed_at, type) | 衰减退化扫描（长期未访问） |
| idx_origin_session | 普通 | origin_session_id | 按来源会话反查记忆 |

## 枚举

`type`（ENUM，全库唯一原生枚举）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| USER | MEMORY_USER | 用户画像记忆 | 用户自述偏好、身份、个人信息等 |
| FEEDBACK | MEMORY_FEEDBACK | 反馈记忆 | 用户对回答的点赞/点踩/纠错等反馈结论 |
| PROJECT | MEMORY_PROJECT | 项目背景记忆 | 与用户当前项目/事务相关的事实 |
| REFERENCE | MEMORY_REFERENCE | 知识引用记忆 | 被用户认可/标注的法律知识点引用 |

## 业务规则

- **写入**：会话中抽取事实（一句话事实 `body`）→ 提炼 `summary`（注入时节省 token）→ 打向量 `embedding`（1536 维 JSON 数组）→ 记录 `origin_session_id` 与 `source_session_ids`。
- **评分**：`confidence`（0-1，多证据来源则升高）、`importance`（0-10 归一化到 0-1），检索排序综合两者。
- **检索/更新**：命中时 `access_count` +1、刷新 `last_accessed_at`；新证据并入 `source_session_ids` 并提升 `confidence`。
- **生命周期**：`idx_user_decay` 扫描长期未访问记忆做衰减退化（可降 importance 或清理），防止记忆池膨胀。
- **关系**：`user_id` → user.id；`origin_session_id`/`source_session_ids` 引用 conversation.id（逻辑关联，无物理外键）。
- **用途**：跨会话个性化，为 RAG 问答注入用户画像与偏好，提升回答相关性（与 ai_chat 联动）。
