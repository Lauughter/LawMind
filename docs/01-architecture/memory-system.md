# 跨会话记忆系统

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现（归并定时任务 🚧）
> 事实源：`（旧文档《记忆系统详解》已分解合并入本文）`、源码 `D:\develop\Code\LawMind\src\main\java\com\lhs\lawmind\agent\memory\`、`agent\tool\RetrieveMemoryTool.java`、`D:\develop\Code\LawMind\src\main\resources\application.yml`（lawmind.memory）。持久层遵循全局分层：`AiMemory` 实体在 `entity/`、`AiMemoryMapper` 在 `mapper/`、XML 在 `resources/mapper/AiMemoryMapper.xml`。
> 关联：`overview.md`、`agent-orchestration.md`（AgentRunner 两处注入点）、`security-safety.md`（PII 排除）

---

## 一、模块范围

解决「上次的合同/偏好/纠正，下次还知道」的**跨会话记忆**问题。区别于单次请求内的上下文（Context）：记忆存 MySQL + Redis 向量，跨天/永久，按需注入 System Prompt。

**不做什么**：不存储合同原文、身份证/手机号/银行账号、AI 推理中间过程、无价值寒暄、知识库已有法条原文（仅存引用指针）。

| 子模块 | 关键类 | 涉及表/索引 | 说明 |
|--------|--------|------------|------|
| 记忆模型 | `AiMemory`（`entity/`）、`MemoryType` | MySQL `ai_memory` | 四类型统一表 |
| 存储 | `MemoryStore` | MySQL + Redis `idx:memory` | CRUD + 向量化 |
| 检索 | `MemoryRetriever` | — | 两级混合检索 |
| 提取 | `MemoryExtractor` | — | LLM 提取 + 冲突检测 |
| 管理 | `MemoryManager` | — | 统一入口 + 异步提取 |
| 工具 | `RetrieveMemoryTool` | — | Agent 按需取 body |

---

## 二、核心业务流程

```
用户请求
  │
  ├─ [检索] MemoryManager.retrieveAndFormat(userId, question)
  │     ├─ Level1 索引层：SELECT 按 importance 降序 top30 → ~200 token 标题索引（始终注入）
  │     ├─ Level2 语义层：问题向量化 → Redis 相似度 ≥0.7 → top3 body（按需注入，~600 token）
  │     └─ 总预算 ~800 token
  │     ↓
  │   注入 System Prompt 末尾（用户画像/近期事项/历史反馈/相关参考）
  │
  ├─ [推理] Agent 可调 retrieveMemory(memoryId) 获取详情（访问计数+1、更新 last_accessed_at）
  │
  └─ [提取] 会话成功后 @Async → MemoryManager.extractAsync()
        ├─ enforceCapacity(上限 200，超限删 importance 最低)
        ├─ MemoryExtractor.extract()：LLM 四类型提取 + 排除规则 + JSON 输出
        ├─ detectAndResolveConflicts()：标题字符重叠 >50% → 合并（confidence+0.1、溯源并集、丢弃新记忆）
        └─ MemoryStore.save()：写 MySQL + embed → Redis 向量索引

[🚧 规划] 每日归并任务（MemoryConsolidator）
        └─ 类型差异化衰减 + 向量相似度>0.85 合并 + importance<0.2 清理
```

---

## 三、业务规则

### 3.1 四类型记忆模型

| 类型 | 枚举 | 含义 | 示例 | 衰减周期 |
|------|------|------|------|:---:|
| 用户画像 | `USER` | 身份、知识水平、长期偏好 | "HR 经理，偏好表格呈现" | 180 天 |
| 历史反馈 | `FEEDBACK` | 纠正、补充、偏好声明 | "试用期上限按《劳动合同法》第19条" | 90 天 |
| 近期事项 | `PROJECT` | 咨询结论、案件进展 | "用户咨询了劳动纠纷赔偿问题" | 30 天 |
| 相关参考 | `REFERENCE` | 法条/案例引用指针 | "(2023)京01民终1234号" | 60 天 |

统一存储于 `ai_memory`（`type` ENUM 区分），索引 `idx_user_type`、`idx_user_importance`、`idx_user_decay`、`idx_origin_session`。向量存 Redis `idx:memory`（`memory:vector:{id}`，1536 维，COSINE，FLAT）。

### 3.2 两级检索与 Token 预算

| 层级 | 预算 | 内容 | 触发 |
|------|:---:|------|------|
| 索引层 | ~200 token | 全部重要记忆标题行（top30，★ 标 importance≥0.8） | 每次请求自动 |
| 语义详情层 | ~600 token | 语义匹配记忆的 body/summary（top3，相似度 ≥0.7） | 自动语义匹配 / Agent 主动调用 / 用户显式引用 |
| **总计** | **~800 token** | | |

设计动机：记忆增长到 100+ 条时全量注入 body 会超 3000 token；两级检索将"知道有什么"与"知道详细内容"分离。

### 3.3 LLM 提取 Prompt 排除规则

1. 合同原文、具体金额、身份证号、手机号、银行账号（PII）
2. 知识库已有的法条原文（仅记录法条名+条款号作 REFERENCE）
3. AI 推理过程和技术细节
4. 问候语、感谢语等无信息量对话
5. 无跨会话复用价值的内容

> 原则："宁可漏提，不要多提——错误的记忆比没有记忆更糟糕"。

### 3.4 冲突检测（简化实现）

- 提取同 `user_id + type` 的已有记忆，比较**标题字符重叠占比 > 50%** → 判定冲突。
- 冲突处理：已有记忆 `confidence +0.1`、合并 `source_session_ids`、**丢弃新记忆**（保留溯源）。
- 反馈记忆闭环：用户纠正 → 提取为 FEEDBACK → 下次相关话题自动注入 → 行为改变。

### 3.5 容量与衰减

- 每用户上限 `max-per-user`(200)，提取前 `enforceCapacity` 删除 importance 最低者。
- 衰减触发天数：PROJECT 30 / REFERENCE 60 / FEEDBACK 90 / USER 180。
- 🚧 归并定时（`consolidation` 配置已就绪：cron `0 0 3 * * ?`、merge-threshold 0.85、min-importance 0.2），`MemoryStore` 已提供 `findForDecay`/`updateImportance` 与 mapper 支持，**但当前无调度器实际触发**，类型衰减/归并/清理未在线上运行。

### 3.6 隐私与合规

- 按 `user_id` 分区隔离，检索/删除限定单用户。
- 提供 `GET /api/memory/list`、`DELETE /api/memory/{id}`、`DELETE /api/memory/clear`。
- 删除同步清除 MySQL 记录与 Redis 向量索引。

---

## 四、关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 四类型模型替代情节/语义二分 | 按使用语义（谁的信息/什么用途）分类 | 使用语义决定检索策略、衰减周期、注入方式与用户可见性 |
| 统一表 + type 枚举 | 1 张 `ai_memory` 替代三表 | 跨类型查询/归并/迁移/ORM 均简化 |
| 两级检索 | 索引层 + 语义层分离 | 注入成本不随记忆数量线性增长 |
| 类型差异化衰减 | 项目 30/参考 60/反馈 90/用户 180 天 | 案件有时效、用户画像相对稳定，衰减强度分层 |
| LLM 提取 + 排除规则 | JSON 结构化输出 + 明确"不提取"清单 | 保证记忆质量与隐私，防噪音膨胀 |
| 与压缩系统互补 | 请求前记忆注入 / 请求中 ContextCompressor / 请求后异步提取 | 记忆是"所有历史积累了什么"，压缩是"本次学到什么" |

---

## 五、实现进度

| 项 | 状态 |
|----|:---:|
| `ai_memory` 统一表 + `AiMemory`/`MemoryType` | ✅ |
| `MemoryStore`（MySQL CRUD + Redis 向量 + 索引初始化） | ✅ |
| `MemoryRetriever` 两级检索 + 格式化注入 | ✅ |
| `MemoryExtractor` LLM 提取 + 冲突检测 | ✅ |
| `MemoryManager` 统一入口 + `@Async` 异步提取 | ✅ |
| `RetrieveMemoryTool` Agent 工具 | ✅ |
| AgentRunner 两处注入点（前置注入 + 成功提取） | ✅ |
| 容量控制（200/用户） | ✅ |
| 记忆查看/删除 API | ✅ |
| 记忆归并定时任务（类型衰减/合并/清理） | 🚧（配置与存储方法就绪，调度器未实现） |
