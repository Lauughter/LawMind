# LawMind 建表约定（Database Conventions）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联文档：data-dictionary.md、02-db/tables/*.md

**用途**：汇总 LawMind 数据库 13 张表的命名、通用字段、索引、JSON、ENUM 等建表约定。观察结论基于 init_schema.sql 全部 13 张表。

---

## 一、命名约定

### 1.1 表名

- **单数** snake_case，全小写，**不加 `_table` 后缀，也不加复数 s**（对照：`user`、`conversation`、`review_log`，而非 `users`/`user_table`）。
- 按业务域加**语义前缀**分组，见下表：

| 前缀 | 示例 | 分组 |
|------|------|------|
| （无前缀） | `user`、`conversation` | 核心实体 |
| `law_` | `law_knowledge`、`law_vector_task`、`law_file_upload` | 法律知识域 |
| `knowledge_` | `knowledge_chunk` | 知识分块 |
| `ai_` | `ai_chat`、`ai_memory` | AI 能力域 |
| `sys_` | `sys_config` | 系统内部 |
| `security_` | `security_audit_log` | 安全审计 |
| `review_` | `review_log` | 审核反馈 |
| `rag_` | `rag_metrics_daily` | 检索评估/可观测性 |
| `evaluation_` | `evaluation_report` | 质量评估 |

- 保留字处理：`law_knowledge.section` 为保留字，SQL 中用反引号 `\`section\`` 转义；字段命名应避免保留字。

### 1.2 字段名

- 全小写 snake_case，无前缀（`user_id`、`create_time`），不冗余表名（如 ai_chat 内为 `user_id` 而非 `ai_chat_user_id`）。
- 布尔/状态多用 `INT`（0/1 或枚举数字），少用 BIT。
- 外键逻辑字段以 `_id` 结尾：`user_id`、`conversation_id`、`knowledge_id`、`chat_id`、`origin_session_id`。

## 二、通用字段

### 2.1 主键 `id`

全部 13 张表统一：`id BIGINT AUTO_INCREMENT PRIMARY KEY`。命名一致，无表名前缀。

### 2.2 时间戳字段

| 约定 | 字段 | 规则 |
|------|------|------|
| 创建时间 | `create_time` / `created_at` | `DATETIME DEFAULT CURRENT_TIMESTAMP` |
| 更新时间 | `update_time` / `updated_at` | `DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` |

存在**两套命名并存**（观察到的差异，非统一）：

| 命名 | 使用表 |
|------|--------|
| `create_time` + `update_time` | user、conversation、law_knowledge、knowledge_chunk、law_vector_task、sys_config |
| `created_at` + `updated_at` | review_log、rag_metrics_daily、evaluation_report、ai_memory |
| 仅 `create_time`（无更新时间） | ai_chat、security_audit_log |
| 特例 | law_file_upload 用 `upload_time`（无 create/update_time） |

> 建议后续统一为 `created_at`/`updated_at`（迁移时对齐）。

### 2.3 软删除 `is_deleted`

- 约定：`is_deleted INT DEFAULT 0`，`0-正常 1-已删除`。
- **仅 4 张业务表启用**：user、conversation、law_knowledge、law_file_upload。
- 其余表（ai_chat、knowledge_chunk、law_vector_task、sys_config、security_audit_log、review_log、rag_metrics_daily、evaluation_report、ai_memory）**无软删除字段**，物理删除或保留全量历史。

## 三、索引约定

| 类型 | 命名 | 示例 | 说明 |
|------|------|------|------|
| 主键 | 默认 `PRIMARY` | — | `id` |
| 唯一 | `idx_<字段>`（**不用 `uk_` 前缀**） | `idx_username`、`idx_config_key`、`idx_question_md5`、`idx_metric_date` | 唯一约束全部以 `idx_` 开头 |
| 普通 | `idx_<字段>` | `idx_user_id`、`idx_knowledge_id` | 单列或多列 |
| 全文 | `ft_<字段>` | `ft_title_content` | 中文 ngram 解析器 |
| 降序 | 索引字段带 `DESC` | `idx_create_time (create_time DESC)`、`idx_update_time (user_id, update_time DESC)` | 时间排序类索引常用降序 |
| 复合 | `idx_<语义>` | `idx_user_type (user_id, type)`、`idx_status_expire (status, expire_time)`、`idx_user_decay (user_id, last_accessed_at, type)` | 联合索引前缀放等值列、后放排序列 |

- **全文索引**：`law_knowledge` 的 `FULLTEXT INDEX ft_title_content (title, content) WITH PARSER ngram`，用于中文法律标题/条文的全文检索。全库唯一全文索引。
- 索引命名统一小写 `idx_`/`ft_`，与表字段名对齐。

## 四、JSON 字段

| 表.字段 | 类型 | 内容 |
|---------|------|------|
| ai_chat.knowledge_match | JSON | 匹配的知识数据 |
| evaluation_report.report_json | JSON | 完整评估报告 |
| ai_memory.source_session_ids | JSON | 支撑记忆的所有会话 ID |
| ai_memory.embedding | JSON | 1536 维向量（JSON 数组） |
| security_audit_log.request_params | TEXT（存 JSON 文本） | 请求参数（注意为 TEXT 而非 JSON 类型） |

- 除 `request_params` 外均为 MySQL JSON 类型。
- `ai_memory.embedding` 用 JSON 数组存向量，无专用向量类型（MySQL 8 无原生向量列），检索在应用层完成。

## 五、ENUM 字段（枚举/状态码汇总）

| 表.字段 | 类型 | 取值 | 说明 |
|---------|------|------|------|
| ai_memory.type | **ENUM**（唯一真枚举） | USER / FEEDBACK / PROJECT / REFERENCE | 记忆类型 |
| user.role | VARCHAR(32) | admin / user | 角色 |
| ai_chat.feedback | INT | 1=赞 / -1=踩 | 用户反馈 |
| ai_chat.feedback_status | VARCHAR(20) | PENDING_REVIEW / REVIEWED / DISMISSED | 反馈审核状态 |
| law_knowledge.vector_status | INT | 0=未生成 / 1=已生成 / 2=失败 | 向量化状态 |
| law_knowledge.status | VARCHAR(32) | EFFECTIVE / REPEALED / DRAFT | 法律状态 |
| law_knowledge.source | VARCHAR(64) | BATCH_IMPORT / MANUAL / AUTO_LEARN | 数据来源 |
| knowledge_chunk.vector_status | INT | 0=未生成 / 1=已生成 / 2=失败 | 分块向量化状态 |
| law_vector_task.vector_status | INT | 0=待处理 / 1=已完成 / 2=失败 | 向量任务状态 |
| law_vector_task.redis_search_sync | INT | 0=未同步 / 1=已同步 | Redis 检索同步状态 |
| law_file_upload.processing_status | VARCHAR(32) | PENDING / PROCESSING / COMPLETED / FAILED | 文档处理状态 |
| law_file_upload.risk_level | VARCHAR(20) | LOW / MEDIUM / HIGH / CRITICAL | 风险等级 |
| review_log.processed | INT | 0=未处理 / 1=已处理 | 审核处理状态 |
| security_audit_log.request_method | VARCHAR(10) | GET / POST / PUT / DELETE | 请求方法 |
| security_audit_log.result | VARCHAR(20) | SUCCESS / FAIL | 操作结果 |

- 约定：仅 `ai_memory.type` 使用 MySQL 原生 ENUM，其余枚举用 VARCHAR（可扩展）或 INT（数字状态）。
- INT 状态值统一为「0=初始/正常态」起算。

## 六、引擎与字符集

- 引擎：全部 `InnoDB`（支持事务与外键约束）。
- 字符集：`utf8mb4`，排序规则 `utf8mb4_unicode_ci`，库级 `DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci`。
- 外键：schema 中**未声明物理外键**，均为逻辑关联（如 `ai_chat.conversation_id`、`knowledge_chunk.knowledge_id`）。

## 七、观察到的疑点 / 不规范点

1. **默认管理员硬编码**：`INSERT INTO user ... ('admin', '<bcrypt 哈希>', '管理员', 'admin')`，注释明文暴露「密码: 123456」。哈希本身是 bcrypt（`$2b$12$...`），但默认弱口令 + 明文注释属上线风险，建议部署时强制改密。
2. **时间戳命名不统一**：`create_time/update_time` vs `created_at/updated_at` 两套并存，`law_file_upload` 用 `upload_time`。
3. **`security_audit_log.request_params` 用 TEXT 存 JSON**，非 JSON 类型，无校验。
4. **`ai_memory.embedding` JSON 存向量**：无原生向量索引，百万级检索有性能风险。
5. **无物理外键**：全部逻辑关联，靠应用层保证一致性，孤儿数据需在代码中规避。
6. **`review_log` 冗余快照**：`question` 与 ai_chat 冗余，审核时需以 ai_chat 为准。

> 字段级完整说明见 [data-dictionary.md](data-dictionary.md)。各表详细文档见 [tables/](tables/)。
