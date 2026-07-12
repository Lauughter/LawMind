# 合同审查 — 完整实现流程与 Skill 技能包说明

> 文档状态：已完成实现（2026-06-15），所有流程均对应实际可运行的代码。

---

## 一、完整实现流程（四段式）

### 第一段：前端 → Controller（文件上传 + 文本提取）

```
用户拖拽合同文件 → ContractReview.vue
    ↓  fetch("POST /api/contract-review/upload") + FormData
    ↓  SSE 长连接 (SseEmitter, 超时 5 分钟)
    ↓
ContractReviewController.uploadAndReview()
    ↓  新线程 "contract-review-{userId}"
    ↓
步骤1: FileUtil.extractText(file)
    → Apache Tika 自动检测格式
    → PDF → PDFBox,  Word → POI,  TXT → 直接读
    → 纯内存操作，"阅后即焚"，不写磁盘不存库
    ↓
步骤2: buildReviewPrompt(fileName, extractedText)
    → 拼装"审查指令 + 合同全文 + 7章节报告格式要求"
```

### 第二段：意图门控（Gate Pipeline）

```
步骤3: intentGate.process(reviewQuestion)
    ↓
    Layer 1: DomainGate.judge()
        → "这是法律问题吗？" → 是 → 继续
    Layer 2: IntentClassifierEnhanced.classify()
        → 关键词匹配: "审查合同/合同审查/审合同" → CONTRACT_REVIEW
        → LLM 兜底分类（关键词未命中时）
    Layer 3: ComplexityAssessor.assess() + IntentRouter.decide()
        → CONTRACT_REVIEW 在 agent-intents 列表中
        → 路由结果: AGENT 通道, 预估 3000 tokens
    ↓
    gateResult.accepted() == true → 继续
```

### 第三段：Agent 多步推理（核心 — 知识库锚定的审查循环）

```
步骤4: agentRunner.execute(reviewQuestion, CONTRACT_REVIEW_SYSTEM_PROMPT)
                  ↑                          ↑
            合同全文+审查指令       合同审查专用 System Prompt
                                   (覆盖 AgentConfig 默认 Prompt)
    ↓
┌─ Agent 循环 (最多 5 轮) ──────────────────────────────────────┐
│                                                                 │
│  第1轮: LLM 收到 System Prompt + User Prompt                     │
│         → LLM 决定: "先审查违约金条款，需要检索法条"               │
│         → 返回 ToolExecutionRequest:                             │
│           searchLawKnowledge("违约金 过高 调整 民法典第585条")     │
│         ↓                                                        │
│  AgentRunner.executeTool()                                       │
│         ↓                                                        │
│  LawSearchTools.searchLawKnowledge()                             │
│         ↓                                                        │
│  EmbeddingUtil.embed() → 查询向量化                               │
│         ↓                                                        │
│  HybridSearchService.searchHybrid()                              │
│         ↓                                                        │
│  向量检索 (Redis) + BM25 关键词检索 → RRF 融合 → Top 10           │
│         ↓                                                        │
│  返回: "[1] 《民法典》| 第585条 | 违约金超过造成损失30%..."        │
│         ↓                                                        │
│  第2轮: LLM 收到检索结果 + 继续审查                                │
│         → "违约责任条款约定的违约金为合同金额200%，                  │
│            远超法定30%上限，属于🔴严重风险"                         │
│         → 继续调用 searchLawKnowledge 检索下一个条款的法律依据      │
│         ↓                                                        │
│  第3-N轮: 逐条 检索→对照→判断，覆盖 10 大风险维度                   │
│         ↓                                                        │
│  最终轮: 生成完整审查报告（7个章节）                                │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
    ↓
AgentResult.success(answer, conversationHistory)
```

### 第四段：SSE 流式返回 → 前端渲染

```
Agent 返回完整报告文本 (Markdown)
    ↓
Controller 逐字符 SSE 推送 (每3字符 sleep 5ms 模拟流式效果)
    event:progress → {"message":"Agent 正在从知识库检索法条..."}
    event:message  → "合"  (逐字符)
    event:message  → "同"
    event:message  → "审"
    ...
    event:done     → {"status":"completed","channel":"agent",...}
    ↓
ContractReview.vue  processEvent()
    → streamContent 逐字符拼接
    → ContractReviewReport.vue (MarkdownContent 渲染)
    → 审查完成后可: 复制报告 / 下载 .md / 审查新合同
```

---

## 二、关键组件对照表

| 流程阶段 | 实际代码文件 | 关键方法 | 说明 |
|---------|------------|---------|------|
| 文件上传入口 | `ContractReviewController.java` | `uploadAndReview()` | SSE 端点，新线程异步处理 |
| 文本提取 | `FileUtil.java` | `extractText()` | Apache Tika → PDFBox/POI |
| 审查指令拼装 | `ContractReviewController.java` | `buildReviewPrompt()` | 7 章节报告格式 + 合同全文 |
| 领域门控 | `DomainGate.java` | `judge()` | 判断是否法律问题 |
| 意图分类 | `IntentClassifierEnhanced.java` | `classify()` | 关键词 + LLM 兜底 |
| 路由决策 | `IntentRouter.java` | `decide()` | CONTRACT_REVIEW → AGENT |
| Agent 推理引擎 | `AgentRunner.java` | `execute(question, systemPrompt)` | 多步推理循环，最多 5 轮 |
| 知识库检索工具 | `LawSearchTools.java` | `searchLawKnowledge()` | `@Tool` 注解，Agent 自动调用 |
| 法条原文查询 | `LawSearchTools.java` | `getArticleText()` | `@Tool` 注解，精确查法条 |
| 混合检索引擎 | `HybridSearchServiceImpl.java` | `searchHybrid()` | 向量 + BM25 → RRF 融合 |
| 向量化 | `EmbeddingUtil.java` | `embed()` | 文本 → 向量（调用嵌入模型） |
| 上下文压缩 | `ContextCompressor.java` | `needsCompression()` / `compressToolResult()` | 超阈值时压缩历史消息 |
| 监控采集 | `AgentMetricsCollector.java` | `recordAgentCall()` / `recordToolCall()` | Agent 调用次数、Tool 调用统计 |
| 前端上传页 | `ContractReview.vue` | `startReview()` | el-upload + fetch SSE 手动解析 |
| 前端报告渲染 | `ContractReviewReport.vue` | — | 复用 MarkdownContent 组件 |
| 路由注册 | `router/index.js` | — | `/home/contract-review` |
| 导航菜单 | `Layout.vue` | — | "合同审查"菜单项 + Checked 图标 |

---

## 三、审查报告七段式结构

审查报告按以下固定格式输出（在 `buildReviewPrompt()` 中定义）：

| 章节 | 内容 | 说明 |
|------|------|------|
| 一、合同概况 | 合同类型、主体、核心内容概要 | 一句话总结 |
| 二、总体评分 | 合法性/公平性/完整性 三维度评分（1-10） | 含综合评分 |
| 三、逐条审查意见 | 表格：条款 \| 原文摘要 \| 检索法条依据 \| 风险等级 \| 法律分析 \| 修改建议 | **法条依据必须来自 searchLawKnowledge 返回结果** |
| 四、高风险条款汇总 | 列出所有 🔴 严重级别条款 | 聚焦最需要关注的问题 |
| 五、签约建议 | 建议签署 / 建议修改后签署 / 不建议签署 | 附理由 |
| 六、引用法条清单 | 表格：法条 \| 相关内容 | 审查中引用的全部法条 |
| 七、优化示范条款 | 对 🔴/🟡 条款给出完整重写版本 | 原条款 → 优化建议 → 修改要点（含法律依据） |

---

## 四、Agent 循环中的关键机制

### 4.1 System Prompt 运行时注入

AgentRunner 支持两种调用方式：

```java
// 通用法律咨询（使用 AgentConfig 中配置的默认 System Prompt）
agentRunner.execute(userQuestion);

// 合同审查等专项场景（注入领域专用 System Prompt，覆盖默认值）
agentRunner.execute(reviewQuestion, CONTRACT_REVIEW_SYSTEM_PROMPT);
```

合同审查场景下，`CONTRACT_REVIEW_SYSTEM_PROMPT`（~90 行 Java 文本块常量）以"资深合同审查律师"角色注入，包含：

- "先检索再判断"强制规则
- 10 大风险维度审查清单（含检索关键词）
- 8 种常见不公平条款检测模式
- 三级风险等级标准（🔴严重 / 🟡需关注 / 🟢合规）
- 行为约束（不编造法条、不确定标注"需进一步核实"）

### 4.2 上下文压缩（防溢出）

Agent 多轮工具调用会快速积累上下文。`ContextCompressor` 在以下时机介入：

- 工具返回结果超阈值 → `compressToolResult()` 压缩长文本
- 全局上下文超阈值 → `needsCompression()` 触发 → 用结构化知识摘要重建上下文
- 达到最大迭代次数 (5轮) → 强制终止循环，基于已有信息生成最终回答

### 4.3 工具调用失败降级

`AgentRunner.executeTool()` 中捕获所有异常，返回 `"[Tool 错误] ...请尝试换一种方式检索"` 而不是直接抛异常导致 Agent 循环中断。

### 4.4 "阅后即焚"安全保障

- 合同文件不写入磁盘（仅在 `MultipartFile.getInputStream()` 中流转）
- 文本提取后不存入数据库（无 `LawFileUpload` 持久化）
- 不触发 `DocumentIngestionService`（不加入 RAG 知识库索引）
- 审查完成后仅保留报告文本，原始合同内容随请求结束而释放

---

## 五、Skill 技能包运行时状态说明

> **关键结论：Skill 技能包目前是设计时参考文档，尚未在运行时被代码动态加载。**

### 5.1 验证证据

搜索了全部 Java 源码（`grep -r "skills/" --include="*.java"`），结果为 **零匹配**。没有任何代码读取 `skills/` 目录下的文件或解析 `manifest.yaml`。

### 5.2 Skill 文件实际用途对照

| Skill 目录文件 | 设计意图 | 实际运行时来源 | 加载方式 |
|-------------|---------|-------------|---------|
| `manifest.yaml` | Skill 元数据（名称、版本、触发意图、所需工具） | **未加载** | — |
| `prompt/system-message.md` | Agent 审查时的系统提示词 | `ContractReviewController.CONTRACT_REVIEW_SYSTEM_PROMPT`（Java 文本块常量） | 编译时硬编码 |
| `prompt/user-prompt-template.md` | 用户问题模板（含占位符） | `ContractReviewController.buildReviewPrompt()` 方法 | 运行时动态拼接 |
| `checklists/general-clauses.yaml` | 通用条款审查清单（10 维度） | 已融入 System Prompt 的 Markdown 表格 | 编译时硬编码 |
| `checklists/labor-contract.yaml` | 劳动合同专项清单 | **未加载** — 当前仅通用清单生效 | — |
| `checklists/loan-agreement.yaml` | 借款合同专项清单 | **未加载** — 当前仅通用清单生效 | — |
| `checklists/rental-contract.yaml` | 租赁合同专项清单 | **未加载** — 当前仅通用清单生效 | — |
| `patterns/unfair-clauses.yaml` | 不公平条款模式库（8 种模式） | 已融入 System Prompt 的"常见不公平条款检测模式"列表 | 编译时硬编码 |
| `templates/review-report.md` | 审查报告输出模板 | `buildReviewPrompt()` 中的 7 章节输出格式 | 运行时动态拼接 |
| `references/` | 民法典合同编摘要 + 典型判例 | **未加载** | — |

### 5.3 当前架构图示

```
运行时实际生效的组件                    仅作为设计文档存在的文件

┌───────────────────────────┐      ┌──────────────────────────────┐
│ ContractReviewController  │      │ skills/contract-review/       │
│  · CONTRACT_REVIEW_       │◄─────│  · manifest.yaml              │
│    SYSTEM_PROMPT (90行)   │ 设计 │  · prompt/system-message.md   │
│  · buildReviewPrompt()    │ 参考 │  · prompt/user-prompt-        │
│                           │      │    template.md                │
├───────────────────────────┤      │  · checklists/*.yaml          │
│ AgentConfig               │      │  · patterns/unfair-clauses.   │
│  · agentRunner Bean       │      │    yaml                       │
│  · 注册三大 Tool 组       │      │  · templates/review-report.md │
│                           │      │  · references/*.md            │
├───────────────────────────┤      └──────────────────────────────┘
│ LawSearchTools (@Tool)    │
│  · searchLawKnowledge()   │      Skill 文件在设计阶段指导了
│  · getArticleText()       │      代码编写，但运行时 Agent 不会
│                           │      动态读取或热加载它们。
├───────────────────────────┤
│ AgentRunner               │      所有 prompt、清单、检测模式
│  · execute(q, sysPrompt)  │      已经"编译"进了 Java 代码和
│  · 最多 5 轮推理循环      │      Spring Bean 配置中。
│  · 上下文压缩 + 降级      │
└───────────────────────────┘
```

### 5.4 若要实现真正的 Skill 热加载

如果希望 Skill 包真正做到"可插拔、热更新"（如新增劳动合同审查清单无需重启服务），需要实现以下组件：

| 组件 | 职责 | 预估工作量 |
|------|------|----------|
| `SkillLoader` | 解析 `manifest.yaml`，注册 Skill 元数据 | 0.5 天 |
| `SkillPromptResolver` | 读取 `prompt/*.md`，替换占位符，生成运行时 Prompt | 0.5 天 |
| `SkillChecklistResolver` | 按合同类型匹配专项审查清单（如劳动合同 → labor-contract.yaml） | 0.5 天 |
| `SkillPatternLoader` | 加载不公平条款模式库，用于规则匹配 | 0.5 天 |
| `SkillManager` | 管理 Skill 生命周期：注册、热更新、版本切换 | 1 天 |
| **合计** | | **3 天** |

当前阶段（MVP 验证）暂不需要此项改造。当出现以下场景时再考虑：

- 需要频繁更新审查清单而不想重启服务
- 需要支持用户自定义审查维度
- 合同类型扩展到 10+ 种，每种有独立的专项清单
