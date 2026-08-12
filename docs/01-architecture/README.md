# 01 · 架构层（Architecture）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现

本层描述 LawMind 的系统架构与各模块设计。**入口先读 [overview.md](overview.md)**，再按模块深入。

## 文档清单

| 文档 | 说明 | 状态 |
|------|------|:---:|
| [overview.md](overview.md) | 系统架构总览：主链路、三通道分流、模块地图、关键决策 | ✅ |
| [rag-retrieval.md](rag-retrieval.md) | 多级 RAG 检索管道：混合召回 / RRF / Rerank / MMR / 双阈值 | ✅ |
| [agent-orchestration.md](agent-orchestration.md) | ReAct Agent + 四级意图门控 + 上下文压缩 + 快慢分流 | ✅ |
| [memory-system.md](memory-system.md) | 跨会话记忆：四类型 / 两级检索 / 衰减 | ✅（衰减调度 🚧） |
| [evaluation.md](evaluation.md) | 质量评估体系：Golden Dataset / RAGAS / 回归门禁 | ✅ |
| [security-safety.md](security-safety.md) | 法律安全：五层守卫 / 引用验证 / 专属分块 / 审计 | ✅ |
| [observability.md](observability.md) | 可观测性：指标采集 / AOP 日志 / RAG 日报 | ✅（部分 🚧） |

## 使用指引

- **建立全貌** → 读 overview.md。
- **涉及某模块** → 读对应设计文档 + `../03-api/modules/` 对应接口 + `../02-db/tables/` 相关表文档，三者配套。
- **查实现进度** → 各文档「实现进度」表 + 状态标记（🚧 = 规划中）。

> 本层文档的引用关系登记在 [00-conventions/reference-map.md](../00-conventions/reference-map.md)。
