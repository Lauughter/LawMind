# knowledge_chunk（知识块表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：知识库模块（分块）
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：法律知识分块后的片段，每块独立向量化并供检索，是 RAG 实际命中的最小知识单元。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，知识块 ID |
| knowledge_id | BIGINT | 否 | — | 所属法律知识 ID |
| chunk_index | INT | 否 | — | 块序号（从 0 开始） |
| context_prefix | VARCHAR(500) | 是 | NULL | 上下文前缀，帮助定位法律出处 |
| content | TEXT | 否 | — | 块内容 |
| vector_status | INT | 是 | 0 | 向量化状态（见枚举） |
| error_msg | VARCHAR(500) | 是 | NULL | 向量化失败原因 |
| retry_count | INT | 是 | 0 | 重试次数 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_knowledge_id | 普通 | knowledge_id | 按知识条目取全部分块 |
| idx_vector_status | 普通 | vector_status | 未向量化/失败分块批量处理 |

## 枚举

`vector_status`（INT）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| 0 | NOT_VECTORIZED | 未生成 | 默认，分块已建待向量化 |
| 1 | VECTORIZED | 已生成 | 块向量写入向量库完成 |
| 2 | FAILED | 失败 | 向量化异常，error_msg 记录原因 |

## 业务规则

- **归属**：`knowledge_id` → law_knowledge.id（逻辑关联，无物理外键）；一个知识条目拆为多块，`chunk_index` 从 0 递增保证顺序。
- **状态流转**：`0 → 1`（成功）或 `0 → 2`（失败，记 error_msg）；失败块可重试，`retry_count` 累加。
- **父状态联动**：全部块完成后，law_knowledge.vector_status 置 1；任一块失败可置 2。
- **检索**：向量检索命中块后，通过 `context_prefix` + 父条目标题/条文号回拼出处（引用溯源）。
- **用途**：RAG 检索命中载体与引用溯源的最小粒度。
