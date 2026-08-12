# CLAUDE.md — 项目记忆（每次会话自动加载）

本文件在每次会话启动时自动加载进 AI 上下文。**首次接手本项目请先读 [docs/README.md](docs/README.md) 建立全貌。**

## 项目

LawMind（LawMind）— 面向中国法律垂直场景的智能问答平台，从多级 RAG 混合检索到多步 Agent 推理的完整链路。技术栈：Spring Boot 3.5 + Java 17 + LangChain4j 0.36 + Redis Stack 7.4（向量存储）+ MySQL 8.0 + MyBatis + Vue 3。
已实现模块：多级 RAG 检索管道、ReAct 多工具 Agent、四级意图门控、上下文压缩、跨会话记忆、质量评估体系、可观测性。
规划模块：详见各设计文档 🚧 标记。
完整项目简介与文档索引：[docs/README.md](docs/README.md)

## 强制规则（必须遵守）

1. **开发前先读规范**：动手写代码前，阅读 [docs/00-conventions/development-standards.md](docs/00-conventions/development-standards.md) 的「总体原则」与「AI 使用指南」章节。
2. **改代码必同步文档**：任何新增/修改接口、表结构、业务规则的代码变更，必须在同一 PR 内同步更新 [docs/](docs/) 对应文档。**不能只改主文档**——改完必须用 [docs/00-conventions/reference-map.md](docs/00-conventions/reference-map.md) 反向推导"谁引用了它"并逐个同步。完整规则见 [docs/00-conventions/doc-writing.md](docs/00-conventions/doc-writing.md)。
3. **事实源优先级**：接口以 Controller 源码注解为准、数据库以 `src/main/resources/sql/init_schema.sql` 为准、配置以 `application*.yml` 为准、向量/Redis 以源码 RedisIndexInitializer 为准。发现文档与源码不一致时，**源码优先**，并同步更新文档。
4. **新文档登记索引**：新增文档必须在 [docs/README.md](docs/README.md) 完整索引中登记一行，并在所属层 README 中登记。

## 关键入口

| 需求 | 文档 |
|------|------|
| 开发规范 / 评审清单 | [docs/00-conventions/development-standards.md](docs/00-conventions/development-standards.md) |
| 文档编写规范 | [docs/00-conventions/doc-writing.md](docs/00-conventions/doc-writing.md) |
| 引用图（改文档先查下游） | [docs/00-conventions/reference-map.md](docs/00-conventions/reference-map.md) |
| 数据库表结构 | [docs/02-db/README.md](docs/02-db/README.md) |
| 接口契约 | [docs/03-api/README.md](docs/03-api/README.md) |
| 本地开发环境 | [docs/04-environments/development.md](docs/04-environments/development.md) |
| 架构总览 | [docs/01-architecture/README.md](docs/01-architecture/README.md) |
