# law_knowledge（法律知识库表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：知识库模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：法律知识库主表，存储法律文档标题、条文内容、生效/失效信息、向量化状态与来源，是 RAG 检索的知识根。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，知识条目 ID |
| user_id | BIGINT | 是 | NULL | 上传用户 ID |
| law_type | VARCHAR(64) | 是 | NULL | 法律类型：民法/刑法/行政法等 |
| title | VARCHAR(500) | 否 | — | 法律文档标题 |
| chapter | VARCHAR(100) | 是 | NULL | 章 |
| section | VARCHAR(100) | 是 | NULL | 节（保留字，SQL 反引号转义） |
| article_number | INT | 是 | NULL | 条文号 |
| content | TEXT | 否 | — | 条文内容 |
| vector_status | INT | 是 | 0 | 向量化状态（见枚举） |
| effective_date | DATE | 是 | NULL | 生效日期 |
| expiry_date | DATE | 是 | NULL | 失效日期（NULL=无失效） |
| status | VARCHAR(32) | 是 | 'EFFECTIVE' | 法律状态（见枚举） |
| source | VARCHAR(64) | 是 | 'BATCH_IMPORT' | 来源（见枚举） |
| publisher | VARCHAR(200) | 是 | NULL | 发布机构 |
| publish_date | DATE | 是 | NULL | 发布日期 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| is_deleted | INT | 是 | 0 | 软删除标记：0-正常 1-已删除 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_law_type | 普通 | law_type | 按法律类型过滤 |
| idx_vector_status | 普通 | vector_status | 向量化待办/失败扫描 |
| idx_status | 普通 | status | 按法律状态筛选（EFFECTIVE/REPEALED） |
| idx_is_deleted | 普通 | is_deleted | 软删除过滤 |
| ft_title_content | 全文（ngram） | (title, content) | 中文全文检索标题与条文 |

## 枚举

`vector_status`（INT）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| 0 | NOT_VECTORIZED | 未生成 | 默认，入库后待向量化 |
| 1 | VECTORIZED | 已生成 | 分块向量化全部完成 |
| 2 | FAILED | 失败 | 向量化失败（配合 knowledge_chunk.error_msg） |

`status`（VARCHAR(32)）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| EFFECTIVE | EFFECTIVE | 现行有效 | 默认 |
| REPEALED | REPEALED | 已废止 | 法律被废止（expiry_date 可对应） |
| DRAFT | DRAFT | 草稿 | 录入中的草案，不参与检索 |

`source`（VARCHAR(64)）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| BATCH_IMPORT | BATCH_IMPORT | 批量导入 | 默认，批量入库 |
| MANUAL | MANUAL | 人工录入 | 手工新增 |
| AUTO_LEARN | AUTO_LEARN | 自动学习 | 系统自动抓取/学习 |

## 业务规则

- **检索过滤**：参与 RAG 检索须满足 `status='EFFECTIVE'` 且 `is_deleted=0`；REPEALED/DRAFT 不参与。
- **分块链路**：1 对多 → knowledge_chunk（按块序号拆分），每块独立向量化；law_vector_task 记录整体向量任务状态。
- **全文检索**：`ft_title_content` 使用 ngram 解析器，支持中文标题/条文全文检索（MySQL 侧 BM25）。
- **时间有效性**：`effective_date`/`expiry_date` 标注条文生效期，`expiry_date=NULL` 表示无失效。
- **软删除**：置 `is_deleted=1` 逻辑删除，分块与向量任务不自动级联清理。
- **用途**：RAG 知识检索根表，支撑法条命中与引用溯源。
