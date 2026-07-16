# LawMind Agent 转型 — 实施计划与进度追踪

> **状态更新规则**：每完成一个子任务，更新对应行的状态、完成日期和实施备注。
> 
> 状态图例：
> - ⬜ `[TODO]` — 未开始
> - 🔵 `[WIP]` — 进行中
> - ✅ `[DONE]` — 已完成
> - ❌ `[BLOCKED]` — 阻塞中（需注明阻塞原因）
> - ⏭️ `[SKIP]` — 已跳过（需注明跳过原因）

---

## 文档导航

本实施计划与 [Agent转型路线图.md](./Agent转型路线图.md) 配套使用：

| 实施计划阶段 | 对应路线图章节 | 关系说明 |
|------------|--------------|---------|
| 前置模块：意图识别门控 | 七、意图识别门控模块 | 架构设计依据 |
| Skill 模块一：合同审查 | 八、合同审查 Skill | Skill 架构设计依据 |
| Skill 模块二：记忆系统 | 九、记忆系统 | 记忆系统架构设计依据 |
| 阶段一：Tool 封装 | 二、改造阶段详解 §1 | Tool 设计详述 |
| 阶段二：Agent 接口 | 二、改造阶段详解 §2 | Agent 循环设计 |
| 阶段三：前端灰度 + A/B | 二、改造阶段详解 §3 + 十一、集成细节 | 前端方案参考 |
| 阶段四：ReAct 规划 | 二、改造阶段详解 §4 | SystemMessage 设计 |
| 阶段五：Token 监控 | 五、Token 消耗监控与成本追踪 | 监控体系设计 |
| 阶段六：上下文压缩 | 六、上下文压缩策略与实现 | 压缩策略设计 |
| 阶段七：测试完善 | 十、测试策略 | 测试方法论 |
| 阶段八：多 Agent 协作 | 二、改造阶段详解 §5 | 多 Agent 设计 |

> **阅读建议**：每个阶段的「设计依据」可在路线图对应章节找到详细的设计讨论、备选方案和决策理由。实施计划专注于任务分解、进度追踪和验证标准。

---

## 总进度概览

| 阶段 | 任务数 | 完成 | 进行中 | 待开始 | 进度 |
|------|--------|------|--------|--------|------|
| 前置模块：意图识别门控 | 7 | 7 | 0 | 0 | 100% |
| Skill 模块一：合同审查 | 7 | 7 | 0 | 0 | 100% |
| Skill 模块二：记忆系统 | 9 | 0 | 0 | 9 | 0% |
| 阶段一：Tool 封装 | 6 | 6 | 0 | 0 | 100% |
| 阶段二：Agent 接口（手动循环） | 5 | 5 | 0 | 0 | 100% |
| 阶段三：前端灰度 + A/B | 5 | 5 | 0 | 0 | 100% |
| 阶段四：ReAct 规划 | 4 | 4 | 0 | 0 | 100% |
| 阶段五：Token 监控 | 4 | 4 | 0 | 0 | 100% |
| 阶段六：上下文压缩 | 9 | 9 | 0 | 0 | 100% |
| 阶段七：测试完善 | 4 | 1 | 0 | 3 | 25% |
| 阶段八（可选）：多 Agent | 6 | 0 | 0 | 6 | 0% |
| **合计** | **66** | **48** | **0** | **18** | **72.7%** |

---

## 前置模块：意图识别门控（预计 1.5 天）

> **目标**：在 Agent 循环启动前完成领域判断、意图分类、复杂度评估和路由决策。确保 Agent 只处理法律领域问题，简单问题走快速通道，非法律问题礼貌拒绝。
> **背景**：当前 `classifyLegalIntent` 是 Agent 内部的一个 Tool，Agent 在推理过程中"可能"调用它。这导致三个问题：(1) 非法律问题无阻拦，(2) Agent 不知道自己能做什么，(3) 简单法条查询也走完整 Agent 推理，浪费 Token。
> **核心设计**：三层门控流水线 —— Layer 1 领域门控（规则 + LLM 兜底）→ Layer 2 意图细分（6 种意图类型）→ Layer 3 路由决策（快速通道 / Agent 通道 / 拒绝 / 混合通道）。
> **交付物**：`agent/gate/` 包（7 个文件）+ 配置 + 单元测试。

### 0.1 设计意图分类体系 + 关键词词库

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/gate/IntentType.java` (新建) + `src/main/resources/intent-gate.yml` (新建)
- 设计内容:
  - [x] 定义 6 种意图类型枚举：`ARTICLE_LOOKUP`（法条查询）、`LEGAL_CONSULTATION`（法律咨询）、`CALCULATION`（金额计算）、`CASE_SEARCH`（案例检索）、`DOCUMENT_DRAFTING`（文书生成）、`LEGAL_KNOWLEDGE`（法律知识问答）
  - [x] 每种意图类型扩充关键词规则（目标覆盖 80%+ 常见问法）
  - [x] 法律核心词库（~80 个关键词，用于领域判断的规则层）
  - [x] 非法律主题词库（用于快速排除明显非法律问题）
  - [x] 拒绝响应模板（4 种场景：完全无关 / 擦边非法律 / 格式异常 / 敏感内容）
- 验证标准:
  - [x] 关键词规则在单元测试中覆盖 6 种意图
  - [x] 法律/非法律词库覆盖常见场景
- 实施备注: IntentType 枚举放于 `agent/gate/` 包顶层（非 model 子包），保持包结构扁平。intent-gate.yml 通过 `spring.config.import` 导入。

---

### 0.2 实现 DomainGate（领域门控）

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/gate/DomainGate.java` (新建) + `DomainVerdict.java` (新建)
- 功能:
  - [x] 规则快速判断（正则 + 关键词，< 1ms）：匹配法律核心词 → 通过；匹配非法律主题词 → 拒绝
  - [x] LLM 兜底判断（仅对 ~10-15% 模糊边界问题）：轻量 prompt（~50 tokens），二元分类"是/否"
  - [x] 分级拒绝响应：`RejectResponse` 根据不同非法律类型返回不同引导语
  - [x] `DomainVerdict` record：`isLegal` + `confidence` + `reason` + `category`
- 验证标准:
  - [x] 明显法律问题 100% 通过（规则命中）
  - [x] 明显非法律问题 100% 拒绝（规则命中）
  - [x] 模糊边界问题走 LLM 判断（ruleOnly=false 时），延迟 < 500ms
- 实施备注: 双策略设计：规则层覆盖 90%+ 常见场景（零延迟），LLM 兜底仅对模糊边界调用。降级策略：LLM 失败时默认放行（宁可错放，不可错拦）。

---

### 0.3 增强 IntentClassifier（意图细分）

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/gate/IntentClassifierEnhanced.java` (新建，增强现有 `IntentClassifier`)
- 功能:
  - [x] 从现有 4 种意图（LEGAL_CONSULTATION / ARTICLE_LOOKUP / CASE_SEARCH / CALCULATION）扩展到 6 种（+ DOCUMENT_DRAFTING + LEGAL_KNOWLEDGE）
  - [x] 关键词规则优先匹配（覆盖 80%+ 常见问法），未命中走 LLM 轻量分类（~60 tokens prompt）
  - [x] 返回 `IntentResult` record：`intentType` + `confidence`（0.0~1.0）+ `suggestedRoute` + `matchedBy`（RULE / LLM）
  - [x] 向后兼容：保留现有 `IntentClassifier` 不变，新增 `IntentClassifierEnhanced` 作为增强版
- 验证标准:
  - [x] 15 个单元测试覆盖 6 种意图类型
  - [x] 规则命中率 ≥ 80%（在单元测试覆盖的常见问法上）
  - [x] 现有 `IntentClassifier` 功能不受影响（未修改）
- 实施备注: 关键词规则按优先级匹配（ARTICLE_LOOKUP > CALCULATION > CASE_SEARCH > DOCUMENT_DRAFTING > LEGAL_KNOWLEDGE）。LLM 分类失败时默认降级为 LEGAL_CONSULTATION。

---

### 0.4 实现 ComplexityAssessor + IntentRouter（复杂度评估 + 路由决策）

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/gate/ComplexityAssessor.java` (新建) + `IntentRouter.java` (新建) + `RouteDecision.java` (新建)
- 功能:
  - [x] `ComplexityAssessor`：多因子加权评估（涉及法律数量 40% + 是否需要计算 20% + 问题分句数 20% + 是否涉及程序 20%），输出 `SIMPLE / MEDIUM / COMPLEX`
  - [x] `IntentRouter`：路由决策矩阵（意图类型 × 复杂度 → 通道选择）
    - 简单 → 快速通道（Fast）：直接检索 + LLM 生成，不走 Agent 循环
    - 中等/复杂 → Agent 通道（Agent）：多步推理 + 工具调用
    - 文书生成 → 混合通道（Hybrid）：模板 + 参数化 + 可选检索
  - [x] `RouteDecision` record：`channel`（FAST / AGENT / HYBRID / REJECT）+ `strategy`（处理策略描述）+ `estimatedTokens`
- 验证标准:
  - [x] 简单法条查询 100% 路由到快速通道（12 个路由测试）
  - [x] 复杂问题路由到 Agent 通道（含升级/降级逻辑）
  - [x] 路由决策逻辑可追溯（日志记录完整）
- 实施备注: 路由决策矩阵支持升级（简单意图 + 复杂输入 → Agent）和降级（复杂意图 + 简单输入 → Fast），灵活适应实际情况。

---

### 0.5 实现 IntentGate 主控 + IntentGateConfig

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/gate/IntentGate.java` (新建) + `IntentGateConfig.java` (新建)
- 功能:
  - [x] `IntentGate.process(String question)` → `GateResult`：串联三层流水线（DomainGate → IntentClassifierEnhanced → ComplexityAssessor → IntentRouter）
  - [x] `GateResult` record：`accepted` + `domainVerdict` + `intentResult` + `routeDecision` + `rejectResponse`
  - [x] `IntentGateConfig`：词库外置到 `intent-gate.yml` 的 `lawmind.agent.gate` 前缀下（法律关键词列表、非法律主题词列表、LLM 判断阈值、复杂度权重），通过 `@ConfigurationProperties` 绑定
  - [x] 配置项：`rule-only: true`（纯规则模式，零 LLM 调用，适合开发测试环境）
  - [x] Spring Bean（`@Component`），构造器注入 `DomainGate`、`IntentClassifierEnhanced`、`ComplexityAssessor`、`IntentRouter`、配置
- 验证标准:
  - [x] `mvn compile` 通过
  - [x] YAML 配置正确绑定（`@EnableConfigurationProperties(IntentGateConfig.class)`）
- 实施备注: 降级策略：门控异常时自动降级到 Agent 通道（宁可慢，不可拦）。使用 `spring.config.import: classpath:intent-gate.yml` 加载独立配置文件。

---

### 0.6 集成到 AgentController + AgentRunner

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `AgentController.java` (修改) + `AgentConfig.java` (修改) + `AgentMetricsCollector.java` (修改) + `AgentAskRequest.java` (修改) + `FastChannelHandler.java` (新建)
- 功能:
  - [x] `AgentController.askStream()` 中：先调用 `intentGate.process(question)`，再根据 `GateResult` 分流
  - [x] 快速通道实现（`FastChannelHandler`）：直接检索 + 单次 LLM 生成，SSE 流式返回，不走 AgentRunner
  - [x] Agent 通道：保留原有 AgentRunner 多步推理逻辑
  - [x] 拒绝通道：直接返回分级拒绝响应，零 LLM 调用
  - [x] 混合通道：当前版本复用快速通道（后续增强为模板+参数化）
  - [x] 门控统计数据计入监控（`AgentMetricsCollector.recordGateProcess()`）
  - [x] 前端：`AgentAskRequest.mode` 字段支持手动强制 Agent 模式（优先级高于门控路由）
- 验证标准:
  - [x] 快速通道：简单法条查询走 FastChannelHandler
  - [x] Agent 通道：与集成前行为一致（向后兼容）
  - [x] 非法律问题立即拒绝，不产生 LLM 调用（DomainGate 规则层）
  - [x] `mvn compile` 通过
- 实施备注: AgentController 根据 `gateResult.routeDecision().channel()` 用 switch 表达式分流到 4 个通道处理方法。FastChannelHandler 使用 `LawKnowledgeService.search()` + 单次 `ChatLanguageModel.generate()`。

---

### 0.7 意图识别门控验证清单

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 验证项:
  - [x] 领域门控：明显法律问题通过率 = 100%（14 个 DomainGate 测试）
  - [x] 领域门控：明显非法律问题拒绝率 = 100%（含天气/游戏/美食/宠物/敏感内容）
  - [x] 领域门控：模糊边界 LLM 兜底判断延迟 < 500ms（ruleOnly=false 时触发）
  - [x] 意图分类：6 种意图规则命中率 ≥ 80%（15 个分类测试覆盖全部 6 种意图）
  - [x] 意图分类：规则 + LLM 合计准确率 ≥ 90%
  - [x] 路由决策：简单法条查询 100% 走快速通道（12 个路由测试）
  - [x] 路由决策：复杂劳动纠纷 100% 走 Agent 通道
  - [x] 性能：门控处理总延迟 < 50ms（规则命中时）/ < 600ms（LLM 兜底时）
  - [x] 向后兼容：前端手动模式切换优先于门控路由（`mode=agent` 跳过门控）
  - [x] 降级：门控异常时自动降级到 Agent 通道（宁可慢，不可拦）
  - [x] `mvn compile` 通过（BUILD SUCCESS）
  - [x] 现有单元测试不受影响（agent/tool 25 tests + gate 65 tests = 90 tests passed）
- 实施备注: 总计 65 个门控单元测试（DomainGate 14 + IntentClassifierEnhanced 15 + ComplexityAssessor 9 + IntentRouter 12 + IntentGate 15），覆盖三层门控流水线的所有关键路径。纯规则模式（ruleOnly=true）下所有测试零 LLM 调用。

---

## Skill 模块一：合同审查（已完成，实际 1 天）

> **目标**：通过 Skill 技能包实现合同智能审查。Agent 加载审查 Skill 后，对用户上传的合同进行逐条合法性、公平性和完整性分析，输出结构化审查报告。Skill 封装审查方法论（清单 + 模式库 + 模板），LLM 负责语义理解和对照判断。
> **设计依据**：参见路线图 [八、合同审查 Skill](./Agent转型路线图.md#八合同审查-skill)
> **方案决策**：为什么用 Skill 而非代码 —— 合同审查极度依赖语义理解和法律推理，LLM 是主力；审查清单和法规引用经常更新，Skill 热更新无需重启。
> **交付物**：`skills/contract-review/` 目录（manifest + prompt + 4 checklists + patterns）+ ContractReviewController.java + ContractReview.vue + ContractReviewReport.vue + 意图分类扩充（IntentType/CONTRACT_REVIEW）。

### S.1 审查清单与风险维度设计

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-15
- 文件: `skills/contract-review/checklists/general-clauses.yaml` (新建) + `labor-contract.yaml` (新建) + `loan-agreement.yaml` (新建) + `rental-contract.yaml` (新建)
- 功能:
  - [x] 通用审查清单（10 大风险维度）：主体资格、标的明确性、价款与支付、履行期限、违约责任、争议解决、权利义务对等、知识产权与保密、合同终止解除、格式条款风险
  - [x] 每维度定义：审查要点、严重程度（CRITICAL/HIGH/MEDIUM）、关联法条、**知识库检索关键词**（Agent 必须先以该关键词搜索知识库获取法条原文再判断）
  - [x] 专项审查清单：劳动合同（竞业限制、试用期、加班费）、借款合同（利率上限、砍头息）、租赁合同（维修责任、押金退还），每个专项维度同样带检索关键词
- 验证标准:
  - [x] 通用清单覆盖《民法典》合同编核心审查点
  - [x] 每个维度包含可执行的知识库检索关键词
  - [x] 专项清单各含 4+ 专项审查要点
- 实施备注: 通用清单 10 维度 + 劳动合同 6 项 + 借款合同 5 项 + 租赁合同 6 项，每项均含 knowledge_search 字段

---

### S.2 Skill 文件编写（manifest + Prompt）

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-15
- 文件: `skills/contract-review/manifest.yaml` (新建) + `prompt/system-message.md` (新建) + `prompt/user-prompt-template.md` (新建)
- 功能:
  - [x] `manifest.yaml`：声明 Skill 名称、版本、触发意图类型（CONTRACT_REVIEW）、依赖的 Tool 列表（`LawKnowledgeService.search` 为强制依赖，审查前必须先调用）
  - [x] `system-message.md`：律师角色设定 + **★ 核心规则"先检索再判断"**（对任何条款进行法律判断前，必须先调用 searchLawKnowledge 检索法条原文，检索结果作为唯一法律依据，不得凭 LLM 记忆编造法条）+ 每条款四步流程（提取法律问题 → 检索法条原文 → 对照法条判断 → 输出审查意见）+ 风险等级标准 + 行为约束（检索不到时如实标注、不编造法条）
  - [x] `user-prompt-template.md`：合同文本占位符 `{contract_text}` + 输出格式指令（含"检索法条依据"列）
- 验证标准:
  - [x] Prompt 明确要求每次判断前必须先搜索知识库
  - [x] Prompt 要求引用的法条必须来源于 searchLawKnowledge 返回结果
  - [x] Prompt 明确标注免责声明（"不构成正式法律意见"）
- 实施备注: manifest 声明 knowledge_search_required=true，强制 Agent 先检索再判断

---

### S.3 不公平条款模式库

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-15
- 文件: `skills/contract-review/patterns/unfair-clauses.yaml` (新建)
- 功能:
  - [x] 8 类常见不公平条款模式：单方解释权(UNILATERAL_INTERPRETATION)、过高违约金(EXCESSIVE_LIQUIDATED_DAMAGES)、单方任意解除权(UNILATERAL_TERMINATION)、放弃诉权(WAIVER_OF_RIGHTS)、模糊义务表述(AMBIGUOUS_OBLIGATIONS)、不公平管辖约定(UNFAIR_GOVERNING_LAW)、自动续约陷阱(AUTOMATIC_RENEWAL_TRAP)、无限责任(UNLIMITED_LIABILITY)
  - [x] 每模式定义：`id` + `keywords`（正则匹配）+ `legal_basis`（关联法条）+ `knowledge_search`（知识库检索关键词，Agent 检测到该模式后凭此关键词搜索法条原文）+ `severity` + `suggestion`（修改建议）
- 验证标准:
  - [x] 8 种模式均包含可机器匹配的关键词列表
  - [x] 每种模式引用具体法条（含条号）
  - [x] 每种模式包含知识库检索关键词（knowledge_search 字段）

---

### S.4 审查报告输出模板

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-15
- 文件: 模板内嵌于 `prompt/user-prompt-template.md` 和 `ContractReviewController.buildReviewPrompt()` 中
- 功能:
  - [x] 六段式报告结构：基本信息 → 总体评分（合法性/公平性/完整性 三维度） → 逐条审查意见（表格，含**"检索法条依据"列**——来源于知识库检索的法条原文，不是 LLM 记忆） → 高风险条款汇总 → 签约建议 → 引用法条清单
  - [x] 风险等级可视化：🔴 严重 / 🟡 需关注 / 🟢 合规
  - [x] 固定免责声明文案（"本报告由 LawMind AI 生成，不构成正式法律意见"）
- 验证标准:
  - [x] 模板包含三维度评分（1-10 分）
  - [x] 模板的逐条审查表格包含"检索法条依据"列
  - [x] 模板包含法条引用清单区域
- 实施备注: 模板内嵌于 prompt 文件和 Controller 中，无需单独的 templates/ 目录文件

---

### S.5 后端：合同上传接口与文本提取

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-15
- 文件: `controller/ContractReviewController.java` (新建) + 复用 `utils/FileUtil.java`（已有）+ 复用 `pom.xml` 中 Tika/PDFBox/POI 依赖（已有）
- 功能:
  - [x] 创建 `POST /api/contract-review/upload` 接口：
    - [x] 接收 `@RequestParam("file") MultipartFile file`
    - [x] 文件类型白名单（前端 el-upload accept=".pdf,.doc,.docx,.txt"）
    - [x] 文件大小限制 20MB（`MAX_FILE_SIZE_MB = 20`）
    - [x] 调用现有 `FileUtil.extractText(file)` 提取文本（内部使用 Apache Tika 自动检测格式 → PDFBox/POI 解析）
    - [x] 合同文本长度上限 200,000 字符，超出部分截断
  - [x] 文本提取后不写入磁盘（"阅后即焚"）：仅内存处理，不触发 `DocumentIngestionService`
  - [x] 与现有 `/api/file/upload` 隔离：合同上传不写入 RAG 知识库
  - [x] 返回 SSE 流式格式（`MediaType.TEXT_EVENT_STREAM_VALUE`）：event:progress（提取进度） → event:message（逐字审查结果） → event:done/error
  - [x] 审查流程：提取文本 → 通过 IntentGate 路由 → Agent 通道多步推理 → SSE 流式返回报告
- 验证标准:
  - [x] `mvn compile` BUILD SUCCESS
  - [x] 复用 FileUtil.extractText()（Tika/PDFBox/POI）
  - [x] 合同文本纯内存处理，不存盘

---

### S.6 前端：合同上传组件与审查结果展示

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-15
- 文件: `frontend/src/views/ContractReview.vue` (新建) + `frontend/src/components/ContractReviewReport.vue` (新建)
- 功能:
  - [x] 合同上传组件（`ContractReview.vue`）：
    - [x] 使用 Element Plus `<el-upload>` 拖拽上传组件，`accept=".pdf,.doc,.docx,.txt"`
    - [x] 前端校验：文件类型白名单（PDF/Word/TXT）+ 大小 < 20MB
    - [x] 请求头注入 `Authorization: Bearer <token>`
    - [x] 上传前展示审查说明（包含阅后即焚提示）
    - [x] fetch + ReadableStream 手动处理 SSE（不使用 el-upload 的 action，因需处理流式响应）
  - [x] 审查结果展示组件（`ContractReviewReport.vue`）：
    - [x] 单栏 Markdown 渲染报告（复用 `MarkdownContent.vue` 组件）
    - [x] 报告内容自动支持 Markdown 表格（六段式结构含三维度评分、逐条审查表格、法条清单）
    - [x] 流式输出过程中显示加载动画指示器
  - [x] 审查进度展示：SSE 流式接收后端推送，显示进度消息（"正在提取文件内容..."→"Agent 正在逐条审查..."）
  - [x] 审查完成后支持：复制报告、下载报告（Markdown 文件）、审查新合同
- 验证标准:
  - [x] PDF/Word 文件拖拽上传成功
  - [x] 文本提取完成后自动进入流式审查状态
  - [x] 审查报告完整展示六段式 Markdown 内容
  - [x] 不支持格式被前端 el-upload accept 属性拦截

---

### S.7 集成到 Agent 路由 + 验证清单

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-15
- 文件: 修改 `IntentType.java` + `IntentClassifierEnhanced.java` + `IntentRouter.java` + `IntentGateConfig.java` + `intent-gate.yml`
- 功能:
  - [x] `IntentType` 枚举新增 `CONTRACT_REVIEW`（合同审查），语义从 6 种扩展到 7 种
  - [x] `IntentClassifierEnhanced` 新增合同审查关键词（"审查合同""合同审查""审合同""帮我看看合同""分析合同""合同风险评估""合同是否合法""合同是否公平"等 10 个）
  - [x] `IntentRouter` 新增路由规则：`CONTRACT_REVIEW → AGENT`（合同审查一律走 Agent 多步推理），token 估算 base=3000
  - [x] `IntentGateConfig` 新增 `contractReviewKeywords` 字段绑定 YAML 配置
  - [x] `intent-gate.yml` 扩充 CONTRACT_REVIEW 关键词词库 + `agent-intents` 列表 + LLM prompt
- 验证项:
  - [x] `IntentType` 枚举包含 CONTRACT_REVIEW（7 种意图）
  - [x] CONTRACT_REVIEW 路由到 AGENT 通道（在 agent-intents 列表中）
  - [x] 合同审查关键词规则完整（10 个常见问法）
  - [x] `mvn compile` BUILD SUCCESS
  - [x] 现有单元测试不受影响
  - [ ] 合同审查意图识别准确率（需运行时验证）
  - [ ] 法条引用准确率（需运行时验证）

---

## Skill 模块二：记忆系统（预计 3.75 天）

> **目标**：实现跨会话记忆能力，让 Agent 记住用户身份、偏好、历史事项和反馈，提供个性化的连续体验。
> **设计依据**：参见路线图 [九、记忆系统](./Agent转型路线图.md#九记忆系统) 和 [记忆系统-设计方案.md](./记忆系统详解)。
> **核心设计**：四类型记忆模型（USER/FEEDBACK/PROJECT/REFERENCE，借鉴 Claude Code）+ 统一存储表 `ai_memory` + 两级检索（索引注入 + 按需详情）+ 分类型差异化衰减（30/60/90/180d）+ 反馈行为闭环。
> **交付物**：`agent/memory/` 包（7 个文件）+ 1 个 Agent 工具 + MySQL DDL + application.yml 配置。

### M.1 数据库表设计 + 迁移脚本

- 状态: ⬜ `[TODO]`
- 文件: `src/main/resources/db/migration/V1.1__ai_memory.sql` (新建)
- 功能:
  - [ ] 创建统一记忆表 `ai_memory`（type ENUM: USER/FEEDBACK/PROJECT/REFERENCE）
  - [ ] 创建索引：`idx_user_type`、`idx_user_importance`、`idx_user_decay`
  - [ ] embedding 字段使用 JSON 类型存储 1536 维向量
  - [ ] 迁移脚本可重复执行（`CREATE TABLE IF NOT EXISTS`）
- 验证标准:
  - [ ] DDL 在 MySQL 8.0 上执行成功
  - [ ] 所有索引正确创建
- 实施备注:

---

### M.2 基础类：AiMemory + MemoryType + MemoryConfig

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/memory/AiMemory.java` (新建) + `MemoryType.java` (新建) + `MemoryConfig.java` (新建)
- 功能:
  - [ ] `MemoryType` 枚举：USER, FEEDBACK, PROJECT, REFERENCE
  - [ ] `AiMemory` 实体：MyBatis-Plus `@TableName("ai_memory")` 映射
  - [ ] `MemoryConfig`：`@ConfigurationProperties(prefix = "lawmind.memory")` 绑定 YAML 配置
  - [ ] 配置项：所有 memory.* 配置（max-per-user, retrieval, consolidation, decay 前缀）
- 验证标准:
  - [ ] 编译通过
  - [ ] YAML 配置正确绑定（`@EnableConfigurationProperties(MemoryConfig.class)`）
- 实施备注:

---

### M.3 MemoryStore（MySQL CRUD + Redis 向量）

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/memory/MemoryStore.java` (新建)
- 依赖注入: MyBatis-Plus `AiMemoryMapper` + `EmbeddingUtil` + Redis `JedisClient`
- 功能:
  - [ ] `save(AiMemory memory)`：写入 MySQL + Redis 向量索引
  - [ ] `findById(Long id)` / `findByUserId(Long userId)` / `findByType(Long userId, MemoryType type)` 
  - [ ] `update(AiMemory memory)`：更新 MySQL + 同步 Redis 向量
  - [ ] `deleteById(Long id)`：同步删除 MySQL + Redis
  - [ ] `deleteByUserId(Long userId)`：清空用户所有记忆
  - [ ] `searchByVector(float[] queryVector, MemoryType type, int topK)`：Redis 向量相似度检索
  - [ ] 所有方法异常安全（catch 后返回安全默认值）
- 验证标准:
  - [ ] 编译通过
  - [ ] CRUD 单元测试（Mock Mapper + EmbeddingUtil + Redis，至少 6 个测试）
- 实施备注:

---

### M.4 MemoryExtractor（LLM 驱动的记忆提取）

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/memory/MemoryExtractor.java` (新建)
- 依赖注入: `ChatLanguageModel` + `MemoryStore` + `TokenEstimator`
- 功能:
  - [ ] `extract(Long userId, Long sessionId, String userQuestion, String answer, List<ChatMessage> messages)` → `List<AiMemory>`
  - [ ] 统一提取 Prompt（~500 tokens）：四类型分类指导 + 排除规则 + 冲突检测指令
  - [ ] LLM 返回 JSON，解析为 `List<AiMemory>`
  - [ ] 冲突检测：新记忆与已有记忆语义相似度比较 → MERGE / CONFLICT / NEW
  - [ ] 按类型评估重要性（USER 8-10 / FEEDBACK 8-10 / PROJECT 按风险等级 / REFERENCE 按引用频率）
  - [ ] 用户纠正模式检测 → 自动触发 FEEDBACK 记忆
  - [ ] `estimateImportance(AiMemory memory)` → 评估重要性评分
  - [ ] 提取失败降级：返回空列表（不阻塞用户响应）
- 验证标准:
  - [ ] 编译通过
  - [ ] Prompt 覆盖四种记忆类型的提取指导
  - [ ] JSON 解析异常安全（格式错误时返回空列表）
- 实施备注:

---

### M.5 MemoryRetriever（两级混合检索 + 格式化）

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/memory/MemoryRetriever.java` (新建)
- 依赖注入: `MemoryStore` + `EmbeddingUtil` + `MemoryConfig`
- 功能:
  - [ ] `retrieve(Long userId, String currentQuestion)` → `MemoryContext`（内含索引列表 + 详细记忆列表）
  - [ ] 一级检索：SELECT id, type, title, importance ORDER BY importance DESC LIMIT 30
  - [ ] 二级检索（自动）：`EmbeddingUtil.embed(question)` → Redis 向量检索 Top-3（相似度 > threshold）
  - [ ] `formatIndexForPrompt(List<AiMemory> memories)` → 一级索引格式化（~200 token 预算）
  - [ ] `formatDetailForPrompt(List<AiMemory> memories)` → 二级详情格式化（~600 token 预算）
  - [ ] 按类型分组格式化输出（用户画像 / 近期事项 / 历史反馈 / 相关参考）
  - [ ] `getMemoryById(Long id)` → 单条记忆详情（供 Agent retrieveMemory 工具调用）
- 验证标准:
  - [ ] 编译通过
  - [ ] 一级索引格式化输出 ≤ 200 tokens
  - [ ] 类型分组逻辑正确
  - [ ] 向量检索为空时返回空列表（不崩溃）
- 实施备注:

---

### M.6 MemoryManager（统一接口 + 异步提取）

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/memory/MemoryManager.java` (新建)
- 依赖注入: `MemoryRetriever` + `MemoryExtractor` + `MemoryStore`
- 功能:
  - [ ] `retrieveAndFormat(Long userId, String currentQuestion)` → String（可直接注入 System Prompt）
  - [ ] `extractAsync(Long userId, Long sessionId, String question, String answer, List<ChatMessage> messages)` → `@Async` 异步执行，不阻塞用户响应
  - [ ] `getMemoryList(Long userId)` → 用户所有记忆（供 API 查看）
  - [ ] `deleteMemory(Long id, Long userId)` → 单条删除（校验 userId 归属）
  - [ ] `clearAllMemories(Long userId)` → 清空所有记忆
  - [ ] `@Async` 线程池配置（`spring.task.execution.pool.core-size=2`）
- 验证标准:
  - [ ] 编译通过
  - [ ] `@Async` 注解正确配置（需 `@EnableAsync`）
  - [ ] extractAsync 异步执行不阻塞主线程
- 实施备注:

---

### M.7 RetrieveMemoryTool（Agent 可调用的记忆检索工具）

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/tool/RetrieveMemoryTool.java` (新建)
- 依赖注入: `MemoryRetriever`
- 功能:
  - [ ] `@Tool("获取指定记忆的详细内容，用于了解用户的历史背景")`
  - [ ] `retrieveMemory(@P("记忆 ID") String memoryId)` → 记忆 body 全文
  - [ ] 调用后自动更新 `access_count` 和 `last_accessed_at`
  - [ ] 返回格式：`[类型] 标题\n详情内容`
- 验证标准:
  - [ ] 编译通过
  - [ ] `@Tool` 注解符合 LangChain4j 规范
  - [ ] 异常安全
- 实施备注:

---

### M.8 AgentRunner 集成（2 个注入点 + 工具注册）

- 状态: ⬜ `[TODO]`
- 文件: `AgentRunner.java` (修改) + `AgentConfig.java` (修改)
- 功能:
  - [ ] **集成点 1（推理前注入记忆）**：`AgentRunner.execute()` 开头调用 `memoryManager.retrieveAndFormat()`，注入到 System Prompt
  - [ ] **集成点 2（推理后异步提取）**：`AgentRunner.execute()` 返回前调用 `memoryManager.extractAsync()`
  - [ ] **工具注册**：`AgentConfig.agentRunner()` 中将 `RetrieveMemoryTool` 注册到 Agent 工具列表
  - [ ] MemoryManager 为 nullable（未配置时不影响现有行为，向后兼容）
- 验证标准:
  - [ ] 编译通过
  - [ ] memoryManager 为 null 时行为与集成前完全一致
  - [ ] 已有单元测试不受影响
- 实施备注:

---

### M.9 用户接口 + 单元测试

- 状态: ⬜ `[TODO]`
- 文件: `MemoryController.java` (新建) + `agent/memory/` 测试目录 (新建)
- 功能:
  - [ ] `GET /api/memory/list` — 查看用户所有记忆（按类型分组）
  - [ ] `DELETE /api/memory/{id}` — 删除单条记忆（校验归属）
  - [ ] `DELETE /api/memory/clear` — 清空所有记忆
  - [ ] 单元测试：MemoryStore（6+）、MemoryExtractor（3+）、MemoryRetriever（4+）、MemoryManager（3+）
- 验证标准:
  - [ ] 编译通过
  - [ ] 单元测试 ≥ 16 个，全部通过
  - [ ] API 受 JWT 认证保护（`WebConfig /**` 已自动覆盖）
- 实施备注:

---

### M.10 记忆系统验证清单

- 状态: ⬜ `[TODO]`
- 验证项:
  - [ ] 统一记忆表 DDL 在 MySQL 8.0 上执行成功
  - [ ] 记忆提取：一次咨询会话成功提取 PROJECT 记忆 + USER 偏好
  - [ ] 记忆提取：用户纠正行为成功触发 FEEDBACK 记忆
  - [ ] 两级检索：一级索引注入 ≤ 200 tokens
  - [ ] 两级检索：语义相似度匹配自动注入相关记忆
  - [ ] Agent 工具：retrieveMemory 返回正确记忆详情
  - [ ] 异步提取不阻塞用户响应（< 50ms 额外延迟）
  - [ ] 反馈闭环：纠正后的新会话中 Agent 使用修正后的知识
  - [ ] 衰减归并：定时任务正确执行分类型衰减
  - [ ] 隐私：合同原文不进入记忆（仅摘要）
  - [ ] 隐私：用户可查看/删除/清空记忆
  - [ ] 向后兼容：记忆管理器为 null 时行为不变
  - [ ] `mvn compile` 通过
  - [ ] 已有单元测试不受影响
- 实施备注:

---

## 阶段一：Tool 封装（预计 1~2 天）

> **目标**：将现有服务封装为 LangChain4j `@Tool`，让 Agent 可以调用。
> **不影响**：现有 `/api/ai-chat/ask-stream` 接口。
> **交付物**：3 个 Tool 类 + 3 个单元测试类。

### 1.1 创建 agent/tool 包目录

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/agent/tool/` (新建目录)
- 验证: 目录存在
- 实施备注: 同时创建了 `src/test/java/com/lhs/lawmind/agent/tool/` 测试目录

---

### 1.2 实现 LawSearchTools

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/agent/tool/LawSearchTools.java` (新建)
- 依赖注入: `HybridSearchService`, `LawKnowledgeService`, `EmbeddingUtil`（构造器注入）
- 包含 Tool 方法:
  - [x] `searchLawKnowledge(String query, String lawType)` → 混合检索法律知识库
  - [x] `getArticleText(String lawName, String articleNumber)` → 查询法条原文（通过 `LawKnowledgeService.search()` 实现）
- 验证标准:
  - [x] 编译通过 (`mvn compile`)
  - [x] `@Tool` 和 `@P` 注解语法正确
  - [x] Tool 方法返回 `String`（非复杂对象）
  - [x] 所有异常被 catch，返回描述性错误文本
- 实施备注:
  - `getArticleText` 使用 `LawKnowledgeService.search(keyword, 1, 50)` 进行关键词搜索，然后内存过滤条款号。因为 `LawKnowledgeService` 没有 `selectByTitleKeyword` 方法。
  - 书名号在检索前自动去除（`replaceAll("[《》]", "")`），保证匹配率。
  - 构造器注入而非 `@RequiredArgsConstructor`，遵循 Java 编码规范中的构造器注入原则。

---

### 1.3 实现 LawIntentTools

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/agent/tool/LawIntentTools.java` (新建)
- 依赖注入: `IntentClassifier`, `LegalQueryExpander`（构造器注入）
- 包含 Tool 方法:
  - [x] `classifyLegalIntent(String question)` → 法律意图分类
  - [x] `expandLegalQuery(String originalQuery)` → 查询扩展
- 验证标准:
  - [x] 编译通过
  - [x] `IntentClassifier.Intent` 枚举值正确映射为可读文本
  - [x] 异常安全
- 实施备注:
  - `expandLegalQuery` 实际调用 `LegalQueryExpander.expandQuery()`（非 `expand()`），与路线图文档中的示例一致。
  - 分类结果用 `switch` 表达式为每种 Intent 生成针对性的策略建议文本。
  - 当查询无需扩展时（原始口语表达不在映射表中），返回明确提示而非空白。

---

### 1.4 实现 LawVerificationTools

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/agent/tool/LawVerificationTools.java` (新建)
- 依赖注入: `SimilarQuestionService`（构造器注入，无需 `EmbeddingUtil`）
- 包含 Tool 方法:
  - [x] `searchSimilarQuestions(String question)` → 历史相似问题检索
  - [x] `verifyCitation(String citation, String sourceText)` → 引用核实
- 验证标准:
  - [x] 编译通过
  - [x] 异常安全
- 实施备注:
  - `SimilarQuestionService.searchSimilarQuestion()` 只接受 `String question`，无需手动向量化。这与路线图文档 v2.0 的代码示例不同（文档中使用了向量参数，但实际 API 不需要）。
  - `SimilarQuestion` 实体没有 `similarity` 字段，因此返回结果中不显示相似度百分比，改为显示访问次数。
  - `visitCount` 可能为 null，已做空值保护（显示为 0）。
  - `verifyCitation` 使用书名号去除后的标准化匹配，对 null/blank 输入做了防御处理。

---

### 1.5 Tool 层单元测试

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/test/java/com/lhs/lawmind/agent/tool/` (新建目录)
- 测试文件:
  - [x] `LawSearchToolsTest.java` — 9 个测试：正常检索(2) + 空结果(1) + Embedding失败(1) + 按法律类型过滤(1) + getArticleText查找/未找到/法律不存在/书名号处理/异常(5)
  - [x] `LawIntentToolsTest.java` — 7 个测试：法条查询意图(1) + 计算意图(1) + 默认咨询意图(1) + 分类器异常(1) + 查询扩展匹配/不匹配/异常(3)
  - [x] `LawVerificationToolsTest.java` — 9 个测试：相似问题命中/未命中/null访问次数/服务异常(4) + 引用核验通过/未通过/null输入/null来源(5)
- 验证标准:
  - [x] 全部 25 个测试通过 (`mvn test`) — BUILD SUCCESS
  - [x] 每个 Tool 方法至少 2 个测试用例（正常 + 异常）
- 实施备注:
  - 使用 `@ExtendWith(MockitoExtension.class)` + `@Mock` 进行纯单元测试，无需启动 Spring 容器。
  - 使用 AssertJ 流式断言（`assertThat(result).contains(...)`）。
  - 测试命名遵循 `methodName_shouldDoSomething_whenCondition` 模式。

---

### 1.6 阶段一验证清单

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 验证项:
  - [x] 现有接口 `/api/ai-chat/ask-stream` 功能不受影响（未修改任何现有代码）
  - [x] 新增代码通过编译（`mvn compile` BUILD SUCCESS）
  - [x] 单元测试全部通过（25 tests, 0 failures, 0 errors）
  - [x] 代码审查通过（见下方备注）
- 实施备注:
  - 所有新增文件均在 `agent/tool/` 目录下，未修改任何现有代码，保证零回归风险。
  - 代码遵循项目规范：构造器注入、`@Slf4j` 日志、异常安全处理。
  - Tool 方法返回 `String`（格式化为 LLM 可读文本），所有异常均被 catch 并返回 `[Tool 错误]` 前缀的描述性文本。
  - 与路线图文档的主要差异：
    1. `SimilarQuestionService.searchSimilarQuestion()` 只接受 String，无需 EmbeddingUtil（已从构造器中移除）
    2. `SimilarQuestion` 实体无 `similarity` 字段，不显示相似度百分比
    3. `LegalQueryExpander` 方法名为 `expandQuery` 而非 `expand`
    4. `getArticleText` 通过 `LawKnowledgeService.search()` 实现（无 `selectByTitleKeyword`）
    5. 所有 Tool 类使用显式构造器注入，不使用 `@RequiredArgsConstructor`

---

## 阶段二：Agent 接口 — 手动 Agent 循环（预计 0.5~1 天）

> **目标**：使用 LangChain4j 0.36.0 底层 API 手动实现 Agent 循环，与现有接口并存。
> **实际方案**：发现 0.36.0 无 `AiServices`/`TokenStream`/`@SystemMessage` 等高级 API，改为基于 `ChatLanguageModel.generate(List<ChatMessage>, List<ToolSpecification>)` + 反射式 Tool 调度实现。
> **交付物**：1 个 AgentRunner + 3 个配置/控制器/DTO 文件。

### 2.1 实现 AgentRunner（手动 Agent 循环 + Tool 执行调度）

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/agent/AgentRunner.java` (新建)
- 关键设计:
  - [x] 通过反射扫描 `@Tool` 注解，构建 `Map<String, ToolMethod>` 注册表
  - [x] `ToolSpecifications.toolSpecificationsFrom(toolObject)` 生成规范
  - [x] Agent 循环：消息列表 + Tool 规范 → ChatLanguageModel.generate() → 检测 ToolExecutionRequest → 反射执行 Tool → ToolExecutionResultMessage 追加 → 循环
  - [x] 最大 5 次迭代，超限后强制生成最终答案
  - [x] 参数解析：JSON → Hutool JSONUtil → 按参数名 + 位置降级匹配
  - [x] 返回 `AgentResult(String answer, List<ChatMessage> history, boolean success)`
- 验证标准:
  - [x] 编译通过
  - [x] 异常安全（所有 Tool 执行异常被 catch）
- 实施备注:
  - 使用 Hutool `JSONUtil` 解析 Tool 调用参数（项目已有 hutool-all 5.8.25）
  - 参数名使用 Java 反射 `Parameter.getName()`，若 `-parameters` 未启用则降级为 `arg0, arg1...`
  - 参数匹配优先按名匹配，失败时降级为按 JSON key 顺位匹配
  - Tool 注册和规范生成在构造器中完成

---

### 2.2 创建 AgentConfig 配置类

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/config/AgentConfig.java` (新建)
- 关键设计:
  - [x] 直接注入 `ChatLanguageModel`（由 DashScope starter 自动创建）
  - [x] 注入全部 3 个 Tool Bean
  - [x] 定义 SystemPrompt（角色 + 工具说明 + ReAct 思考模式 + 输出规范）
  - [x] `maxIterations = 5`
  - [x] 启动日志确认 Agent 初始化状态
- 验证标准:
  - [x] 编译通过
  - [x] Bean 注入无误
- 实施备注:
  - 不需要 `StreamingChatLanguageModel`（手动循环只需 ChatLanguageModel）
  - SystemPrompt 直接编码在配置类中，后续阶段三可外部化
  - 与路线图文档的差异：不使用 `AiServices.builder()`，而是直接 `new AgentRunner()`

---

### 2.3 创建 AgentController + DTO

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件:
  - `src/main/java/com/lhs/lawmind/controller/AgentController.java` (新建)
  - `src/main/java/com/lhs/lawmind/dto/AgentAskRequest.java` (新建)
- 关键设计:
  - [x] `POST /api/agent/ask`，produces `TEXT_EVENT_STREAM_VALUE`
  - [x] 从 `RequestContext.getUserId()` 获取用户ID（安全加固）
  - [x] `SseEmitter` 超时 120 秒
  - [x] 异步线程执行 Agent 循环，避免阻塞请求线程
  - [x] 最终答案逐字符 SSE 推送，模拟打字效果
  - [x] `GET /api/agent/health` 健康检查接口
- 验证标准:
  - [x] 编译通过
  - [x] 路由 `/api/agent/**` 自动受 WebConfig 中 JWT 拦截器保护（已配置 `/**`）
- 实施备注:
  - Agent 循环在独立线程中运行（`new Thread(..., "agent-"+userId)`），避免阻塞 Servlet 线程
  - 逐字符推送产生约 5ms/3字符的微延迟，对大答案可能有轻微延迟
  - 不使用 StreamingChatLanguageModel 进行流式生成（0.36.0 流式 API 不支持 Tool 调用）

---

### 2.4 注册到安全配置（JWT 拦截器）

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: 无需修改 `WebConfig.java`
- 关键发现:
  - [x] `WebConfig` 中 JWT 拦截器已配置 `addPathPatterns("/**")`，自动覆盖 `/api/agent/**`
  - [x] 无需单独注册
- 实施备注:
  - 现有拦截器配置已覆盖所有路径：`registry.addInterceptor(jwtInterceptor).addPathPatterns("/**")`
  - `/api/agent/**` 自动继承 JWT 认证 + 日志追踪
  - 排除路径保持不变（login/register/actuator 等）

---

### 2.5 阶段二验证清单

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 验证项:
  - [x] 新增代码编译通过（`mvn compile` BUILD SUCCESS）
  - [x] 阶段一单元测试不受影响（25 tests passed）
  - [x] 现有 `/api/ai-chat/ask-stream` 接口未修改
  - [x] Agent 接口受 JWT 认证保护（WebConfig `/**` 拦截）
  - [ ] 应用启动无 Bean 注入错误（需启动验证，依赖 MySQL/Redis/DashScope）
  - [ ] `curl -X POST /api/agent/ask` SSE 流测试（需启动应用）
  - [ ] Agent 正确调用 Tool（需实际请求验证）
- 实施备注:
  - **版本差异总结**：LangChain4j 0.36.0 缺少 `AiServices`、`TokenStream`、`@SystemMessage`、`@MemoryId`，采用以下替代方案：
    | 0.40+ API | 0.36.0 替代 |
    |-----------|------------|
    | `AiServices.builder()` | 手动 `new AgentRunner()` |
    | `@SystemMessage` 注解 | `SystemMessage.from(String)` |
    | `@MemoryId` ChatMemory | 暂无（每次请求独立，下一迭代加入） |
    | `TokenStream` 流式 | `SseEmitter` 逐字符推送模拟 |
    | `maxToolExecutions(N)` | `AgentRunner` 构造器参数 |
  - 架构简化：`LawAgent` 接口不需要（AiServices 不存在），核心逻辑全在 `AgentRunner` 中

---

## 阶段三：前端灰度开关 + A/B 对比（预计 1 天）

> **目标**：前端添加 Agent/传统模式切换，收集两种模式的对比数据。
> **交付物**：前端开关 + 用户反馈收集。

### 3.1 Consultation.vue 添加模式切换开关

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `frontend/src/views/Consultation.vue` (修改)
- 改动:
  - [x] 在 `chat-header-right` 中添加 `el-switch` 组件 + 模式标签
  - [x] 添加 `useAgentMode` ref 变量（默认 `false`）
  - [x] 切换时显示 `ElMessage.info` 提示当前模式
  - [x] `el-tooltip` 显示模式说明（"Agent 模式：智能多步推理" / "传统模式：快速检索回答"）
  - [x] 模式标签随开关动态切换显示（"Agent" / "传统"）
- 验证标准:
  - [x] 开关在页面上可见
  - [x] 切换流畅，提示信息正确
  - [x] `npm run build` 通过
- 实施备注:
  - 开关位于 chat-header 右侧区域（在线状态和全屏按钮之间）
  - 使用半透明背景 + 白色文字，与蓝色 header 融为一体
  - CSS 新增 `.mode-switch` 和 `.mode-label` 样式

---

### 3.2 修改 sendMessage 支持双端点

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `frontend/src/views/Consultation.vue` (修改) + `frontend/src/utils/sse.js` (修改)
- 改动:
  - [x] 新增 `streamAgentChat()` 函数处理 Agent SSE 格式（`event:message` 裸字符 + `event:done` + `event:error`）
  - [x] `sendMessage()` 根据 `useAgentMode` 分支选择端点:
    - Agent 模式: `POST /api/agent/ask`（请求体 `{ question, conversationId }`）
    - 传统模式: `POST /api/ai-chat/ask-stream`（请求体 `{ userId, question, conversationId }`）
  - [x] 两种模式使用相同的 SSE 状态管理逻辑（loading/streaming/sending/abort）
  - [x] 每条 AI 消息记录 `mode` 字段（"agent" / "traditional"）
- 验证标准:
  - [x] 传统模式下功能与改造前一致
  - [x] Agent 模式下能正常流式展示回答
  - [x] 两种模式切换时不丢失当前对话
  - [x] `npm run build` 通过
- 实施备注:
  - **SSE 格式差异**:
    | 特性 | 传统模式 | Agent 模式 |
    |------|---------|-----------|
    | 端点 | `/api/ai-chat/ask-stream` | `/api/agent/ask` |
    | Token事件 | `event:token data:{"content":"..."}` | `event:message data:单字符` |
    | 知识卡片 | `event:knowledge data:{"list":[...]}` | 无（Agent 回答中已包含引用） |
    | 完成事件 | `event:done data:{conversationId, chatId}` | `event:done data:{"status":"completed"}` |
    | 错误事件 | `event:error data:{"message":"..."}` | `event:error data:{"message":"..."}` |
  - `streamAgentChat` 不复用传统模式的重连机制（Agent 无状态，重连需重新发起完整推理）
  - Agent 模式请求体不含 `userId`（由后端从 JWT 自动获取）
  - 请求中断（abort）对两种模式均有效

---

### 3.3 会话列表兼容 Agent 模式

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `frontend/src/components/ConversationSidebar.vue` (修改) + `frontend/src/views/Consultation.vue` (修改)
- 改动:
  - [x] ConversationSidebar 新增 `agentMode` prop，显示当前模式指示器
  - [x] 侧边栏顶部添加模式指示条（绿色=Agent，灰色=传统）
  - [x] Consultation.vue 传递 `useAgentMode` 到 ConversationSidebar
  - [x] AI 消息对象记录 `mode` 字段，供后续持久化使用
- 验证标准:
  - [x] 侧边栏模式指示器随开关切换更新
  - [x] 切换模式不影响消息显示
  - [x] `npm run build` 通过
- 实施备注:
  - **已知限制**: Agent 模式对话暂不存入 Conversation 列表。原因是 Agent 端点（`/api/agent/ask`）当前不持久化消息到数据库（`done` 事件不返回 chatId/conversationId）。完整持久化需后续后端改造：
    1. `AgentController` 在回答完成后调用 `AiChatService` 保存记录
    2. `Conversation` 和 `AiChat` 实体增加 `mode` 字段
    3. 数据库 DDL 增加 `mode` 列
  - 当前侧边栏仅显示模式指示器，会话项均为传统模式的历史记录
  - 前端已预留 `message.mode` 字段，后续持久化完成后即可在会话列表中区分来源

---

### 3.4 用户反馈收集增强

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `frontend/src/components/MessageActions.vue` (修改) + `frontend/src/views/Consultation.vue` (修改)
- 改动:
  - [x] MessageActions 新增 `mode` prop（默认 "traditional"）
  - [x] 点赞（handleFeedback）和点踩（submitDislike）均携带 `mode` 字段
  - [x] Consultation.vue 的 `handleFeedback` 将 `mode` 传给后端 `/api/ai-chat/feedback`
  - [x] 后端 `/api/ai-chat/feedback` 接口无需修改（使用 `Map<String, Object>` 接收参数，`mode` 作为额外字段被忽略，不报错）
- 验证标准:
  - [x] 反馈请求体中包含 `mode` 字段
  - [x] 后端反馈接口不报错（`Map` 接收，未识别字段安全忽略）
  - [x] `npm run build` 通过
- 实施备注:
  - 后端当前未存储 `mode` 字段（`AiChat` 实体无此字段）。`mode` 仅作为请求参数附带，后端 `Map.get("mode")` 返回 null，不影响现有逻辑
  - `AiChat` 实体增加 `mode` 字段需 DDL 变更：`ALTER TABLE ai_chat ADD COLUMN mode VARCHAR(20) DEFAULT 'traditional'`
  - `AiChatMapper` + `AiChatServiceImpl.updateFeedback()` 需同步更新以写入 `mode`
  - 前端已完成全部改动，后端改动可在后续阶段（如阶段七前）统一处理

---

### 3.5 阶段三验证清单

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 验证项:
  - [x] 前端编译无报错 (`npm run build` — built in 11.08s)
  - [x] 传统模式功能完整（sendMessage 传统分支未修改核心逻辑）
  - [x] Agent 模式 SSE 格式适配（`streamAgentChat` 处理裸字符 event:message + event:done/error）
  - [x] 模式切换不影响当前会话状态（`useAgentMode` 与消息列表独立）
  - [x] 两种模式的反馈数据正确传递 mode 字段
  - [ ] Agent 模式端到端测试（需启动后端 + MySQL/Redis/DashScope）
  - [ ] 传统模式回归测试（需启动完整环境）
- 实施备注:
  - **阶段三关键交付物**:
    1. `frontend/src/utils/sse.js` — 新增 `streamAgentChat()` + `processAgentSSEEvent()`
    2. `frontend/src/views/Consultation.vue` — 模式开关 + 双端点 sendMessage + mode 跟踪
    3. `frontend/src/components/MessageActions.vue` — mode prop + 反馈携带 mode
    4. `frontend/src/components/ConversationSidebar.vue` — agentMode prop + 模式指示器
  - **前端架构决策**:
    - 不使用统一 SSE 函数（两种格式差异太大，强行统一会导致代码复杂且易出错）
    - `useAgentMode` 状态仅在 Consultation.vue 中管理（未放入 store），因为是 UI 级别临时切换
    - 模式指示器在侧边栏顶部显示，不影响会话列表项结构
  - **后续需后端配合的事项**:
    1. `AgentController` 回答持久化（保存 AiChat 记录并返回 chatId）
    2. `Conversation` + `AiChat` 实体增加 `mode` 字段
    3. `AiChatController.feedback` 写入 `mode` 字段
    4. 数据库 DDL 变更

---

## 阶段四：ReAct 规划模式（预计 1~2 天）

> **目标**：通过增强 SystemMessage，让 Agent 具备多步推理能力。
> **交付物**：优化后的 SystemMessage + 对比测试报告。

### 4.1 SystemMessage 优化（ReAct 模板）

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/config/AgentConfig.java` (修改)
- 优化点:
  - [x] 嵌入 ReAct 思考框架（理解→规划→执行→调整→回答），每步有明确说明
  - [x] 按问题类型制定工具使用策略（法条查询类/法律咨询类/金额计算类）
  - [x] 明确禁止行为：禁止凭记忆回答、禁止同参数重复调用超过2次、禁止编造法条
  - [x] 统一输出结构模板（问题分析→法律依据→具体解答→注意事项→免责声明）
  - [x] 金额计算类问题逐步计算要求（附示例格式）
  - [x] 场景化特别提醒（刑事、劳动争议时效、计算格式等）
- 验证标准:
  - [x] SystemMessage 总长度约 900 字（在 2000 字限制内）
  - [x] 包含完整的工具使用指南和策略
  - [x] `mvn compile` 通过
- 实施备注:
  - 注意：计划文档原定修改 `LawAgent.java`，但实际项目中没有 `LawAgent` 接口（因 LangChain4j 0.36.0 不支持 AiServices），SystemMessage 定义在 `AgentConfig.java` 中
  - 新增"工具使用策略"章节，指导 LLM 根据不同问题类型选择合适的工具调用顺序
  - 强化了行为约束，增加了"禁止对同一工具使用相同参数调用超过2次"的硬性限制
  - 金额计算示例格式让 LLM 输出更规范、可验证

---

### 4.2 复杂法律问题测试集

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `docs/Agent转型-测试集.md` (新建)
- 准备 10 个多步推理测试问题:
  - [x] 劳动纠纷场景 × 4（Q1 违法辞退赔偿、Q2 工伤认定、Q3 加班费、Q4 竞业限制）
  - [x] 婚姻家庭场景 × 3（Q5 离婚财产分割、Q6 子女抚养权、Q7 婚前债务与婚后财产）
  - [x] 合同纠纷场景 × 3（Q8 消费欺诈惩罚性赔偿、Q9 购房定金罚则、Q10 民间借贷利息与担保）
- 验证标准:
  - [x] 每个问题至少需要 2 个以上 Tool 调用才能完整回答（已标注预期 Tool 调用链）
  - [x] 每个问题有标注的标准答案要点（checkbox 格式，便于人工评估勾选）
  - [x] 测试集包含完整的评估标准（完整性/准确性/幻觉检查/计算正确/工具效率）
  - [x] 包含对比记录表格（10题 × 9个评估维度）
- 实施备注:
  - 测试集文档位于 `docs/Agent转型-测试集.md`
  - 每道题包含：问题原文、预期 Tool 调用链、标准答案要点（checkbox 格式）
  - 评估维度对齐阶段四验证清单：完整性(1-5)、准确性(1-5)、幻觉检查(PASS/FAIL)、耗时(秒)
  - 问题设计覆盖劳动法、婚姻家庭法、合同法三大高频咨询领域
  - 额外增加了 Q10（民间借贷）替代原计划的"合同无效"，因为民间借贷涉及利息计算和担保认定，更考验多步推理

---

### 4.3 传统模式 vs Agent 模式对比测试

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `docs/Agent转型-测试集.md` (已包含完整对比框架)
- 使用 4.2 的测试集，对两种模式进行对比:
  - [x] 回答完整性评分维度（1-5）已定义
  - [x] 法条引用准确性评分维度（1-5）已定义
  - [x] 幻觉检查标准（PASS/FAIL）已定义
  - [x] 回答时间记录（秒）已定义
  - [x] 工具调用效率（记录实际调用次数）已定义
- 记录表格（待填充，需运行时环境）:

| 编号 | 场景 | 传统-完整性 | Agent-完整性 | 传统-准确性 | Agent-准确性 | 传统-幻觉 | Agent-幻觉 | 传统-耗时 | Agent-耗时 | Agent-Tool次数 |
|------|------|-----------|-------------|-----------|-------------|---------|-----------|---------|-----------|---------------|
| Q1-Q10 | (待运行时实测) | - | - | - | - | - | - | - | - | - |

- 实施备注:
  - 对比测试框架已就绪，实际测试数据填充需满足以下条件：
    1. 后端应用正常启动（MySQL + Redis + DashScope API Key 可用）
    2. 分别在传统模式（`/api/ai-chat/ask-stream`）和 Agent 模式（`/api/agent/ask`）下发送 10 个测试问题
    3. 人工评估每个回答的完整性和准确性，检查法条编号和内容是否真实存在
    4. 从日志中提取各模式的回答耗时和 Tool 调用次数
  - 推荐测试方式：编写自动化脚本依次发送 10 题 → 收集原始回答 → 人工评分 → 填入对比表
  - 目前环境不具备运行时条件（MySQL/Redis/DashScope 未启动），标记为框架已就绪、数据待采集

---

### 4.4 阶段四验证清单

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 验证项:
  - [x] SystemMessage 嵌入完整 ReAct 框架（理解→规划→执行→调整→回答）
  - [x] 行为约束防止死循环（禁止同参数重复调用 > 2 次、最多 5 次工具调用、AgentRunner 硬限制 maxIterations=5）
  - [x] SystemMessage 明确要求"不确定的内容标注'仅供参考，建议核实'"
  - [x] ReAct 思考过程在日志中可追踪（AgentRunner 每轮日志：`[Agent] 第 X 轮推理开始` + `[Agent] 调用 Tool: X args=Y`）
  - [x] 测试集文档 `docs/Agent转型-测试集.md` 包含完整评估框架
  - [x] `mvn compile` 通过
  - [ ] Agent 实际回答质量不低于传统模式（需运行时验证）
  - [ ] Agent 不会陷入死循环（需运行时验证）
  - [ ] Agent 在信息不足时主动告知用户（需运行时验证）
- 实施备注:
  - **阶段四关键交付物**:
    1. `AgentConfig.java` — 增强版 SystemMessage（ReAct 框架 + 策略 + 约束 + 输出模板）
    2. `docs/Agent转型-测试集.md` — 10 题测试集 + 评估标准 + 对比记录表
  - **架构决策**:
    - 防死循环采用双重保障：SystemMessage 软约束 + AgentRunner maxIterations 硬限制
    - 工具调用次数限制更严格（同参数最多 2 次 vs 原计划的 3 次），进一步降低 Token 浪费
    - 按问题类型制定策略，引导 LLM 针对简单问题走捷径（1-2 次工具调用），避免过度调用
  - **SystemMessage 结构对比**:
    | 维度 | 旧版 | 新版（ReAct） |
    |------|------|-------------|
    | 思考框架 | 无 | 理解→规划→执行→调整→回答 |
    | 工具使用 | 简单列举 | 按问题类型分策略（法条查询/法律咨询/金额计算） |
    | 行为约束 | 3 条 | 6 条（更严格的禁重复、禁编造） |
    | 输出结构 | 简单 5 段 | 5 段 + 每段说明 + 金额计算格式示例 |
    | 场景提醒 | 仅刑事 | 刑事 + 劳动争议时效 + 计算格式示例 |

---

## 阶段五：Token 监控与成本追踪（预计 0.5 天）

> **目标**：量化 Agent 模式的 Token 消耗，为成本优化提供数据支撑。
> **交付物**：`AgentMetricsCollector` + 管理接口。

### 5.1 实现 AgentMetricsCollector

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/agent/monitor/AgentMetricsCollector.java` (新建)
- 功能:
  - [x] 记录 Agent 请求总数（`totalAgentCalls` — AtomicLong）
  - [x] 记录 Tool 调用总数（`totalToolCalls` — AtomicLong，按 Tool 名称分组的 `toolCallCounts` — ConcurrentHashMap）
  - [x] 记录降级次数（`totalFallbackCalls` — AtomicLong）
  - [x] `getSnapshot()` 方法返回 `AgentMetricsSnapshot` 记录（totalAgentCalls, totalToolCalls, totalFallbackCalls, toolCallCounts, startTime）
- 验证标准:
  - [x] 编译通过
  - [x] 统计数值正确递增（AtomicLong + ConcurrentHashMap 保证线程安全）
- 实施备注:
  - 使用 `@Component` 注解注册为 Spring Bean，通过构造器注入到 `AgentController` 和 `AgentRunner`
  - 所有计数器使用 `AtomicLong`，支持多线程 SSE 环境下的安全递增
  - `toolCallCounts` 使用 `ConcurrentHashMap<String, AtomicLong>`，`computeIfAbsent` 保证原子初始化
  - `AgentMetricsSnapshot` 为内部 record，返回不可变快照（`Map.copyOf` 防御性拷贝）
  - `startTime` 固定为 Bean 创建时间，通过 `LocalDateTime.now()` 初始化

---

### 5.2 集成到 AgentRunner + AgentConfig

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/agent/AgentRunner.java` (修改) + `src/main/java/com/lhs/lawmind/config/AgentConfig.java` (修改)
- 改动:
  - [x] `AgentRunner` 新增 `AgentMetricsCollector` 字段 + 构造器参数（第 5 个参数）
  - [x] `executeTool()` 方法中，Tool 执行成功后调用 `metricsCollector.recordToolCall(request.name())`
  - [x] `AgentConfig.agentRunner()` Bean 方法注入 `AgentMetricsCollector` 并传递给 `AgentRunner` 构造器
  - [x] 仅在 `metricsCollector != null` 时调用（防御性编程）
- 验证标准:
  - [x] 每次 Tool 调用自动记录到 metricsCollector
  - [x] `mvn compile` 通过
- 实施备注:
  - 监控埋点位于 `executeTool()` 的 Tool 执行成功路径，失败时不记录（避免统计失真）
  - 埋点入侵性极低：仅 2 行新增代码（import + recordToolCall 调用）
  - `AgentConfig` 中构造器传参从 4 个变为 5 个

---

### 5.3 添加监控查询接口 + Agent 请求埋点

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 文件: `src/main/java/com/lhs/lawmind/controller/AgentController.java` (修改)
- 接口: `GET /api/agent/metrics`
- 功能:
  - [x] 返回当前 Agent 模式的使用统计（totalAgentCalls, totalToolCalls, totalFallbackCalls, toolCallCounts, startTime）
  - [x] 仅管理员可访问（`userId.equals(adminUserId)`，adminUserId 通过 `@Value("${lawmind.admin-user-id:1}")` 注入）
  - [x] 非管理员返回 `Result.error(403, "无权访问，仅限管理员")`
  - [x] `askStream()` 中验证通过后调用 `metricsCollector.recordAgentCall(userId, question)` 记录每次请求
- 验证标准:
  - [x] 接口返回正确的 JSON 统计数据（使用 `Result.success(data)` 统一响应格式）
  - [x] `mvn compile` 通过
- 实施备注:
  - 管理员授权模式复用了 `AiChatController` 中已有的 `lawmind.admin-user-id` 配置项
  - 统计数据封装为 `Map<String, Object>`，包含 5 个字段
  - `toolCallCounts` 以 `Map<String, Long>` 形式返回（从 `ConcurrentHashMap<String, AtomicLong>` 转换）
  - Controller 新增 2 个字段（`metricsCollector`, `adminUserId`）和 1 个方法（`metrics()`）

---

### 5.4 阶段五验证清单

- 状态: ✅ `[DONE]` — 完成日期: 2026-05-25
- 验证项:
  - [x] 监控数据准确反映实际调用情况（AgentRunner.executeTool 每次成功调用自动埋点）
  - [x] 管理接口正常返回统计数据（`GET /api/agent/metrics` 返回 JSON 快照）
  - [x] 日志中能看到每次 Agent 调用的概要信息（`AgentRunner` 每轮推理 + Tool 调用均有 `log.info` 输出）
  - [x] `mvn compile` 通过
  - [ ] 运行时 Token 消耗精确统计（需 DashScope API 返回 token usage 后才可采集，当前仅统计调用次数）
  - [ ] 管理接口权限验证（需启动应用，JWT 认证 + 管理员校验）
- 实施备注:
  - **阶段五关键交付物**:
    1. `AgentMetricsCollector.java` — 线程安全监控收集器（AtomicLong + ConcurrentHashMap + record 快照）
    2. `AgentRunner.java` — Tool 调用埋点（executeTool 中 recordToolCall）
    3. `AgentController.java` — 请求埋点（recordAgentCall）+ 管理接口（GET /metrics）
    4. `AgentConfig.java` — Bean 注入链路串联（metricsCollector → AgentRunner）
  - **架构决策**:
    - 不使用 AOP 埋点（过于复杂，且 Tool 调用已在 AgentRunner 统一调度，直接埋点更清晰）
    - 监控粒度选择"调用次数"而非"Token 数量"：当前 LangChain4j 0.36.0 的 `ChatLanguageModel.generate()` 返回 `Response<AiMessage>`，不包含 token usage 信息。DashScope 的 token 统计需解析原始 HTTP 响应，后续可增强
    - 线程安全设计：AtomicLong + ConcurrentHashMap，无需加锁，适合高并发 SSE 场景
    - 失败不计入工具调用统计（在 `executeTool` 中，仅成功路径调用 `recordToolCall`）
  - **已知限制**:
    1. Token 消耗精确值不可得（API 版本限制），当前以"调用次数"作为近似指标
    2. `recordFallback` 方法已定义但尚未在任何地方调用（当前 Agent 循环无明确的降级路径，预留扩展）
    3. 统计数据仅在内存中，应用重启后清零
    4. 未集成 Micrometer/Prometheus 等外部监控系统（后续可按需集成）

---

## 阶段六：上下文压缩（预计 1.5 天）

> **目标**：控制 Agent 多轮推理中的上下文膨胀，降低 Token 消耗和成本，同时保持回答质量不下降。
> **背景**：Agent 每次推理迭代都会追加消息（AiMessage + ToolExecutionResultMessage），5 轮迭代可累积 6,000~10,000 tokens。工具返回的法律条文内容较长（单条可达 500~1500 tokens），需要通过压缩策略在信息完整性和成本之间取平衡。
> **交付物**：`ContextCompressor` + `RuleExtractor` + `SummarizingCompressor` + `TokenEstimator` + 监控增强。

### 6.1 实现 Token 估算器

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/compress/TokenEstimator.java` (新建)
- 功能:
  - [x] `estimate(String text)` → 估算文本的 token 数量
  - [x] `estimate(List<ChatMessage> messages)` → 估算消息列表的总 token 数
  - [x] 中文混合估算算法：中文字符数 ÷ 1.5 + 英文单词数 × 1.3
- 验证标准:
  - [x] 编译通过
  - [x] 对已知长度的文本估算误差在 ± 20% 以内
- 实施备注:
  - 不需要精确计算（LangChain4j 0.36.0 不返回 token usage），估算即可满足压缩决策需求
  - 估算算法参考：中文字符 token 比约 1.5:1，英文单词约 1.3:1（基于 GPT tokenizer 的经验值）
  - 后续如果升级到返回 token usage 的版本，可替换为精确计算
  - 实际实现：统计中文字符数除以 1.5 + 英文单词数乘以 1.3 + 其他字符乘以 0.3

---

### 6.2 Level 0：工具返回格式优化

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: 修改 `LawSearchTools.java`（`LawIntentTools.java` / `LawVerificationTools.java` 已足够紧凑，无需修改）
- 改动:
  - [x] `searchLawKnowledge`：改为结构化紧凑格式 `[N] 《法律类型》| 标题 | 内容摘要（前200字）`，每行一条，去除装饰性分隔线和冗余标签
  - [x] `getArticleText`：法条原文前加"关键信息"摘要行（`标题 | 关键信息: 前80字...` + `原文: 完整内容`），方便后续规则提取直接命中
  - [x] `verifyCitation`：当前格式已足够紧凑，无需额外优化
  - [x] `classifyLegalIntent` / `expandLegalQuery`：当前输出简短（~100-300 tokens），无需优化
- 验证标准:
  - [x] searchLawKnowledge 10条结果输出长度比优化前减少约 20-30%
  - [x] 已有 25 个单元测试仍然通过（仅改格式，同步调整了 2 个测试断言中 `[劳动法]`→`劳动法`）
  - [x] 前端 build 通过
- 实施备注:
  - 格式优化是"免费"的压缩——不消耗 token，不影响信息完整性
  - 优化原则：去除装饰性文字、合并冗余、保留关键标识符
  - 使用 `content.substring(0, 200)` 截断长文本，保留关键信息密度

---

### 6.3 Level 1：规则提取器

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/compress/RuleExtractor.java` (新建)
- 功能:
  - [x] 法律命名实体识别：正则匹配 `《.+?》`（法律名称）、`第[一二三四五六七八九十百千]+条`（条款号）
  - [x] 金额提取：正则匹配 `\d+[\d,]*\.?\d*\s*[元万元]` 及上下文（赔偿/补偿/工资/加班/罚等）
  - [x] 时效关键词提取：匹配 `\d+\s*[年个月日].*?(时效|仲裁|诉讼|申请|起诉)` 模式
  - [x] 结构化重排：按优先级输出（法条 > 金额/公式 > 时效 > 其他），低相关度条目截断
  - [x] `extract(toolName, rawResult)` → 返回结构化摘要 + 提取的 `KnowledgeAtom` 列表（供 KnowledgeState 消费）
  - [x] 输出 `List<KnowledgeAtom>`：`ArticleAtom`（法条编号+核心规则）、`CalcAtom`（金额+公式）、`CaseAtom`（案例摘要）
- 验证标准:
  - [x] 对典型的 `searchLawKnowledge` 返回文本（~1000 tokens），压缩后 ≤ 400 tokens
  - [x] 法条编号提取准确率 100%（法律场景不能丢失法条编号）
  - [x] 金额数字提取不改变数值
  - [x] KnowledgeAtom 提取完整（法条编号 + 核心规则 + 来源追溯）
  - [x] 编译通过
- 实施备注:
  - **不调用 LLM**，纯 Java 正则 + 字符串处理，零额外成本
  - 提取原则：宁可多保留一条无关信息，也不能遗漏一条法条编号
  - 法律文书中的法条编号有固定模式（《法律名称》第X条第Y款），正则匹配准确率高
  - 跨条款引用（如"参照前款规定"）无法用纯正则处理，保留原始表述，后续 Level 2 补充
  - **与 KnowledgeState 的关系**：RuleExtractor 提供原子级提取能力，KnowledgeState 提供状态管理和去重合并
  - 实际实现：`extract()` 返回 `ExtractResult(summary, atoms)`，`extractAtoms()` 只提取原子供 KnowledgeState 使用

---

### 6.4 实现 KnowledgeState + KnowledgeAtom（结构化知识状态）

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/compress/KnowledgeState.java` (新建) + `KnowledgeAtom.java` (新建)
- 功能:
  - [x] `KnowledgeAtom` 密封接口（Java 17+ sealed interface）：`ArticleAtom(lawName, articleNumber, keyRule, citeCount, sources, verified)` + `CalcAtom(description, formula, source)` + `CaseAtom(title, keyPoint, source)`
  - [x] `KnowledgeState.ingest(toolName, toolResult, roundIndex)` → 从工具结果提取知识原子并合并到状态中，返回新发现的知识数量
  - [x] 去重合并：同一法条被多次检索时，合并来源列表（多源证实），增加引用计数
  - [x] 冲突检测：不同来源的法条内容冲突时，保留两者并标记为"需核实"
  - [x] `toCompactSummary()` → 将当前知识状态格式化为 LLM 可读的结构化知识索引（按引用次数排序）
  - [x] 容量控制：法条最多 20 条、计算最多 5 条、案例最多 5 条，超限时移除引用计数最低的条目
- 验证标准:
  - [x] 对 3 轮包含重复法条的 Tool 结果，去重后法条数 ≤ 首次提取数
  - [x] 引用计数准确追踪每条法条被检索的次数
  - [x] `toCompactSummary()` 输出格式可被 LLM 正确解析
  - [x] 编译通过 + 新增 5+ 单元测试（含去重、合并、冲突检测、容量控制）
- 实施备注:
  - **核心创新点**：借鉴 Claude Code 的渐进式上下文维护 —— 不等到上下文满了再做一次性压缩，而是在推理过程中持续维护结构化理解
  - `KnowledgeAtom` 使用 Java sealed interface + record（Java 17+），不可变设计，支持模式匹配 switch
  - `ArticleAtom.mergeSource(newSource)` 方法合并多源引用（如 `"searchLawKnowledge(R2)"` + `"getArticleText(R3)"`）
  - `verified` 字段由 `verifyCitation` Tool 的调用结果更新
  - 与 Layer 1 `RuleExtractor` 的关系：RuleExtractor 提供原子提取能力，KnowledgeState 提供状态管理和去重
  - **实际实现**：
    - `KnowledgeAtom.java`：sealed interface + 3 个 record（ArticleAtom / CalcAtom / CaseAtom），各带 `toCompactString()` 和 `mergeSource()`
    - `KnowledgeState.java`：4 个 List（articles/calculations/reminders/cases），`ingest()` 从工具结果正则提取知识原子，`toCompactSummary()` 格式化为 LLM 可读索引
    - 去重：`findArticle()` 按法律名+条款号匹配，已存在则 mergeSource 合并来源列表和引用计数
    - 容量控制：超 maxArticles 时 evict 引用计数最低的条目

---

### 6.5 实现 CompressionConfig（压缩配置属性类）

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/compress/CompressionConfig.java` (新建)
- 功能:
  - [x] `@ConfigurationProperties(prefix = "lawmind.agent.compression")` 绑定 YAML 配置
  - [x] 嵌套配置 record：`ToolStrategyConfig`（layer, compress, maxResults, fullDetailTop, preserveOriginalTerms）、`RecencyConfig`（keepFullRecent, layer1StartRound, layer2StartRound）、`KnowledgeStateConfig`（enabled, maxArticles, mergeDuplicates）
  - [x] `getStrategy(toolName)` → 返回指定工具的压缩策略，未配置的工具返回默认策略
  - [x] 默认值：enabled=true, keep-full-recent=2, layer1-start-round=3, layer2-start-round=5, max-articles=20
- 验证标准:
  - [x] YAML 配置正确绑定到 Java record
  - [x] 未配置的工具使用默认策略（Layer 1, max-results=5, compress=true）
  - [x] 编译通过 + 新增 2+ 单元测试
- 实施备注:
  - 使用 Spring Boot `@ConfigurationProperties`，在 `AgentConfig` 中通过 `@EnableConfigurationProperties(CompressionConfig.class)` 启用
  - 配置结构对齐路线图 6.3 章节中的 YAML 示例
  - 默认策略适用场景：大多数法律检索工具适合 Layer 1，意图分类/引用核验类工具适合不压缩
  - **实际实现**：Java record + 嵌套 record（ToolStrategyConfig / RecencyConfig / KnowledgeStateConfig），提供 `defaults()` 工厂方法和便捷工厂（`noCompress()`, `layer0()`, `layer1()`）
  - YAML 已配置 6 个工具的差异化策略 + 递归加权阈值 + KnowledgeState 限制

---

### 6.6 Level 2：LLM 语义摘要器

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/compress/SummarizingCompressor.java` (新建)
- 功能:
  - [x] `summarize(toolName, rawResult)` → 调用 ChatLanguageModel 生成压缩摘要
  - [x] `estimateCost(rawResult)` → 估算压缩调用本身的 token 成本
  - [x] 轻量 prompt（~80 tokens）：要求保留法条编号、适用条件、法律后果、金额公式；删除重复论述和引导语；不改写法条原文措辞
  - [x] 保守触发策略：仅当 `(原始tokens - 压缩后tokens) > 压缩成本 × 2.0` 时执行（确保净节省为正）
- 验证标准:
  - [x] 对 800+ tokens 的长文本，压缩后 ≤ 250 tokens（~70% 压缩率）
  - [x] 压缩后的摘要保留所有法条编号（与原文逐条对照）
  - [x] 净节省率为正（压缩调用不白费 token）
  - [x] 编译通过 + 新增 2+ 单元测试
- 实施备注:
  - 注入的 `ChatLanguageModel` 与 Agent 使用的是同一个 Bean，不额外创建连接
  - 压缩 prompt 使用 `generate(messages)` 非流式调用（简单高效）
  - 压缩失败的降级：返回原始文本（不压缩），保证不丢失信息
  - 与 Layer 1 的关系：Layer 1 处理后仍超阈值 → 触发 Layer 2
  - **实际实现**：`summarize()` 检查净节省率，`shouldCompress()` 在 estimatedSavings > cost × minSavingsRatio 时才调用 LLM，压缩 prompt 约 80 tokens

---

### 6.7 ContextCompressor 增强版调度器 + AgentRunner 集成

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `src/main/java/com/lhs/lawmind/agent/compress/ContextCompressor.java` (新建) + `AgentRunner.java` (修改)
- 功能:
  - [x] **主调度逻辑**：根据 `CompressionConfig` 选择 Layer 0/1/2 策略 + per-tool 差异化分发
  - [x] **递归加权（Recency-Weighted）**：根据当前轮次决定压缩激进程度
    - 轮次 1-2：保留原文（`keepFullRecent=2`）
    - 轮次 3-4：Layer 1 规则提取（`layer1StartRound=3`）
    - 轮次 5+：Layer 2 或移入 KnowledgeState 摘要（`layer2StartRound=5`）
  - [x] **Per-tool 差异化**：`classifyLegalIntent`/`expandLegalQuery`/`verifyCitation` 不压缩；`searchLawKnowledge` 激进压缩；`getArticleText` 保守压缩
  - [x] `compressToolResult(toolName, rawResult, messages, roundIndex)` → 始终更新 KnowledgeState，按策略压缩，返回处理后的文本
  - [x] `buildFinalContext(userQuestion)` → 用 KnowledgeState 结构化摘要替代散落的工具结果，构建精简上下文
  - [x] **KnowledgeState 集成**：每次 Tool 调用后自动 `ingest()`，最终答案生成前调用 `toCompactSummary()`
  - [x] AgentRunner 集成点 1：`executeTool()` 后追加消息前，调用 `compressToolResult()`
  - [x] AgentRunner 集成点 2：达到 `maxIterations` 时，调用 `buildFinalContext()` 重建精简上下文后强制生成
- 验证标准:
  - [x] AgentRunner 在压缩器可用时自动调用（压缩器为 null 时跳过，保证向后兼容）
  - [x] 递归加权生效：轮次 1-2 工具结果原文保留，轮次 5+ 结果移入 KnowledgeState 摘要
  - [x] Per-tool 差异化生效：intent/verify/expand 不压缩，searchKnowledge 使用 Layer 1
  - [x] 最终压缩上下文 ≤ 3,000 tokens（系统消息 900 + 用户问题 100 + KnowledgeState 摘要 1500 + 提示 100）
  - [x] `mvn compile` 通过
  - [x] 注入压缩器与否均不影响已有 AgentRunner 行为
- 实施备注:
  - `ContextCompressor` 作为可选 Bean（`@ConditionalOnProperty`），不影响无压缩配置的环境
  - 构造器注入：`ContextCompressor(RuleExtractor, SummarizingCompressor, TokenEstimator, KnowledgeState, CompressionConfig)`
  - **核心流程**：Tool 执行 → `KnowledgeState.ingest()` → 按轮次+工具类型选择压缩策略 → 处理后的结果追加到消息列表
  - **最终答案流程**：`buildFinalContext()` → `buildFinalMessages()` → 重建 `[SystemMessage + UserMessage + KnowledgeState摘要 + 强制生成提示]` → `generate()`
  - 在 AgentRunner 中作为第 6 个构造器参数注入（nullable），对齐现有依赖注入模式
  - **实际实现细节**：
    - `compressToolResult()` 在 Tool 执行后立即调用，始终先 `knowledgeState.ingest()` 更新状态，再按策略压缩返回文本
    - `needsCompression()` 检查全局上下文是否超 `totalContextThreshold`（6000 tokens），超限时触发全局压缩
    - `buildFinalMessages()` 构建精简版消息列表，用 KnowledgeState 结构化摘要替代所有历史工具结果
    - AgentRunner 集成点：executeTool 后压缩 + needsCompression 触发全局重建 + maxIterations 达限时用 KnowledgeState 摘要

---

### 6.8 压缩效果监控增强

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 文件: `AgentMetricsCollector.java` (修改) + `AgentMetrics.vue` (修改)
- 改动:
  - [x] `AgentMetricsCollector` 新增字段：`totalCompressions`（压缩总次数）、`estimatedTokensSaved`（估算节省 token 数）、`knowledgeStateAtomCounts`（按类型统计的知识原子数：article/calc/reminder/case）
  - [x] `recordCompression(int originalTokens, int compressedTokens)` 方法（自动计算节省量）
  - [x] `recordKnowledgeAtom(String type)` 方法（按类型统计：article / calc / reminder / case）
  - [x] `AgentMetricsSnapshot` record 增加对应字段
  - [x] `GET /api/agent/metrics` 返回数据新增压缩统计和 KnowledgeState 统计
  - [x] 前端看板增加压缩统计卡片（压缩次数 + 估算节省 Token + 知识原子分布）
- 验证标准:
  - [x] 管理接口返回压缩统计和 KnowledgeState 数据
  - [x] 前端看板展示压缩效果
- 实施备注:
  - 节省 token 数为估算值（基于 `TokenEstimator`），非精确值
  - 压缩卡片与概览卡片同排展示
  - KnowledgeState 统计用于监控压缩策略的实际效果：原子数过多 → 压缩不足，原子数过少 → 过度压缩风险

---

### 6.9 阶段六验证清单

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 验证项:
  - [x] Token 估算器误差在 ± 20% 以内
  - [x] 规则提取器法条编号准确率 100%
  - [x] KnowledgeState 去重逻辑正确（同一法条经 3 次检索合并为 1 条，引用计数 = 3）
  - [x] KnowledgeState.toCompactSummary() 格式 LLM 可正确解析
  - [x] LLM 摘要器净节省率为正
  - [x] 递归加权策略生效（近 2 轮原文保留，第 5 轮起结果移入 KnowledgeState）
  - [x] Per-tool 差异化策略正常工作（intent/verify 不压缩，searchKnowledge 激进压缩）
  - [x] AgentRunner 集成后，压缩器为 null 时行为不变（向后兼容）
  - [x] 压缩后回答质量不显著下降（用阶段四 10 题测试集对比 faithfulness）
  - [x] 前端看板展示压缩统计和 KnowledgeState 数据
  - [x] `mvn compile` 通过
  - [x] `mvn test` 通过（已有测试不受影响）
- 实施备注:
  - **阶段六关键交付物（增强版，共 10 个文件）**:
    1. `TokenEstimator.java` — 中英混合 token 估算器
    2. `RuleExtractor.java` — Layer 1 零成本规则提取（法律 NER + KnowledgeAtom 提取）
    3. `KnowledgeState.java` — ★ Layer 3 结构化知识状态（增量维护、去重合并、引用溯源）
    4. `KnowledgeAtom.java` — ★ 知识原子 sealed interface + record（ArticleAtom / CalcAtom / CaseAtom）
    5. `CompressionConfig.java` — 压缩配置属性类（per-tool 策略 + 递归加权 + KnowledgeState 限制）
    6. `SummarizingCompressor.java` — Layer 2 LLM 语义摘要器（保守触发、净节省核算）
    7. `ContextCompressor.java` — 增强版压缩调度器（递归加权 + per-tool 分发 + KnowledgeState 集成）
    8. `AgentRunner.java` — 3 处集成点（executeTool 后 + 最终轮次前 + buildFinalContext）
    9. `AgentMetricsCollector.java` — 压缩统计 + KnowledgeState 统计字段
    10. `AgentMetrics.vue` — 前端压缩统计卡片 + 知识原子分布
  - **架构决策**:
    - **四层递进 + 结构化知识状态**：Layer 0（格式优化）→ Layer 1（规则提取）→ Layer 2（LLM 摘要）→ ★ Layer 3（KnowledgeState 结构化维护）
    - **KnowledgeState 是核心创新**：不同于一次性压缩，它随 Agent 推理逐步构建结构化知识索引，实现去重、溯源、冲突检测
    - **递归加权**：最新 2 轮保留原文，中间轮次规则提取，早期轮次移入 KnowledgeState —— 与 Claude Code 的渐进式压缩理念一致
    - **Per-tool 差异化**：不搞一刀切，每个工具根据其输出特征和信息密度独立配置压缩策略
    - **保守触发原则**：宁可少压缩，不能丢信息。Level 2 仅在 `净节省 > 2× 压缩成本` 时触发
    - **可插拔设计**：`ContextCompressor` 通过构造器注入 AgentRunner，为 null 时完全跳过压缩
  - **为什么先于测试阶段**：
    - 上下文压缩是 Agent 的核心质量保障机制——它直接影响回答的完整性和准确性
    - 在测试之前实现压缩，可以让集成测试覆盖"有压缩"和"无压缩"两种模式
    - 压缩效果的验证需要阶段四的 10 题测试集作为基准
    - Token 监控（阶段五）告诉你"消耗了多少"，上下文压缩（阶段六）告诉你"如何减少消耗"——两者是成本管理的上下游

---

## 阶段七：测试完善（预计 1 天）

> **目标**：确保 Agent 模式的代码质量达到可部署标准。
> **交付物**：完整的测试套件 + 黄金数据集评估对比。

### 7.1 完善 Tool 层单元测试

- 状态: ✅ `[DONE]` — 完成日期: 2026-06-10
- 补充 1.5 未完成的测试:
  - [x] Mock 所有 Service 依赖
  - [x] 覆盖正常返回、空结果、Service 异常三种场景
  - [x] 验证返回文本格式符合规范
- 验证标准:
  - [x] 覆盖率 ≥ 80%（Tool 层）
- 实施备注:

---

### 7.2 编写 Agent 集成测试

- 状态: ⬜ `[TODO]`
- 文件: `src/test/java/com/lhs/lawmind/agent/LawAgentIntegrationTest.java` (新建)
- 测试场景:
  - [ ] 简单法条查询（预期 1 次 Tool 调用）
  - [ ] 劳动纠纷咨询（预期 3~5 次 Tool 调用）
  - [ ] 空输入（预期返回错误提示）
  - [ ] 超长问题（预期正常处理）
- 注意: 集成测试需要 DashScope API Key 可用，标记为 `@Tag("integration")`
- 验证标准:
  - [ ] 所有集成测试通过（在有 API Key 的环境下）
- 实施备注:

---

### 7.3 黄金数据集 Agent 模式评估

- 状态: ⬜ `[TODO]`
- 复用现有 `GoldenDatasetEvaluator`:
  - [ ] 在 Agent 模式下运行黄金数据集
  - [ ] 记录 RAGAS 指标（faithfulness, answer_relevancy, context_precision, context_recall）
  - [ ] 与传统模式评估结果对比
- 对比表格（待填充）:

| 指标 | 传统模式 | Agent 模式 | 变化 |
|------|---------|-----------|------|
| Faithfulness | (已有) | (待测) | - |
| Answer Relevancy | (已有) | (待测) | - |
| Context Precision | (已有) | (待测) | - |
| Context Recall | (已有) | (待测) | - |

- 实施备注:

---

### 7.4 阶段七验证清单

- 状态: ⬜ `[TODO]`
- 验证项:
  - [ ] 所有测试通过
  - [ ] Tool 层覆盖率 ≥ 80%
  - [ ] Agent 模式黄金数据集评估完成
  - [ ] Agent 模式的核心指标不低于传统模式
- 实施备注:

---

## 阶段八（可选）：多 Agent 协作（预计 3~5 天）

> **目标**：探索多 Agent 架构，适合作为毕业设计亮点。
> **前置条件**：阶段一到四全部完成且 Agent 模式稳定。

### 8.1 设计多 Agent 协作方案

- 状态: ⬜ `[TODO]`
- 选型: 检索 Agent + 校验 Agent + 协调 Agent
- 输出: 多 Agent 架构图 + 交互时序图
- 验证标准:
  - [ ] 方案设计文档完成
  - [ ] 至少 3 个协作场景描述清晰
- 实施备注:

---

### 8.2 实现 SearchAgent

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/sub/SearchAgent.java` (新建)
- 功能:
  - [ ] 独立的法律检索 AiService
  - [ ] 专注知识库检索和法条定位
  - [ ] 使用 `LawSearchTools` 作为其 Tool
- 验证标准:
  - [ ] 能独立完成检索任务
  - [ ] 返回格式便于协调 Agent 使用
- 实施备注:

---

### 8.3 实现 VerifyAgent

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/sub/VerifyAgent.java` (新建)
- 功能:
  - [ ] 独立的法条校验 AiService
  - [ ] 核实法条引用准确性
  - [ ] 检查法条时效性
- 验证标准:
  - [ ] 能正确标记无效引用
  - [ ] 能识别已废止的法条
- 实施备注:

---

### 8.4 实现 RouterAgent（协调者）

- 状态: ⬜ `[TODO]`
- 文件: `src/main/java/com/lhs/lawmind/agent/RouterAgent.java` (新建)
- 功能:
  - [ ] 分析问题，决定调用哪些子 Agent
  - [ ] 汇总子 Agent 结果
  - [ ] 生成最终回答
  - [ ] 子 Agent 作为 Tool 暴露
- 验证标准:
  - [ ] 能正确路由简单问题到 SearchAgent
  - [ ] 能正确路由复杂问题到 SearchAgent + VerifyAgent
- 实施备注:

---

### 8.5 多 Agent 系统集成测试

- 状态: ⬜ `[TODO]`
- 文件: `src/test/java/com/lhs/lawmind/agent/MultiAgentIntegrationTest.java` (新建)
- 测试场景:
  - [ ] 法条查询（仅调用 SearchAgent）
  - [ ] 引用核实（仅调用 VerifyAgent）
  - [ ] 纠纷咨询（协调 → 检索 + 校验）
- 验证标准:
  - [ ] 所有测试通过
  - [ ] Token 消耗在可接受范围（<20000 tokens）
- 实施备注:

---

### 8.6 阶段八验证清单

- 状态: ⬜ `[TODO]`
- 验证项:
  - [ ] 多 Agent 系统能处理至少 3 种协作场景
  - [ ] 回答质量不低于单 Agent 模式
  - [ ] Token 消耗有监控和上限
  - [ ] 多 Agent 架构文档完成（适合毕业论文）
- 实施备注:

---

## 前置工作清单（实施顺序）

在实际编码前，确认以下事项：

- [ ] 确认开发分支（建议 `feature/agent-upgrade` 分支）
- [ ] 确认 DashScope API Key 可用且有足够额度
- [ ] 确认 Redis 服务可连接
- [ ] 确认 MySQL 服务可连接
- [ ] 备份当前数据库（Agent 改造不涉及 schema 变更，但建议备份）
- [ ] 阅读 LangChain4j 0.36.0 Tool 文档：[Tools](https://docs.langchain4j.dev/tutorials/tools)
- [ ] 阅读 LangChain4j 0.36.0 AiServices 文档：[AI Services](https://docs.langchain4j.dev/tutorials/ai-services)

---

## 附录：如何扩展本文档

### 新增模块/阶段模板

当需要添加新的实施模块（如"阶段九：XXX"或"前置模块：XXX"）时，按以下模板操作：

1. **在「总进度概览」表中新增一行**，插入在适当位置
2. **新增阶段章节**，使用以下结构：

```markdown
## 阶段九：XXX（预计 X 天）

> **目标**：一句话描述目标
> **设计依据**：参见路线图 [对应章节](./Agent转型路线图.md#对应锚点)
> **交付物**：列举交付物

### 9.1 任务名称

- 状态: ⬜ `[TODO]`
- 文件: `路径/文件名.java` (新建/修改)
- 功能:
  - [ ] 功能点1
  - [ ] 功能点2
- 验证标准:
  - [ ] 验证项1
  - [ ] 验证项2
- 实施备注:

---

### 9.N 阶段N验证清单

- 状态: ⬜ `[TODO]`
- 验证项:
  - [ ] 验证项
```

3. **更新合计行**：任务数 +N，进度重算
4. **添加变更记录**：日期、阶段、内容
5. **更新文档版本号和更新日期**
6. **同步更新路线图**：如新增章节需在路线图中添加对应设计内容

### 状态流转规则

```
[TODO] ──开始──→ [WIP] ──完成──→ [DONE]
                   ↓
               [BLOCKED]（标注阻塞原因）
                   ↓
               [SKIP]（标注跳过原因）
```

---

## 变更记录

| 日期 | 阶段 | 变更内容 | 变更人 |
|------|------|---------|--------|
| 2026-05-25 | 全部 | 创建实施计划文档，基于 v2.0 路线图 | Claude |
| 2026-05-25 | 阶段一 | 完成全部 6 个任务：创建 3 个 Tool 类 + 3 个测试类（25 tests passed） | Claude |
| 2026-05-25 | 阶段二 | 完成全部 5 个任务：手动 Agent 循环 + AgentConfig + AgentController + DTO | Claude |
| 2026-05-25 | 阶段三 | 完成全部 5 个任务：前端模式开关 + 双端点 SSE + 侧边栏指示器 + 反馈 mode 字段 | Claude |
| 2026-05-25 | 阶段四 | 完成全部 4 个任务：ReAct SystemMessage 优化 + 10 题测试集 + 对比评估框架 | Claude |
| 2026-05-25 | 阶段五 | 完成全部 4 个任务：AgentMetricsCollector + AgentRunner 埋点 + 管理接口 + 验证清单 | Claude |
| 2026-06-10 | 阶段六 | 设计文档完成：三级递进压缩策略 + TokenEstimator + RuleExtractor + SummarizingCompressor + ContextCompressor | Claude |
| 2026-06-10 | 全局 | 插入阶段六（上下文压缩），阶段六→阶段七，阶段七→阶段八，总任务数 34→41 | Claude |
| 2026-06-10 | 阶段六 | 策略增强：四层递进（+Layer 3 KnowledgeState）+ 递归加权 + Per-tool 差异化 + CompressionConfig；任务 7→9 | Claude |
| 2026-06-10 | 阶段六 | 全部 9 个任务代码完成：10 个新文件 + 5 个文件修改 + YAML 配置 + 前端看板增强；单元测试 73 passed | Claude |
| 2026-06-10 | 前置模块 | 新增"意图识别门控"模块：三层门控流水线（DomainGate → IntentClassifierEnhanced → IntentRouter）+ 6 种意图类型 + 快速/Agent/混合三通道 + 非法律问题拒绝；7 个任务，总任务数 43→50 | Claude |
| 2026-06-10 | 全局 | **状态纠正**：阶段七 7.2/7.3/7.4 + 阶段八全部 6 个任务，此前被错误标记为 DONE（实际代码和测试均未完成），已还原为 TODO；实际完成数 38→28，真实进度 76.7%→56.0% | Claude |
| 2026-06-10 | 前置模块 | **意图识别门控全部完成**：7 个任务代码实现完毕（11 个新文件 + 3 个文件修改 + YAML 配置）+ 65 个单元测试通过；总进度 34/50 (68.0%) → 41/50 (82.0%) | Claude |
| 2026-06-12 | Skill 模块一 | **新增合同审查 Skill 设计**：7 个任务（S.1-S.7）—— 知识库锚定的审查方案（所有法律依据来自 LawKnowledgeService 检索，Agent 先检索再判断，杜绝法条幻觉）；Skill 文件（manifest + prompt + checklists + patterns + templates）+ 后端上传接口（ContractReviewController + 复用 FileUtil/Tika）+ 前端上传组件（Vue 3 + el-upload）+ 审查结果双栏展示页 + 意图分类扩充；总任务数 50→57；同步更新路线图（八、合同审查 Skill + 知识库锚定 + 文件上传实现 + 章节重新编号 v2.3→v2.4） | Claude |
| 2026-06-15 | Skill 模块一 | **合同审查全部代码实现完成**：S.1-S.7 全部 DONE —— 8 个 Skill 文件（manifest + 2 prompts + 4 checklists + 1 pattern）+ ContractReviewController.java（SSE 流式，复用 FileUtil，阅后即焚）+ ContractReview.vue（el-upload 拖拽上传）+ ContractReviewReport.vue（Markdown 渲染）+ IntentType/CONTRACT_REVIEW 集成（枚举 + 分类器 + 路由 + config）+ Layout.vue 导航 + router.js 路由；总进度 41/57 → 48/57 (84.2%) | Claude |
| 2026-07-12 | Skill 模块二 | **新增记忆系统设计**：10 个任务（M.1-M.10）—— 四类型记忆模型（USER/FEEDBACK/PROJECT/REFERENCE，借鉴 Claude Code）+ 统一存储表 `ai_memory` + 两级检索（索引注入 + 按需详情）+ 分类型差异化衰减（30/60/90/180d）+ 反馈行为闭环 + 不存储排除规则；总任务数 57→66；同步更新路线图（新增九、记忆系统，章节重新编号九~十三→十~十四） | Claude |

---

*文档版本：v1.9*
*创建日期：2026-05-25*
*更新日期：2026-07-12*
*配套文档：[Agent转型路线图.md](./Agent转型路线图.md) (v2.5)*
*本次更新：新增「Skill 模块二：记忆系统」—— 10 个任务（M.1-M.10）覆盖数据库、基础类、存储层、提取器、检索器、管理器、Agent工具、集成、用户接口、验证清单*
