# 06 · 参考层（Reference）

> 版本：V1.0 | 日期：2026-08-12 | 状态：📝 历史归档

本层存放历史规划资料、差距分析、测试集等**参考性文档**。这些文档不代表当前实现状态，仅作追溯；当前事实以 01-architecture / 02-db / 03-api 分层文档为准。

## 文档清单

| 文档 | 说明 | 状态 |
|------|------|:---:|
| [agent-transformation-roadmap.md](agent-transformation-roadmap.md) | Agent 转型路线图（规划，部分已落地） | 📝 |
| [agent-transformation-plan.md](agent-transformation-plan.md) | Agent 转型实施计划与进度追踪 | 📝 |
| [agent-transformation-testset.md](agent-transformation-testset.md) | Agent 转型对比测试集 | 📝 |
| [rag-gap-analysis.md](rag-gap-analysis.md) | 企业级 RAG 差距分析与优化路线图 | 📝 |
| [project-tech-details.md](project-tech-details.md) | 项目技术详解（面试备用，内容已分解进 01-architecture） | 📝 |
| [resume-copy.md](resume-copy.md) | 个人简历文案（非项目文档，可按需删除） | 📝 |
| [agent-optimization-plan.md](agent-optimization-plan.md) | **Agent 子系统优化方案（工作文档，逐项实施中）** | 🚧 |

## 关联资产

| 资产 | 位置 | 说明 |
|------|------|------|
| Golden Dataset | `docs/golden-dataset-rag-evaluation.json` | 质量评估标注集，**被代码与 CI 硬引用（勿移动）** |

## 使用指引

- **了解演进历史 / 规划背景** → 读本层文档。
- **查当前实现** → 以 01-architecture 等分层文档为准，本层内容如与当前实现冲突，**源码优先**。

> 本层文档的引用关系登记在 [00-conventions/reference-map.md](../00-conventions/reference-map.md)。
