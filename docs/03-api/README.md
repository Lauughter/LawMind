# 03 · 接口层（API）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`src/main/java/com/lhs/lawmind/controller/`（Controller 源码注解为准）

本层描述 REST 接口契约。接口契约以 Controller 源码为准，本文档是契约摘要。全局 context-path 为 `/api`。

## 文档清单

| 文档 | 说明 |
|------|------|
| [common.md](common.md) | 通用约定：Result/PageResult / 错误码 / JWT / 管理员 / AOP / SSE / 分页 |

## 模块清单

| 模块 | 覆盖 Controller | 文档 |
|------|----------------|------|
| auth（认证与用户） | UserController | [modules/auth.md](modules/auth.md) |
| chat（对话 / AI / Agent） | AiChatController、ConversationController、AgentController | [modules/chat.md](modules/chat.md) |
| knowledge（知识库 / 文件 / 向量化 / Redis 索引） | LawKnowledgeController、LawFileUploadController、LawVectorTaskController、RedisIndexManagementController | [modules/knowledge.md](modules/knowledge.md) |
| memory（记忆系统） | MemoryController | [modules/memory.md](modules/memory.md) |
| system（系统配置 / 文件 / 自动学习） | SysConfigController、FileController、AutoLearningController | [modules/system.md](modules/system.md) |
| ops（RAG 指标 / Redis 信息） | RagMetricsController、RedisInfoController | [modules/ops.md](modules/ops.md) |

## 使用指引

- **对接口** → 先读 common.md 了解统一约定，再进对应模块文档。
- **改接口** → 按 [00-conventions/doc-writing.md](../00-conventions/doc-writing.md) 变更影响矩阵同步：模块接口文档 + common.md（若涉通用约定）+ 相关设计/表文档。

> ⚠️ 已知风险：多个 Controller 的 `@RequestMapping` 自带 `/api` 前缀，叠加全局 context-path 后实际路径为双前缀（如 `/api/api/memory/*`），详见各模块文档标注。
> 本层文档的引用关系登记在 [00-conventions/reference-map.md](../00-conventions/reference-map.md)。
