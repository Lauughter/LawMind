# LawMind 数据字典（Data Dictionary）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联文档：conventions.md、02-db/tables/*.md

**用途**：LawMind 数据库 14 张核心表的全字段权威说明。字段以 SQL COMMENT 为准，中文表述。含 176 个字段，逐字段列出，无省略。

- 引擎/字符集：全部 InnoDB + utf8mb4 / utf8mb4_unicode_ci
- 命名差异提示：`create_time/update_time` 与 `created_at/updated_at` 混用，`law_file_upload` 使用 `upload_time` 代替 `create_time`，详见 conventions.md

## 目录

| # | 表 | 模块 | 行数 |
|---|----|------|------|
| 1 | [user](#1-user用户表) | 用户认证模块 | 10 |
| 2 | [conversation](#2-conversation会话表) | 聊天模块 | 6 |
| 3 | [ai_chat](#3-ai_chat聊天记录表) | 聊天模块 | 16 |
| 4 | [law_knowledge](#4-law_knowledge法律知识库表) | 知识库模块 | 18 |
| 5 | [knowledge_chunk](#5-knowledge_chunk知识块表) | 知识库模块（分块） | 10 |
| 6 | [law_vector_task](#6-law_vector_task向量任务表) | 知识库模块（向量化） | 8 |
| 7 | [law_file_upload](#7-law_file_upload文件上传表) | 文件上传/文档解析模块 | 14 |
| 8 | [sys_config](#8-sys_config系统配置表) | 系统配置模块 | 6 |
| 9 | [security_audit_log](#9-security_audit_log安全审计日志表) | 安全审计模块 | 14 |
| 10 | [review_log](#10-review_log反馈审核日志表) | 反馈审核模块 | 9 |
| 11 | [rag_metrics_daily](#11-rag_metrics_dailyrag指标日报表) | 可观测性/评估模块 | 23 |
| 12 | [evaluation_report](#12-evaluation_report评估报告表) | 质量评估模块 | 16 |
| 13 | [ai_memory](#13-ai_memory统一记忆表) | 记忆系统模块 | 15 |

---

## 1. user（用户表）

> 关联模块：用户认证模块 | 详见 [tables/user.md](tables/user.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，用户 ID |
| username | VARCHAR(64) | 否 | — | 用户名（唯一） |
| password | VARCHAR(256) | 否 | — | 加密密码（bcrypt 哈希） |
| nickname | VARCHAR(64) | 是 | NULL | 昵称 |
| phone | VARCHAR(20) | 是 | NULL | 手机号 |
| role | VARCHAR(32) | 是 | 'user' | 角色：admin/user |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| last_login_time | DATETIME | 是 | NULL | 最后登录时间 |
| is_deleted | INT | 是 | 0 | 软删除标记：0-正常 1-已删除 |

## 2. conversation（会话表）

> 关联模块：聊天模块 | 详见 [tables/conversation.md](tables/conversation.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，会话 ID |
| user_id | BIGINT | 否 | — | 用户 ID（所属用户） |
| title | VARCHAR(200) | 是 | '新对话' | 会话标题 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| is_deleted | INT | 是 | 0 | 软删除标记：0-正常 1-已删除 |

## 3. ai_chat（聊天记录表）

> 关联模块：聊天模块 | 详见 [tables/ai_chat.md](tables/ai_chat.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，聊天记录 ID |
| user_id | BIGINT | 否 | — | 用户 ID |
| conversation_id | BIGINT | 是 | NULL | 会话 ID（可空，即单轮未入会话） |
| user_question | TEXT | 否 | — | 用户问题 |
| ai_answer | TEXT | 是 | NULL | AI 回答 |
| knowledge_match | JSON | 是 | NULL | 匹配的知识数据（JSON） |
| token_usage_input | INT | 是 | NULL | 输入 Token 数 |
| token_usage_output | INT | 是 | NULL | 输出 Token 数 |
| estimated_cost | DECIMAL(10,6) | 是 | NULL | 预估成本（元） |
| feedback | INT | 是 | NULL | 用户反馈：1=赞 -1=踩 |
| feedback_content | VARCHAR(500) | 是 | NULL | 反馈文字说明 |
| feedback_status | VARCHAR(20) | 是 | NULL | 审核状态：PENDING_REVIEW/REVIEWED/DISMISSED |
| reviewed_by | BIGINT | 是 | NULL | 审核人 ID |
| reviewed_at | DATETIME | 是 | NULL | 审核时间 |
| review_notes | VARCHAR(500) | 是 | NULL | 审核备注 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

## 4. law_knowledge（法律知识库表）

> 关联模块：知识库模块 | 详见 [tables/law_knowledge.md](tables/law_knowledge.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，知识条目 ID |
| user_id | BIGINT | 是 | NULL | 上传用户 ID |
| law_type | VARCHAR(64) | 是 | NULL | 法律类型：民法/刑法/行政法等 |
| title | VARCHAR(500) | 否 | — | 法律文档标题 |
| chapter | VARCHAR(100) | 是 | NULL | 章 |
| section | VARCHAR(100) | 是 | NULL | 节（保留字，SQL 中反引号转义） |
| article_number | INT | 是 | NULL | 条文号 |
| content | TEXT | 否 | — | 条文内容 |
| vector_status | INT | 是 | 0 | 向量化状态：0-未生成 1-已生成 2-失败 |
| effective_date | DATE | 是 | NULL | 生效日期 |
| expiry_date | DATE | 是 | NULL | 失效日期（NULL=无失效） |
| status | VARCHAR(32) | 是 | 'EFFECTIVE' | 法律状态：EFFECTIVE/REPEALED/DRAFT |
| source | VARCHAR(64) | 是 | 'BATCH_IMPORT' | 来源：BATCH_IMPORT/MANUAL/AUTO_LEARN |
| publisher | VARCHAR(200) | 是 | NULL | 发布机构 |
| publish_date | DATE | 是 | NULL | 发布日期 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| is_deleted | INT | 是 | 0 | 软删除标记：0-正常 1-已删除 |

## 5. knowledge_chunk（知识块表）

> 关联模块：知识库模块（分块） | 详见 [tables/knowledge_chunk.md](tables/knowledge_chunk.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，知识块 ID |
| knowledge_id | BIGINT | 否 | — | 所属法律知识 ID |
| chunk_index | INT | 否 | — | 块序号（从 0 开始） |
| context_prefix | VARCHAR(500) | 是 | NULL | 上下文前缀，帮助定位法律出处 |
| content | TEXT | 否 | — | 块内容 |
| vector_status | INT | 是 | 0 | 向量化状态：0-未生成 1-已生成 2-失败 |
| error_msg | VARCHAR(500) | 是 | NULL | 向量化失败原因 |
| retry_count | INT | 是 | 0 | 重试次数 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

## 6. law_vector_task（向量任务表）

> 关联模块：知识库模块（向量化） | 详见 [tables/law_vector_task.md](tables/law_vector_task.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，任务 ID |
| knowledge_id | BIGINT | 否 | — | 关联知识 ID |
| vector_status | INT | 是 | 0 | 向量状态：0-待处理 1-已完成 2-失败 |
| redis_search_sync | INT | 是 | 0 | Redis 同步状态：0-未同步 1-已同步 |
| error_msg | VARCHAR(500) | 是 | NULL | 失败原因 |
| retry_count | INT | 是 | 0 | 重试次数 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

## 7. law_file_upload（文件上传表）

> 关联模块：文件上传/文档解析模块 | 详见 [tables/law_file_upload.md](tables/law_file_upload.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，上传记录 ID |
| user_id | BIGINT | 否 | — | 上传用户 ID |
| knowledge_id | BIGINT | 是 | NULL | 关联知识 ID（解析入库后回填） |
| processing_status | VARCHAR(32) | 是 | 'PENDING' | 处理状态：PENDING/PROCESSING/COMPLETED/FAILED |
| file_name | VARCHAR(500) | 否 | — | 原始文件名 |
| file_type | VARCHAR(32) | 是 | NULL | 文件类型：pdf/docx/txt |
| file_size | BIGINT | 是 | NULL | 文件大小（字节） |
| file_path | VARCHAR(1000) | 是 | NULL | 存储路径 |
| content | LONGTEXT | 是 | NULL | 解析后的文本内容 |
| ai_review_result | TEXT | 是 | NULL | AI 审查结果 |
| ai_revised_content | TEXT | 是 | NULL | AI 修订后内容 |
| risk_level | VARCHAR(20) | 是 | NULL | 风险等级：LOW/MEDIUM/HIGH/CRITICAL |
| upload_time | DATETIME | 是 | CURRENT_TIMESTAMP | 上传时间 |
| is_deleted | INT | 是 | 0 | 软删除标记：0-正常 1-已删除 |

## 8. sys_config（系统配置表）

> 关联模块：系统配置模块 | 详见 [tables/sys_config.md](tables/sys_config.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，配置记录 ID |
| config_key | VARCHAR(200) | 否 | — | 配置键（唯一） |
| config_value | TEXT | 是 | NULL | 配置值 |
| description | VARCHAR(500) | 是 | NULL | 配置说明 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

## 9. security_audit_log（安全审计日志表）

> 关联模块：安全审计模块 | 详见 [tables/security_audit_log.md](tables/security_audit_log.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，审计日志 ID |
| user_id | BIGINT | 是 | NULL | 用户 ID |
| operation_type | VARCHAR(64) | 否 | — | 操作类型 |
| description | VARCHAR(500) | 是 | NULL | 操作描述 |
| resource_type | VARCHAR(64) | 是 | NULL | 资源类型 |
| resource_id | BIGINT | 是 | NULL | 资源 ID |
| request_method | VARCHAR(10) | 是 | NULL | 请求方法：GET/POST/PUT/DELETE |
| request_uri | VARCHAR(500) | 是 | NULL | 请求 URI |
| request_params | TEXT | 是 | NULL | 请求参数（JSON 文本） |
| client_ip | VARCHAR(64) | 是 | NULL | 客户端 IP |
| request_id | VARCHAR(64) | 是 | NULL | 请求追踪 ID |
| result | VARCHAR(20) | 是 | NULL | 操作结果：SUCCESS/FAIL |
| error_message | TEXT | 是 | NULL | 错误信息 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

## 10. review_log（反馈审核日志表）

> 关联模块：反馈审核模块 | 详见 [tables/review_log.md](tables/review_log.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，审核日志 ID |
| chat_id | BIGINT | 否 | — | 聊天记录 ID（来源 ai_chat） |
| question | TEXT | 是 | NULL | 关联问题（冗余快照） |
| action_type | VARCHAR(32) | 是 | NULL | 操作类型 |
| action_detail | VARCHAR(500) | 是 | NULL | 操作详情 |
| feedback_reason | VARCHAR(500) | 是 | NULL | 反馈原因 |
| processed | INT | 是 | 0 | 处理状态：0-未处理 1-已处理 |
| processed_at | DATETIME | 是 | NULL | 处理时间 |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

## 11. rag_metrics_daily（RAG 指标日报表）

> 关联模块：可观测性/评估模块 | 详见 [tables/rag_metrics_daily.md](tables/rag_metrics_daily.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键 |
| metric_date | DATE | 否 | — | 统计日期（唯一，每日一条） |
| total_requests | BIGINT | 是 | 0 | 总请求数 |
| knowledge_hits | BIGINT | 是 | 0 | 知识库命中数 |
| llm_direct_count | BIGINT | 是 | 0 | LLM 直接回答数 |
| non_legal_count | BIGINT | 是 | 0 | 非法律问题拦截数 |
| gate_reject_count | BIGINT | 是 | 0 | 意图门控拒绝数 |
| gate_degraded_count | BIGINT | 是 | 0 | 意图门控异常降级数 |
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

## 13. evaluation_report（评估报告表）

> 关联模块：质量评估模块 | 详见 [tables/evaluation_report.md](tables/evaluation_report.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，报告 ID |
| run_id | VARCHAR(64) | 否 | — | 评估运行 ID |
| dataset_path | VARCHAR(500) | 是 | NULL | 数据集路径 |
| dataset_version | INT | 是 | NULL | 数据集版本 |
| total_cases | INT | 是 | 0 | 总用例数 |
| passed_cases | INT | 是 | 0 | 通过用例数 |
| failed_cases | INT | 是 | 0 | 失败用例数 |
| avg_keyword_recall | DECIMAL(5,4) | 是 | NULL | 平均关键词召回率 |
| avg_source_match | DECIMAL(5,4) | 是 | NULL | 平均来源匹配率 |
| avg_law_type_match | DECIMAL(5,4) | 是 | NULL | 平均法律类型匹配率 |
| avg_answer_length | DECIMAL(10,2) | 是 | NULL | 平均回答长度 |
| avg_total_score | DECIMAL(5,4) | 是 | NULL | 平均总分 |
| avg_faithfulness | DECIMAL(5,4) | 是 | NULL | 平均忠实度 |
| avg_answer_relevance | DECIMAL(5,4) | 是 | NULL | 平均回答相关性 |
| report_json | JSON | 是 | NULL | 完整报告 JSON |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

## 14. ai_memory（统一记忆表）

> 关联模块：记忆系统模块 | 详见 [tables/ai_memory.md](tables/ai_memory.md)

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，记忆 ID |
| user_id | BIGINT | 否 | — | 用户 ID |
| type | ENUM('USER','FEEDBACK','PROJECT','REFERENCE') | 否 | — | 记忆类型（四类型区分） |
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

---

## 统计

- 表数：14
- 字段总数：176
- 含软删除（is_deleted）：user、conversation、law_knowledge、law_file_upload（4 张）
- JSON 类型字段：ai_chat.knowledge_match、evaluation_report.report_json、ai_memory.source_session_ids、ai_memory.embedding（4 个）；另 security_audit_log.request_params 为 TEXT 存储 JSON
- 真 ENUM 字段：仅 ai_memory.type；其余枚举均为 VARCHAR/INT 约束

> 字段级完整说明见各表 [tables/*.md](tables/)。建表通用约定见 [conventions.md](conventions.md)。
