# 02 · 数据库层（Database）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`src/main/resources/sql/init_schema.sql`（唯一表结构权威，共 13 张表）

本层描述 MySQL 8.0 数据库结构。数据库结构以 `init_schema.sql` 为准，本文档是契约摘要。

## 文档清单

| 文档 | 说明 |
|------|------|
| [conventions.md](conventions.md) | 建表约定：命名 / 通用字段 / 索引 / JSON / ENUM |
| [data-dictionary.md](data-dictionary.md) | 完整数据字典（14 表 176 字段全量） |

## 表清单

| 表 | 说明 | 所属模块 | 文档 |
|----|------|---------|------|
| `user` | 用户表 | 用户认证 | [tables/user.md](tables/user.md) |
| `conversation` | 会话表 | 聊天 | [tables/conversation.md](tables/conversation.md) |
| `ai_chat` | 聊天记录表 | 聊天 | [tables/ai_chat.md](tables/ai_chat.md) |
| `law_knowledge` | 法律知识库表 | 知识库 | [tables/law_knowledge.md](tables/law_knowledge.md) |
| `knowledge_chunk` | 知识块表（分块） | 知识库 | [tables/knowledge_chunk.md](tables/knowledge_chunk.md) |
| `law_vector_task` | 向量任务表 | 知识库 | [tables/law_vector_task.md](tables/law_vector_task.md) |
| `law_file_upload` | 文件上传表 | 文件 | [tables/law_file_upload.md](tables/law_file_upload.md) |
| `sys_config` | 系统配置表 | 系统配置 | [tables/sys_config.md](tables/sys_config.md) |
| `security_audit_log` | 安全审计日志表 | 安全审计 | [tables/security_audit_log.md](tables/security_audit_log.md) |
| `review_log` | 反馈审核日志表 | 反馈审核 | [tables/review_log.md](tables/review_log.md) |
| `rag_metrics_daily` | RAG 指标日报表 | 可观测性 | [tables/rag_metrics_daily.md](tables/rag_metrics_daily.md) |
| `evaluation_report` | 评估报告表 | 质量评估 | [tables/evaluation_report.md](tables/evaluation_report.md) |
| `ai_memory` | 统一记忆表 | 记忆系统 | [tables/ai_memory.md](tables/ai_memory.md) |

## 使用指引

- **查字段** → 先 data-dictionary.md 总览，再进单表文档。
- **改表结构** → 按 [00-conventions/doc-writing.md](../00-conventions/doc-writing.md) 变更影响矩阵同步：表文档 + data-dictionary + 本清单 + 反向推导下游。
- **注意**：全库无物理外键，仅逻辑关联；`ai_memory.embedding` 用 JSON 存向量。

> 本层文档的引用关系登记在 [00-conventions/reference-map.md](../00-conventions/reference-map.md)。
