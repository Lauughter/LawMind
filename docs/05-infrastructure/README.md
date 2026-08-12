# 05 · 基础设施层（Infrastructure）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现

本层描述 LawMind 依赖的外部服务与基础设施（Redis / LLM 平台）。

## 文档清单

| 文档 | 说明 | 状态 |
|------|------|:---:|
| [redis.md](redis.md) | Redis Stack 7.4：连接 / 向量索引（idx:law_knowledge）/ Key 前缀 | ✅ |
| [dashscope.md](dashscope.md) | 阿里百炼 DashScope：qwen-plus / text-embedding-v2 / qwen3-rerank / API Key | ✅ |

## 依赖清单

| 服务 | 版本/规格 | 用途 | 事实源 |
|------|-----------|------|--------|
| Redis Stack | 7.4（RediSearch + FLOAT32 向量） | 向量检索 / 缓存 / 会话 | [redis.md](redis.md) |
| 阿里百炼 DashScope | qwen-plus / text-embedding-v2（1536 维）/ qwen3-rerank | LLM 对话 / 向量 / 精排 | [dashscope.md](dashscope.md) |
| MySQL | 8.0 | 主存储 | ../02-db/README.md |

## 使用指引

- **排查外部服务** → 读对应 infra 文档；配置变更按 [00-conventions/doc-writing.md](../00-conventions/doc-writing.md) 影响矩阵同步 environments。

> ⚠️ 安全：`application.yml` 中 DashScope API Key 与 JWT secret 当前硬编码（prod 未覆盖），生产必须改环境变量并轮换。
> 本层文档的引用关系登记在 [00-conventions/reference-map.md](../00-conventions/reference-map.md)。
