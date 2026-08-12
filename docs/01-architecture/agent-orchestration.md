# ReAct Agent 与意图门控

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`D:\develop\Code\LawMind\docs\06-reference\project-tech-details.md` §3–§4、源码 `D:\develop\Code\LawMind\src\main\java\com\lhs\lawmind\agent\`（gate/、compress/、tool/、monitor/、AgentRunner.java）、`AgentConfig.java`、`D:\develop\Code\LawMind\src\main\resources\application.yml`（lawmind.agent）
> 关联：`overview.md`、`rag-retrieval.md`、`memory-system.md`

---

## 一、模块范围

本模块负责**意图门控（四级）→ 快慢分流 → ReAct 循环推理 → 上下文压缩 → Agent 监控**。主控器 `IntentGate.process()` 串联四级流水线；`AgentRunner.execute()` 实现多轮工具调用推理，最大 5 轮。

| 子模块 | 关键类 | 说明 |
|--------|--------|------|
| 领域门控 | `DomainGate` | 是否法律问题（规则 + LLM 兜底，fail-open） |
| 意图分类 | `IntentClassifierEnhanced` | 7 种意图，规则 + LLM 兜底 |
| 复杂度/路由 | `ComplexityAssessor` + `IntentRouter` | 四因子加权 → 三通道路由 |
| 快速通道 | `FastChannelHandler` | 单次 LLM，无工具 |
| Agent 循环 | `AgentRunner` | ReAct 循环 + 反射 `@Tool` 注册 |
| 上下文压缩 | `ContextCompressor` + `KnowledgeState` | 4 层渐进压缩 + 知识原子 |
| 监控 | `AgentMetricsCollector` | Token/工具/压缩/门控统计 |

---

## 二、核心业务流程

### 2.1 四级意图门控

```
用户问题
  │
  ▼
Layer1: DomainGate.judge()
  ├─ 空/格式异常/敏感内容 → 拒绝（malformed / sensitive）
  ├─ 规则：法条引用模式《..》/第X条、~26 法律关键词 → legal(0.95)
  ├─ 规则：~12 非法律关键词（天气/游戏/电影…） → 拒绝(0.95)
  ├─ 规则：敏感词（色情/暴力/政治/赌博/毒品/翻墙/VPN） → 拒绝
  └─ LLM 兜底 → legal(0.7) / nonLegal(0.7)；LLM 失败 → 放行 legal(0.5)
  │
  ▼
Layer2: IntentClassifierEnhanced.classify()
  ├─ 规则（6 类关键词按优先级） → intent(0.9)
  └─ LLM 兜底 → intent(0.7)
  │
  ▼
Layer3a: ComplexityAssessor.assess() → SIMPLE / MEDIUM / COMPLEX
  │
  ▼
Layer3b: IntentRouter.decide(intent, complexity) → RouteDecision(FAST/AGENT/HYBRID, token 预估)
```

**降级原则**：门控异常 → `GateResult.accept` 兜底路由到 Agent 通道（"宁可错放，不可错拦"）。

### 2.2 意图类型与复杂度

| 意图 | 说明 | 关键词示例 | 建议路由 |
|------|------|-----------|----------|
| `ARTICLE_LOOKUP` | 法条查询 | "第几条规定""查法条" | Fast |
| `LEGAL_KNOWLEDGE` | 知识问答 | "什么是""名词解释" | Fast |
| `DOCUMENT_DRAFTING` | 文书起草 | "帮我写起诉状" | Hybrid |
| `LEGAL_CONSULTATION` | 兜底咨询 | — | Agent |
| `CALCULATION` | 金额计算 | "赔偿多少钱""加班费怎么算" | Agent |
| `CASE_SEARCH` | 案例检索 | "有没有类似案例" | Agent |

**复杂度四因子加权**：`涉及法律数量×0.4 + 是否需要计算×0.2 + 子句数量×0.2 + 是否涉及程序×0.2`；`≤0.35 SIMPLE / 0.35~0.65 MEDIUM / ≥0.65 COMPLEX`。

**路由决策矩阵（token 预估）**：

| 意图 | SIMPLE | MEDIUM | COMPLEX |
|------|--------|--------|---------|
| ARTICLE_LOOKUP / LEGAL_KNOWLEDGE | FAST(300) | FAST(300) | **AGENT 升格** |
| LEGAL_CONSULTATION | FAST 降格(500) | AGENT(1500) | AGENT(3000) |
| CALCULATION | FAST 降格(500) | AGENT(800) | AGENT(1600) |
| CASE_SEARCH | FAST 降格 | AGENT(1200) | AGENT(2400) |
| DOCUMENT_DRAFTING | HYBRID | HYBRID | HYBRID |

### 2.3 Fast 通道 vs Agent 通道

| 维度 | Fast Channel | Agent Channel |
|------|-------------|---------------|
| 入口 | `FastChannelHandler` | `AgentRunner` |
| 提示词 | ~15 行精简提示 | ~85 行 ReAct 系统提示 |
| LLM 调用 | 1 次 | 2–6 次（≤5 轮） |
| 工具 / 记忆 / 压缩 | 无 | 6 工具 + 记忆注入 + 4 层压缩 |
| 耗时 | < 2 秒 | 3–15 秒 |

### 2.4 ReAct 循环（AgentRunner）

```
for (iteration = 0; iteration < 5; iteration++) {
  1. needsCompression(messages) → 是 → 构建知识总结 → 直接生成最终答案，退出
  2. chatLanguageModel.generate(messages, toolSpecifications)
  3. 有 ToolExecutionRequest →
        for req : requests
          result = executeTool(req)          // 反射调用 @Tool
          compressed = compressToolResult(...)
          messages.add(ToolExecutionResultMessage(...))
        continue
  4. 有 text → triggerMemoryExtraction(异步) → 返回 success
}
5. 循环耗尽 → 压缩 + 强制最终答案
```

**工具注册**：`registerTools()` 反射扫描 `@Tool` 注解 → `ToolSpecifications.toolSpecificationsFrom()` 生成规范；`@P` 提供参数说明；构建 `ToolMethod(instance+Method+paramNames)` 存入注册表。

### 2.5 上下文压缩（4 层渐进）

| 层 | 方法 | 成本 | 触发 |
|----|------|------|------|
| Layer 0 | `applyLayer0()` | 零 | 单条工具结果 < 500 token：去 markdown 分隔线/连续换行/首尾空格 |
| Layer 1 | `applyLayer1()` → `RuleExtractor` | 零（正则） | 提取法条引用《X法》第X条、金额、时效 |
| Layer 2 | `applyLayer2()` → `SummarizingCompressor` | LLM | 节省量 > 压缩成本×2.0 且原文 ≥400 token；压缩后仍 ≥ 原文则回退 |
| Layer 4 | `buildFinalContext()` | LLM | 全局消息 > 6000 token 或 ReAct 达 5 轮 → 折叠为 4 条消息窗口 |

**按工具定制压缩策略**（`lawmind.agent.compression.tool-strategies`）：`searchLawKnowledge`→Layer1 保留 top5 前 3 完整；`getArticleText`→Layer0 `preserveOriginalTerms=true`（法条原文必须精确）；`classifyLegalIntent`/`expandLegalQuery`/`verifyCitation`→不压缩。

**近因加权**：`recency(keepFullRecent=2, layer1StartRound=3, layer2StartRound=5)`——1-2 轮保留完整结果，3-4 轮规则提取，5 轮起 LLM 语义压缩。

### 2.6 KnowledgeState（结构化知识状态）

| 类别 | 存储 | 提取规则 |
|------|------|----------|
| `articles` | `List<ArticleEntry>` | 正则条款号 + 回溯书名号法律名，截 120 字符规则文本 |
| `calculations` | `List<CalcEntry>` | 金额模式 ±20 字符上下文 |
| `reminders` | `List<String>` | `\d+年/月/日 + (时效/仲裁/诉讼)` |
| `cases` | `List<CaseEntry>` | 关键词（判决书/案例/判例）前 200 字符 |

- **法条去重**：`lawName + articleNumber` 精确匹配；`citeCount`+1、`sources` 合并、`verified` 取 OR。
- **容量驱逐**：articles 超 `maxArticles`(20) 时移除引用最少者。
- **输出**：`toCompactSummary()` 生成「相关法条（按引用频次）/金额计算/时效提醒」结构化总结，供 Layer4 折叠。

---

## 三、业务规则

- 工具调用异常被 `executeTool` 捕获并返回 `"[Tool 错误] …请尝试换一种方式检索"`，不中断循环。
- 禁止对同一工具相同参数调用超 2 次；最多 5 次工具调用；知识库未命中时换关键词重试 1 次。
- 系统提示强制"先检索再判断"，禁止编造法条；不确定内容标注"仅供参考，建议核实"。
- `MemoryManager`、`ContextCompressor` 在整个链路可空，为 `null` 时行为与无增强完全一致（可插拔）。
- 记忆注入点：① 执行前 `retrieveAndFormat()` 追加到系统提示；② 成功后 `@Async` 异步提取。

---

## 四、关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 四级门控 | 领域→意图→复杂度→路由 逐层判定 | 每一层用最便宜的判定手段先过滤，昂贵 LLM 判定仅作兜底 |
| 升格/降格 | 简单意图+复杂问题升格 Agent；简单咨询降格 Fast | 保质量同时省 Agent 开销，按 token 预估路由 |
| fail-open | 门控异常降级放行 | 避免误拦法律咨询 |
| 4 层渐进压缩 | 格式→正则→LLM→全局折叠 | 零成本优先，LLM 压缩保守触发，控制延迟与 Token |
| KnowledgeState | 跨轮次跨工具知识原子去重归并 | 解决 ReAct 循环上下文膨胀与重复检索 |
| 可插拔增强 | MemoryManager/Compressor 可空 | 便于开关与回归对比 |

---

## 五、实现进度

| 项 | 状态 |
|----|:---:|
| DomainGate（规则 + LLM 兜底 + fail-open） | ✅ |
| IntentClassifierEnhanced（7 意图） | ✅ |
| ComplexityAssessor + IntentRouter 决策矩阵 | ✅ |
| Fast / Hybrid / Agent 三通道 | ✅ |
| AgentRunner ReAct 循环 + 反射工具注册 | ✅ |
| 6 个法律工具（见 `memory-system.md` 补充） | ✅ |
| 上下文压缩 4 层 + 按工具策略 + 近因加权 | ✅ |
| KnowledgeState 知识原子（articles/calc/reminder/cases） | ✅ |
| AgentMetricsCollector 监控 | ✅ |
| Agent 监控接入指标日报 / 仪表盘 | 🚧（采集已有，可视化规划中） |
