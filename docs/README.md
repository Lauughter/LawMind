# LawMind — 项目文档

> 版本：V1.0 | 日期：2026-08-12 | 本文件是 docs 目录的**唯一入口**
>
> **读者对象：人类开发者 + AI。** 本目录是一套面向 AI 的、可维护的项目文档体系，采用结构化密集格式，优先保证字段级事实的准确与完整。

---

## 一、项目简介

LawMind 是一款面向中国法律垂直场景的智能问答平台，核心链路为「意图门控 → 三通道分流（Fast/Hybrid/Agent）→ 多级 RAG 检索管道 → LLM 生成 → 安全后处理」。

| 项 | 值 |
|----|----|
| 技术栈 | Spring Boot 3.5 + Java 17 + LangChain4j 0.36 + Redis Stack 7.4 + MySQL 8 + MyBatis + Vue 3 |
| 文档体系 | 面向 AI 的结构化文档（共 51 份文档，7 层） |

## 二、模块地图

| 模块 | 状态 | 设计文档 | 接口文档 | 数据 |
|------|:---:|---------|---------|------|
| 意图门控 + Agent 编排 | ✅ | [01-architecture/agent-orchestration.md](01-architecture/agent-orchestration.md) | [03-api/modules/chat.md](03-api/modules/chat.md) | ai_chat |
| 多级 RAG 检索 | ✅ | [01-architecture/rag-retrieval.md](01-architecture/rag-retrieval.md) | [03-api/modules/chat.md](03-api/modules/chat.md) | law_knowledge 等 |
| 上下文压缩 | ✅ | [01-architecture/agent-orchestration.md](01-architecture/agent-orchestration.md) | — | — |
| 跨会话记忆 | ✅（衰减 🚧） | [01-architecture/memory-system.md](01-architecture/memory-system.md) | [03-api/modules/memory.md](03-api/modules/memory.md) | ai_memory |
| 质量评估 | ✅ | [01-architecture/evaluation.md](01-architecture/evaluation.md) | [03-api/modules/ops.md](03-api/modules/ops.md) | evaluation_report |
| 法律安全 | ✅ | [01-architecture/security-safety.md](01-architecture/security-safety.md) | — | security_audit_log |
| 可观测性 | 🚧 部分 | [01-architecture/observability.md](01-architecture/observability.md) | [03-api/modules/ops.md](03-api/modules/ops.md) | rag_metrics_daily |
| 用户 / 会话 | ✅ | — | [03-api/modules/auth.md](03-api/modules/auth.md) | user, conversation |
| 知识库管理 | ✅ | — | [03-api/modules/knowledge.md](03-api/modules/knowledge.md) | law_knowledge 等 |

## 三、文档体系导航

### 层级总览

```
docs/
├── README.md                        ← 你在这里（入口 + 索引）
├── 00-conventions/                  ← 规范层：怎么开发、怎么写文档、怎么评审
├── 01-architecture/                 ← 设计层：系统架构 + 各模块设计
├── 02-db/                           ← 数据库层：数据字典 + 每表一文档
├── 03-api/                          ← 接口层：通用约定 + 每模块接口
├── 04-environments/                 ← 环境层：开发 / 生产
├── 05-infrastructure/               ← 基础设施层：Redis / DashScope
└── 06-reference/                    ← 参考层：历史规划 / 差距分析 / 测试集
```

### 按场景快速入口

| 我要…… | 去这里 |
|--------|--------|
| 了解项目架构 | [01-architecture/README.md](01-architecture/README.md) |
| 了解开发规范 | [00-conventions/README.md](00-conventions/README.md) |
| 按规范写/改文档 | [00-conventions/doc-writing.md](00-conventions/doc-writing.md) |
| 改文档先查下游引用 | [00-conventions/reference-map.md](00-conventions/reference-map.md) |
| 查数据库结构 | [02-db/README.md](02-db/README.md) |
| 对接接口 | [03-api/README.md](03-api/README.md) |
| 搭本地环境 | [04-environments/development.md](04-environments/development.md) |
| 排查 Redis/LLM | [05-infrastructure/README.md](05-infrastructure/README.md) |
| 看历史规划/差距分析 | [06-reference/README.md](06-reference/README.md) |

### 完整索引

| 文档 | 说明 |
|------|------|
| **[00-conventions/](00-conventions/README.md)** | 规范层 |
| [development-standards.md](00-conventions/development-standards.md) | 开发规范与评审标准 |
| [doc-writing.md](00-conventions/doc-writing.md) | 文档编写规范 |
| [reference-map.md](00-conventions/reference-map.md) | 文档中央引用图 |
| **[01-architecture/](01-architecture/README.md)** | 设计层 |
| [overview.md](01-architecture/overview.md) | 系统架构总览 |
| [rag-retrieval.md](01-architecture/rag-retrieval.md) | 多级 RAG 检索管道 |
| [agent-orchestration.md](01-architecture/agent-orchestration.md) | ReAct Agent + 意图门控 + 压缩 |
| [memory-system.md](01-architecture/memory-system.md) | 跨会话记忆系统 |
| [evaluation.md](01-architecture/evaluation.md) | 质量评估体系 |
| [security-safety.md](01-architecture/security-safety.md) | 法律安全守卫 |
| [observability.md](01-architecture/observability.md) | 可观测性 |
| **[02-db/](02-db/README.md)** | 数据库层（13 表） |
| [conventions.md](02-db/conventions.md) | 建表约定 |
| [data-dictionary.md](02-db/data-dictionary.md) | 完整数据字典 |
| [tables/user.md](02-db/tables/user.md) 等 13 份 | 每表一文档（见 [02-db/README.md](02-db/README.md) 表清单） |
| **[03-api/](03-api/README.md)** | 接口层（6 模块） |
| [common.md](03-api/common.md) | 通用约定 |
| [modules/auth.md](03-api/modules/auth.md) 等 6 份 | 每模块接口（见 [03-api/README.md](03-api/README.md) 模块清单） |
| **[04-environments/](04-environments/README.md)** | 环境层 |
| [development.md](04-environments/development.md) | 本地开发环境 |
| [production.md](04-environments/production.md) | 生产配置 |
| **[05-infrastructure/](05-infrastructure/README.md)** | 基础设施层 |
| [redis.md](05-infrastructure/redis.md) | Redis Stack / 向量索引 |
| [dashscope.md](05-infrastructure/dashscope.md) | 阿里百炼 DashScope |
| **[06-reference/](06-reference/README.md)** | 参考层 |
| [agent-transformation-roadmap.md](06-reference/agent-transformation-roadmap.md) | Agent 转型路线图 |
| [agent-transformation-plan.md](06-reference/agent-transformation-plan.md) | Agent 转型实施计划 |
| [agent-transformation-testset.md](06-reference/agent-transformation-testset.md) | Agent 转型测试集 |
| [rag-gap-analysis.md](06-reference/rag-gap-analysis.md) | 企业级 RAG 差距分析 |
| [project-tech-details.md](06-reference/project-tech-details.md) | 项目技术详解 |
| [resume-copy.md](06-reference/resume-copy.md) | 简历文案（可删） |
| **资产** | |
| [golden-dataset-rag-evaluation.json](golden-dataset-rag-evaluation.json) | 质量评估标注集（被代码/CI 硬引用，勿移动） |

## 四、给 AI 的使用指引

1. **先读** [00-conventions/development-standards.md](00-conventions/development-standards.md) 的「总体原则」与「AI 使用指南」。
2. **再读** [01-architecture/overview.md](01-architecture/overview.md) 建立系统全貌。
3. **任务涉及某模块** → 读该模块设计 + 接口 + 相关表文档，三者配套。
4. **写代码前**对照评审清单自审；**写文档时**遵守 [00-conventions/doc-writing.md](00-conventions/doc-writing.md)。
5. **改文档**先用 [reference-map.md](00-conventions/reference-map.md) 查下游。

> ⚠️ **一致性原则**：接口/数据以源码为准（Controller 注解、`init_schema.sql`、`application*.yml`），本文档是契约摘要。发现不一致时源码优先，并更新本文档。

## 五、文档维护约定

- 结构、命名、编写格式见 [00-conventions/doc-writing.md](00-conventions/doc-writing.md)。
- 引用关系见 [00-conventions/reference-map.md](00-conventions/reference-map.md)。
- **代码变更必须同步文档**（同一 PR）；新增文档必须在本索引 + 所属层 README + reference-map 三处登记。
- 状态标记：✅ 已实现 / 🚧 规划中 / 📝 草稿 / 📌 历史归档。
