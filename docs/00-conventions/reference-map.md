# 文档中央引用图（Reference Map）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 生效
> **单一事实来源：所有文档的引用关系只在本文件登记一处。** 用法见 [doc-writing.md](doc-writing.md) 第 4 章。

---

## 如何使用

- **查上游**：看某文档所在行的「引用（上游）」列 = 它依赖谁。
- **查下游（关键）**：修改文档 X 时，grep 下方所有表的「引用（上游）」列，凡是包含 X 的行 = X 的下游，必须检查是否需同步。
- **新增文档**：按 [doc-writing.md](doc-writing.md) 4.4 流程在此登记。
- **删除文档**：同步删除本文件对应行 + 根 README 索引行 + 所有引用它的文档中的链接。

---

## 一、00-conventions（规范层）

| 文档 | 主题 | 状态 | 引用（上游） |
|------|------|:---:|------------|
| README.md | 规范层索引 | ✅ | development-standards.md, doc-writing.md, reference-map.md |
| development-standards.md | 开发规范与评审标准 | ✅ | ../README.md |
| doc-writing.md | 文档编写规范 | ✅ | reference-map.md, ../README.md |
| reference-map.md | 中央引用图 | ✅ | 全部文档（登记） |

## 二、01-architecture（设计层）

| 文档 | 主题 | 状态 | 引用（上游） |
|------|------|:---:|------------|
| README.md | 设计层索引 | ✅ | overview.md, rag-retrieval.md, agent-orchestration.md, memory-system.md, evaluation.md, security-safety.md, observability.md |
| overview.md | 系统架构总览 | ✅ | 全部设计文档；../03-api/README.md；../02-db/README.md |
| rag-retrieval.md | 多级 RAG 检索管道 | ✅ | overview.md；源码 RagConfig/RagServiceImpl（事实源）；../02-db/tables/law_knowledge.md、knowledge_chunk.md；../03-api/modules/chat.md、knowledge.md |
| agent-orchestration.md | ReAct Agent + 意图门控 + 压缩 | ✅ | overview.md；../02-db/tables/ai_chat.md；../03-api/modules/chat.md |
| memory-system.md | 跨会话记忆系统 | ✅ | overview.md；../02-db/tables/ai_memory.md；../03-api/modules/memory.md |
| evaluation.md | 质量评估体系 | ✅ | overview.md；../02-db/tables/evaluation_report.md、review_log.md；../03-api/modules/ops.md |
| security-safety.md | 法律安全守卫 | ✅ | overview.md；../02-db/tables/security_audit_log.md |
| observability.md | 可观测性 | ✅ | overview.md；../02-db/tables/rag_metrics_daily.md；../03-api/modules/ops.md |

## 三、02-db（数据库层）

| 文档 | 主题 | 状态 | 引用（上游） |
|------|------|:---:|------------|
| README.md | 数据库层索引 + 表清单 | ✅ | conventions.md, data-dictionary.md, tables/* |
| conventions.md | 建表约定 | ✅ | init_schema.sql（事实源） |
| data-dictionary.md | 完整数据字典 | ✅ | init_schema.sql（事实源） |
| tables/user.md | 用户表 | ✅ | data-dictionary.md, conventions.md；../03-api/modules/auth.md |
| tables/conversation.md | 会话表 | ✅ | data-dictionary.md, conventions.md；../03-api/modules/chat.md |
| tables/ai_chat.md | 聊天记录表 | ✅ | data-dictionary.md, conventions.md；../03-api/modules/chat.md |
| tables/law_knowledge.md | 法律知识库表 | ✅ | data-dictionary.md, conventions.md；../01-architecture/rag-retrieval.md；../03-api/modules/knowledge.md |
| tables/knowledge_chunk.md | 知识块表 | ✅ | data-dictionary.md, conventions.md；../01-architecture/rag-retrieval.md；../03-api/modules/knowledge.md |
| tables/law_vector_task.md | 向量任务表 | ✅ | data-dictionary.md, conventions.md；../03-api/modules/knowledge.md |
| tables/law_file_upload.md | 文件上传表 | ✅ | data-dictionary.md, conventions.md；../03-api/modules/knowledge.md |
| tables/sys_config.md | 系统配置表 | ✅ | data-dictionary.md, conventions.md；../03-api/modules/system.md |
| tables/security_audit_log.md | 安全审计日志表 | ✅ | data-dictionary.md, conventions.md；../01-architecture/security-safety.md |
| tables/review_log.md | 反馈审核日志表 | ✅ | data-dictionary.md, conventions.md；../01-architecture/evaluation.md |
| tables/rag_metrics_daily.md | RAG 指标日报表 | ✅ | data-dictionary.md, conventions.md；../01-architecture/observability.md；../03-api/modules/ops.md |
| tables/evaluation_report.md | 评估报告表 | ✅ | data-dictionary.md, conventions.md；../01-architecture/evaluation.md；../03-api/modules/ops.md |
| tables/ai_memory.md | 统一记忆表 | ✅ | data-dictionary.md, conventions.md；../01-architecture/memory-system.md；../03-api/modules/memory.md |

## 四、03-api（接口层）

| 文档 | 主题 | 状态 | 引用（上游） |
|------|------|:---:|------------|
| README.md | 接口层索引 | ✅ | common.md, modules/* |
| common.md | 通用约定（响应/错误码/鉴权/SSE/分页） | ✅ | 源码 Result/PageResult/GlobalExceptionHandler/JwtInterceptor（事实源） |
| modules/auth.md | 认证与用户接口 | ✅ | common.md；../01-architecture/security-safety.md；../02-db/tables/user.md |
| modules/chat.md | 对话 / AI 问答 / Agent 接口 | ✅ | common.md；../01-architecture/agent-orchestration.md；../02-db/tables/ai_chat.md、conversation.md |
| modules/knowledge.md | 知识库 / 文件 / 向量化接口 | ✅ | common.md；../01-architecture/rag-retrieval.md；../02-db/tables/law_knowledge.md、knowledge_chunk.md、law_vector_task.md、law_file_upload.md |
| modules/memory.md | 记忆系统接口 | ✅ | common.md；../01-architecture/memory-system.md；../02-db/tables/ai_memory.md |
| modules/system.md | 系统配置 / 文件 / 自动学习接口 | ✅ | common.md；../02-db/tables/sys_config.md |
| modules/ops.md | RAG 指标 / Redis 信息接口 | ✅ | common.md；../01-architecture/observability.md、evaluation.md；../02-db/tables/rag_metrics_daily.md |

## 五、04-environments（环境层）

| 文档 | 主题 | 状态 | 引用（上游） |
|------|------|:---:|------------|
| README.md | 环境层索引 + 差异对比 | ✅ | development.md, production.md |
| development.md | 本地开发环境 | ✅ | application*.yml（事实源）；../05-infrastructure/redis.md、dashscope.md |
| production.md | 生产配置 | ✅ | application-prod.yml（事实源）；../05-infrastructure/redis.md、dashscope.md |

## 六、05-infrastructure（基础设施层）

| 文档 | 主题 | 状态 | 引用（上游） |
|------|------|:---:|------------|
| README.md | 基础设施层索引 | ✅ | redis.md, dashscope.md |
| redis.md | Redis Stack / 向量索引 | ✅ | 源码 RedisConfig/RedisIndexInitializer（事实源）；../01-architecture/rag-retrieval.md |
| dashscope.md | 阿里百炼 DashScope | ✅ | application.yml langchain4j.*（事实源）；../01-architecture/rag-retrieval.md |

## 七、06-reference（参考层）

| 文档 | 主题 | 状态 | 引用（上游） |
|------|------|:---:|------------|
| README.md | 参考层索引 | ✅ | 全部参考文档 |
| agent-transformation-roadmap.md | Agent 转型路线图 | 📝 | —（历史规划，独立） |
| agent-transformation-plan.md | Agent 转型实施计划 | 📝 | agent-transformation-roadmap.md |
| agent-transformation-testset.md | Agent 转型对比测试集 | 📝 | agent-transformation-roadmap.md |
| rag-gap-analysis.md | 企业级 RAG 差距分析 | 📝 | —（历史，独立；优化项已并入 01-architecture 🚧） |
| project-tech-details.md | 项目技术详解 | 📝 | —（历史，内容已分解进 01-architecture） |
| resume-copy.md | 个人简历文案 | 📝 | —（个人，可删除） |
| agent-optimization-plan.md | Agent 子系统优化方案 | 🚧 | agent-orchestration.md、rag-retrieval.md、observability.md |
