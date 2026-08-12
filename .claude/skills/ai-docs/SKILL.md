---
name: ai-docs
description: 为任意项目搭建/维护一套面向 AI 的文档体系（docs 分层结构 + CLAUDE.md + 中央引用图 + 文档编写规范）。用于新项目初始化 AI 文档（bootstrap，默认）或已有项目文档维护（maintain）。显式命令：/ai-docs。含 bootstrap / maintain 两种模式。
---

# AI 文档体系（ai-docs）

> 目的：让不同开发者 / 不同 AI 接手项目时，通过一套**结构化、可维护、可迁移**的文档快速建立认知，并在开发中保持文档与代码一致。

## 两种模式

| 模式 | 用途 | 触发 |
|------|------|------|
| **bootstrap** | 为项目搭建 AI 文档骨架（默认） | `/ai-docs` 或 `/ai-docs bootstrap` |
| **maintain** | 维护既有文档体系的一致性 | `/ai-docs maintain` |

---

## bootstrap 流程

### 步骤 1：收集配置（向用户确认）

1. 项目名称 / 一句话简介
2. 项目类型：后端 API / 前端 / BFF / 全栈 / 其他
3. 技术栈（语言 + 框架 + 关键组件）
4. 是否有数据库？类型？→ 决定 `02-db` 层
5. 是否有对外接口？→ 决定 `03-api` 层
6. 是否有外部服务 / 基础设施（Redis / 云存储 / 第三方 API）？→ 决定 `05-infrastructure` 层
7. 是否有部署环境区分（dev/test/prod）？→ 决定 `04-environments` 层
8. 是否有 PRD / 历史资料需迁移？→ 决定 `06-reference` 层
9. 已有文档是否需迁入新结构？

### 步骤 2：确定层结构（可裁剪）

默认 7 层，按配置裁剪：

```
docs/
├── README.md                 # 唯一入口：项目简介 + 完整索引
├── 00-conventions/           # [必选] 开发/文档规范、引用图
├── 01-architecture/          # [必选] 架构总览 + 按模块设计
├── 02-db/                    # [有数据库] 总览 + 约定 + 每表一文档
├── 03-api/                   # [有接口] 总览 + 通用约定 + 每模块接口
├── 04-environments/          # [有部署] 开发/测试/生产
├── 05-infrastructure/        # [有外部服务] Redis/云存储/第三方
└── 06-reference/             # [有资料] PRD/手册/测试计划
```

裁剪规则：**00 与 01 必选**；02-06 按步骤 1 的答案增删。生成时不创建多余层。

### 步骤 3：生成文件

用 `templates/` 下的模板生成（AI 替换 `{{占位符}}`）：

| 生成物 | 模板 |
|--------|------|
| `CLAUDE.md`（项目根） | `templates/CLAUDE.md.tmpl` |
| `docs/README.md` | `templates/docs-README.md.tmpl` |
| `docs/00-conventions/doc-writing.md` | `templates/doc-writing.md.tmpl` |
| `docs/00-conventions/reference-map.md` | `templates/reference-map.md.tmpl` |
| 各层 `README.md` | `templates/layer-README.md.tmpl` |
| 表文档 / 接口文档 / 设计文档（按需） | `templates/table-doc.md.tmpl` 等 |

若用户提供表 / 接口 / 模块清单 → 用对应模板**批量生成骨架**，再逐个填事实。

### 步骤 4：初始化引用图

- 为每个已生成的文档在 `reference-map.md` 登记一行（引用上游）。
- 建立「根 README 索引 ↔ 各层 README ↔ 各文档」的登记关系。

### 步骤 5：交付清单

- [ ] `CLAUDE.md` 已生成且 4 条强制规则完整
- [ ] `docs/README.md` 索引齐全
- [ ] 每份文档有元数据头（版本 / 日期 / 状态 / 事实源）
- [ ] `reference-map.md` 已登记全部文档
- [ ] 层结构与项目类型匹配（无多余 / 缺失层）

---

## maintain 流程（轻量）

维护已有体系，不重搭：

1. 检查项目 `CLAUDE.md` 与 `docs/` 是否存在 → 不存在则建议先 bootstrap。
2. 定位变更：用 `docs/00-conventions/reference-map.md` 反向推导"谁引用了被改文档"。
3. 执行变更影响矩阵（见 [REFERENCE.md](REFERENCE.md)）：按变更类型核对受影响文档清单。
4. 新增文档 → 套用 `templates/` 对应模板 + 四步登记（引用图 / 根索引 / 层索引 / 反向推导补链）。
5. 校验：元数据头 / 索引登记 / 引用图登记 / 链接有效。

---

## 通用规则集

完整可移植规则（文档编写总原则、引用图机制、类型模板、变更影响矩阵、新增文档流程、新功能模块全流程）见 [REFERENCE.md](REFERENCE.md)。

## 迁移到其他项目

本 skill 为**纯 Markdown（无脚本）**，迁移 = 复制整个 `ai-docs/` 目录到目标项目的 `.claude/skills/`（项目级）或 `~/.claude/skills/`（用户级，全局可用）。复制后即可用 `/ai-docs` 调用。
