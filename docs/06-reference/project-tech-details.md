> 版本：V1.0 | 日期：2026-08-12 | 状态：📝 历史归档
> 类型：06-reference 历史资料 | 说明：项目技术详解（面试备用），内容已分解进 01-architecture

# LawMind 法律 AI 助手 — 项目技术详解

> 面试备用文档，涵盖项目从 RAG 检索到 Agent 推理的完整技术实现。每个模块均结合项目实际代码说明，确保可以应对面试追问。

---

## 目录

1. [项目概述](#1-项目概述)
2. [RAG 检索增强生成](#2-rag-检索增强生成)
   - [2.1 文档处理与分块策略](#21-文档处理与分块策略)
   - [2.2 查询预处理与扩展](#22-查询预处理与扩展)
   - [2.3 混合检索：BM25 + 向量 + RRF 融合](#23-混合检索bm25--向量--rrf-融合)
   - [2.4 多级精排：Rerank + MMR + 双阈值过滤](#24-多级精排rerank--mmr--双阈值过滤)
   - [2.6 HyDE 假设文档嵌入](#26-hyde-假设文档嵌入)
   - [2.7 引用验证与合规声明](#27-引用验证与合规声明)
   - [2.8 安全守卫：多层防护体系](#28-安全守卫多层防护体系)
   - [2.9 RAG 完整流水线](#29-rag-完整流水线)
3. [Agent 智能代理系统](#3-agent-智能代理系统)
   - [3.1 意图门控：四级流水线](#31-意图门控四级流水线)
   - [3.2 快慢分流策略](#32-快慢分流策略)
   - [3.3 ReAct 循环实现](#33-react-循环实现)
   - [3.4 工具系统](#34-工具系统)
4. [上下文压缩系统](#4-上下文压缩系统)
   - [4.1 渐进式四级压缩](#41-渐进式四级压缩)
   - [4.2 KnowledgeState：结构化知识状态](#42-knowledgestate结构化知识状态)
   - [4.3 按工具定制的压缩策略](#43-按工具定制的压缩策略)
   - [4.4 近因加权策略](#44-近因加权策略)
5. [记忆系统](#5-记忆系统)
   - [5.1 四种记忆类型](#51-四种记忆类型)
   - [5.2 两级混合检索](#52-两级混合检索)
   - [5.3 LLM 驱动的记忆提取](#53-llm-驱动的记忆提取)
   - [5.4 按类型衰减与容量管理](#54-按类型衰减与容量管理)
6. [工程化基础设施](#6-工程化基础设施)
   - [6.1 AOP 横切关注点](#61-aop-横切关注点)
   - [6.2 RAG 评估体系](#62-rag-评估体系)
   - [6.3 Redis Stack 向量存储方案](#63-redis-stack-向量存储方案)
7. [技术栈总结](#7-技术栈总结)

---

## 1. 项目概述

**LawMind** 是一个面向法律领域的智能问答系统，核心技术栈为 **Spring Boot 3.5 + Java 17 + LangChain4j 0.36 + Redis Stack + MySQL**。项目的核心能力包括：

- **RAG 检索增强生成**：多级混合检索管道，从文档分块到引用验证的完整链路
- **Agent 智能代理**：基于 ReAct 循环的多工具调用推理，支持 6 个法律工具
- **意图门控**：四级流水线，自动判断法律相关性、意图类型、复杂度和路由策略
- **上下文压缩**：渐进式四级压缩策略，解决 ReAct 循环中的上下文膨胀问题
- **记忆系统**：四类型跨会话记忆，支持 LLM 自动提取和两级混合检索
- **工程化能力**：AOP 切面（审计/日志）、Golden Dataset 评估、Redis 向量存储

---

## 2. RAG 检索增强生成

### 2.1 文档处理与分块策略

项目实现了两种互补的分块策略，统一实现 `TextChunker` 接口（方法：`List<String> chunk(String text)`）。

#### 2.1.1 LegalArticleChunker：法律文档结构分块

**核心类**：`LegalArticleChunker.java`

利用法律文档的层级结构（编 → 章 → 节 → 条 → 款），将文档解析为树形结构（`LegalDocumentNode.LegalDocument`），再按条输出分块。

**关键流程**：

1. **目录剥离**：`stripToc()` 移除文档目录，避免干扰正文解析
2. **序言识别**：找到第一个"第X章"之前的内容作为序言，按 1200 字符窗口单独分块
3. **结构树构建**：通过正则 `第[一二三四五六七八九十百千零\d]+[章节条编款目]` 扫描所有结构标记，构建 `PartNode → ChapterNode → SectionNode → ArticleNode` 的四级嵌套树
4. **上下文前缀**：每条分块自动附加 `[法律名称 编名 章名 节名 条名]`，确保检索时能定位到具体出处
5. **短块合并**：长度 < 256 字符的短段与相邻段合并
6. **质量校验**：`validateChunks()` 过滤 < 5 字符的碎片，标记不以句号/分号/右括号结尾的潜在截断块

**中文数字转换**：`chineseToInt()` 将中文数字如"一百四十三"转换为整数 143，用于条款排序。

#### 2.1.2 FixedWindowChunker：通用滑动窗口分块

**核心类**：`FixedWindowChunker.java`

基于配置参数的滑动窗口分块，适用于不具法律结构的通用文本。

**配置参数**（来自 `application.yml` 的 `lawmind.chunking`）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `window-size` | 768 字符 | 基础窗口大小（约 512 token） |
| `overlap-size` | 150 字符 | 重叠大小（约 100 token） |
| `max-chunk-size` | 3072 字符 | 单块最大上限（约 2048 token） |
| `sub-chunk-threshold` | 1500 字符 | 子块切分阈值 |

**核心算法** (`doChunk`)：

1. 按换行符将文本分割为段落
2. 逐段累积缓冲区：
   - 若添加当前段落使总长度 > 3072（maxChunkSize）→ 强制刷新缓冲区
   - 若添加当前段落使总长度 > 768（chunkSize）→ 以重叠文本 + 当前段落开启新区
   - 否则追加到当前缓冲区
3. 超长段落（单段 > maxChunkSize）强制按滑动窗口拆分

**智能断点选择** (`findBreakPoint`)：

在目标切分点附近反向搜索最佳断点，优先级为：**句号(。) > 分号(；) / 右括号(）) > 换行(\n)**。确保切分边界在语义完整的位置。

---

### 2.2 查询预处理与扩展

#### 2.2.1 LegalEntityExtractor：法律实体提取

**核心类**：`LegalEntityExtractor.java`

纯规则引擎（零 LLM 耗时），从用户问题中提取结构化法律实体：

| 提取类型 | 方法 | 覆盖范围 |
|----------|------|----------|
| 法律类型 | `extractLawType()` | 13 个法律领域，~115 个关键词映射 |
| 法条引用 | 正则 `《([^》]+)》?\s*第([...])[条节章编款]` | 支持中文/数字条款号 |
| 金额信息 | 正则 `\d+(\.\d+)?\s*[万万千千百十]?\s*元` | 含月工资等变体模式 |
| 当事人类型 | `extractPartyType()` | 用人单位/劳动者/消费者/卖家四类 |
| 时间信息 | 正则 `\d+\s*[个]?\s*[年天日周月个]` | 期限与时效信息 |

提取结果 (`LegalEntities`) 中的 `lawType` 传递给混合检索，作为法律类型过滤器精确限定检索范围。

#### 2.2.2 LegalQueryExpander：查询扩展

**核心类**：`LegalQueryExpander.java`

维护一个 **153 条口语→正式术语映射表** (`SYNONYM_MAP`)，覆盖以下领域：

- **劳动法**："被开除"→"解除劳动合同 辞退 用人单位单方解除"
- **消费者权益**："退一赔三"→"欺诈行为 增加赔偿 三倍 五百元 第五十五条"
- **民法典**："净身出户"→"夫妻财产 过错方 损害赔偿 第一千零九十二条"
- **赔偿缩写**："N+1"→"经济补偿 代通知金 解除劳动合同 第四十七条"

**扩展逻辑** (`expandQuery`)：

1. 遍历问题与每个口语条目的子串包含检查
2. 对匹配的口语词，将其对应正式术语（拆分空格分隔的多词）追加到结果
3. 使用 `HashSet` 去重，避免术语重复追加
4. 最终输出：`原始问题 + 空格分隔的扩展术语`

结果同时用于两种场景：BM25 全文搜索（已包含扩展词）和向量检索（问题带着扩展术语一起向量化）。

#### 2.2.3 LLM 查询改写

**实现位置**：`RagServiceImpl.rewriteQueryWithLLM()`

在规则扩展之外，使用 `qwen-turbo` 模型（temperature=0.1, maxTokens=256）将口语化问题改写为正式法律检索查询。结果通过 `mergeQueries()` 以 `LinkedHashSet` 与规则扩展结果合并，规则扩展中的术语优先保留。

改写的 LLM 调用使用 **LRU 缓存**（上限 500 条），避免相同问题的重复 API 调用。

---

### 2.3 混合检索：BM25 + 向量 + RRF 融合

**核心类**：`HybridSearchServiceImpl.java`

这是检索系统的核心，将向量语义搜索和全文关键词搜索组合在一起。

#### 2.3.1 向量搜索分支（Redis KNN）

```
RedisSearch FT.SEARCH idx:law_knowledge
  => KNN 向量相似度查询（1536 维，COSINE 距离）
  => 提取 fetchSize = max(topK * 3, 40) 提高召回率
  => 从 key 中提取知识 ID（剥离 "law:vector:" 前缀）
  => 后置元数据过滤（法律类型、状态）
```

#### 2.3.2 全文搜索分支（MySQL Ngram）

**中文分词** (`splitSearchTerms`)：

1. 按空格/逗号切分输入
2. 保留法律模式（"第X条"）为完整词条
3. 过滤 46 个中文停用词（"什么"、"怎么"、"的"等）
4. 丢弃 < 2 字符的词条

**查询模式选择** (`canUseFulltext`)：

- 查询 > 15 字符 → `NATURAL LANGUAGE MODE`（适合完整句子）
- 短查询 → `BOOLEAN MODE`（构建 `+term1 +term2 +...` 格式）
- 两者均可降级为 `LIKE %term%` 兜底

**降级链**：
```
BOOLEAN MODE → NATURAL LANGUAGE MODE → LIKE 搜索
```

#### 2.3.3 RRF（倒数排序融合）

**核心算法** (`rrfFusion`)：

```
RRF_score(doc) = 1/(K + rank_vector) + 1/(K + rank_fulltext)
```

其中 `K = 60`。K 值越大，排名深度对最终得分的影响越均匀；K 值越小，排名靠前的结果权重越大。

融合后对分数进行 **Min-Max 归一化** 到 [0, 1]，与后续的双阈值过滤兼容。

#### 2.3.4 实体水合

每条检索结果先从 Redis（`LawKnowledgeRedisUtil`）获取，未命中则回退 MySQL 查询。这在保证热数据低延迟的同时，确保所有法律元数据完整可用。

---

### 2.4 多级精排：Rerank + MMR + 双阈值过滤

检索后的精排流水线：`RRF 融合 → Rerank（可选）→ MMR 多样化（可选）→ 双阈值过滤`

#### 2.4.1 Rerank：DashScope 精排

**核心类**：`RerankServiceImpl.java`

调用阿里 DashScope API 的 `qwen3-rerank` 模型进行语义重排序。

**关键技术细节**：

- **候选截断**：发送给精排器的候选数由 `rerankCandidateTopK`（默认 20）控制，控制 API 调用成本
- **文档构建**：拼接 `lawType + title + content` 作为每个文档的输入
- **结果应用**：将精排返回的 `relevance_score` 写入 `LawKnowledge.score`，按精排后顺序返回
- **故障降级**：API 失败时返回 `candidates.subList(0, topN)`，不中断流水线

**配置**（来自 `application.yml` 的 `rag.search.rerank`）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | true | 是否启用 |
| `model` | qwen3-rerank | 精排模型 |
| `top-n` | 10 | 精排后返回数 |
| `candidate-top-k` | 30 | 发送给精排器的候选数 |

#### 2.4.2 MMR：最大边际相关性多样化

**核心类**：`SearchResultDiversifier.java`

防止检索结果被单一法律章节主导，例如用户问"试用期"，不应该让全部 5 条结果都来自《劳动合同法》第十九条。

**MMR 公式**：

```
MMR = λ × relevance(doc) − (1 − λ) × max_similarity(doc, already_selected)
```

- `λ = 0.7`（可配置 `rag.search.mmr.lambda`）
- λ 越高 → 更偏向相关性（与原始排名接近）
- λ 越低 → 更偏向多样性（跨章节覆盖更广）

**文档相似度**（`computeDocumentSimilarity`）：

基于二维结构矩阵，非语义计算：

| 条件 | 相似度 |
|------|--------|
| lawType 相同 且 章节相同 | 1.0 |
| lawType 相同 但 章节不同 | 0.5 |
| lawType 不同 | 0.0 |
| 两者章节均为 null | 1.0 |

#### 2.4.3 双阈值过滤

**实现在**：`RagServiceImpl.filterByThreshold()`

| 阈值 | 默认值 | 含义 |
|------|--------|------|
| `filter` | 0.40 | 绝对下限，低于此值直接丢弃 |
| `law-knowledge` | 0.55 | 高质量阈值 |

**边缘区域设计**：分数在 [0.40, 0.55) 之间的结果被视为"边缘"结果。如果没有高质量结果，就使用边缘结果作为后备。0.15 的阈值差距创建了一个缓冲带，在精度和召回之间取得平衡。

---

### 2.5 HyDE 假设文档嵌入

**实现在**：`RagServiceImpl` 第 379-394 行

HyDE（Hypothetical Document Embeddings）是一种解决"用户查询与知识库文档语义空间不一致"问题的技术。

**工作流程**：

1. LLM 生成一个假设的法律分析文档（通常 200-400 字的中文法律论证）
2. 对假设文档进行向量化（而非直接对用户问题向量化）
3. 使用假设文档的向量进行知识库检索

**原理**：假设的法律分析文档在语义空间中与真实法律文本更接近（都使用法言法语），因此能检索到更相关的法条。用户的口语化问题（如"老板把我开了"）与法律文本的语义距离较大，但假设文档（"用人单位单方解除劳动合同的法律分析"）与法律文本的语义距离小很多。

**配置**：`rag.search.hyde.enabled` 默认为 true（注意：原 `application.yml` 中 hyde.enabled 为 true，并非 false）。

**故障回退**：HyDE 生成失败时，自动回退为直接使用查询向量。

---

### 2.6 引用验证与合规声明

#### 2.7.1 引用验证

**实现在**：`RagServiceImpl.verifyCitations()`

流程：

1. **提取引用**：正则 `《(.+?)》第([...])条` 从 LLM 回答中提取所有法条引用
2. **中文数字转换**：将"第四十七条"转换为"第47条"便于对照
3. **对照检查**：遍历每个引用，与检索到的知识库内容逐一比对——法律名称和条款号是否在知识库中找到
4. **标记未验证引用**：若引用无法在知识库中找到依据，追加 `UNVERIFIED_CITATION_WARNING`

#### 2.7.2 合规声明

对 `law_knowledge` 和 `llm_direct` 来源的回答，追加标准合规声明：**"以上内容由 AI 生成，仅供参考，不构成法律建议。"**

对 `non_legal_reject` 来源则跳过（非法律回答）。

---

### 2.7 安全守卫：多层防护体系

#### 2.8.1 敏感话题过滤

**核心类**：`SensitiveTopicFilter.java`

在业务逻辑之前拦截敏感话题，被阻止的请求返回静态拒绝消息（来源标记 `guard_blocked`）。

#### 2.8.2 法律相关性判断

**实现在**：`RagServiceImpl.isLegalRelatedQuestion()`

覆盖 **~200 个法律关键词** 和 **~50 个问题模式**（"是否合法"、"怎么维权"等）。

非法律问题的回答为："抱歉，我是一个法律咨询助手，只能回答与法律相关的问题。"（来源标记 `non_legal_reject`）。

#### 2.8.3 提示注入防护

**实现在**：`RagServiceImpl.sanitizeUserInput()`

- 移除 markdown 代码块
- 过滤注入模式：`ignore previous instructions`、`you are now a DAN`、`system:` 等
- 截断输入至 2000 字符

#### 2.8.4 PII 检测

**核心类**：`PiiUtil.java`

在记忆提取和日志记录前检测并脱敏个人身份信息（身份证号、手机号、银行卡号等）。

---

### 2.8 RAG 完整流水线

```
用户问题
  │
  ▼
[SensitiveTopicFilter] 敏感话题拦截
  │
  ▼
[isLegalRelatedQuestion] 法律相关性判断（200+ 关键词, 50+ 模式）
  │ → 非法律 → 拒绝
  ▼
[Step 1] 预处理：TextPreprocessUtil + LegalQueryExpander + LegalEntityExtractor + IntentClassifier
  │
  ▼
[Step 2] LLM 查询改写（qwen-turbo, LRU 缓存 500）+ 向量化（text-embedding-v2, 1536 维）+ HyDE（可选）
  │
  ▼
[Step 3] 知识检索：
  ├─ 混合搜索：向量 KNN（Redis） + 全文搜索（MySQL Ngram）
  │            → RRF 融合（K=60） → Min-Max 归一化
  ├─ Rerank 精排（DashScope qwen3-rerank, 可选）
  ├─ MMR 多样化（λ=0.7, lawType+chapter 结构相似度, 可选）
  └─ 双阈值过滤（0.40 绝对下限, 0.55 高质量）
  │
  ▼
[Step 4] LLM 生成（qwen-plus）：
  ├─ 有知识 → 知识增强提示（对话历史 + 知识库 + 问题）
  └─ 无知识 → 直接 LLM 提示（来源: llm_direct）
  │
  ▼
[Step 5] 后处理：
  ├─ 引用验证（正则提取 + 知识库对照检查）
  ├─ 合规声明附加
  └─ 异步：记录访问、指标采集、记忆提取
  │
  ▼
返回 AIChatResponse（答案 + 关联知识 + 会话 ID）
```

**关键设计原则**：

1. **渐进式降级**：每个外部服务失败时都能优雅降级，不中断整个请求
2. **多阶段排序**：向量+全文→RRF→Rerank→MMR→阈值，每个阶段解决不同问题（召回→精度→多样性→严格度）
3. **安全贯穿始终**：四层安全防护保证输出质量

---

## 3. Agent 智能代理系统

### 3.1 意图门控：四级流水线

**入口类**：`IntentGate.java`

所有用户请求通过四级流水线进行分类和路由：

```
用户问题
  │
  ▼
Layer 1: DomainGate.judge()
  ├─ 空/格式异常/敏感内容 → 拒绝
  ├─ 规则匹配（法律关键词 或 法条模式） → legal(0.95)
  ├─ 规则匹配（非法律关键词） → 拒绝(0.95)
  └─ LLM 兜底 → 返回 legal(0.7) / nonLegal(0.7)
      └─ LLM 失败 → 默认放行 legal(0.5)（宁可错放，不可错拦）
  │
  ▼
Layer 2: IntentClassifierEnhanced.classify()
  ├─ 规则匹配（6 类意图关键词，按优先级排序） → intentType(0.9)
  └─ LLM 兜底 → intentType(0.7)
  │
  ▼
Layer 3a: ComplexityAssessor.assess()（四因子加权）
  └─ 返回 SIMPLE / MEDIUM / COMPLEX
  │
  ▼
Layer 3b: IntentRouter.decide(intent, complexity)
  └─ 返回 RouteDecision（FAST / AGENT / HYBRID）
```

#### 3.1.1 Layer 1：DomainGate 法律领域门控

**核心类**：`DomainGate.java`

**双策略检测**：

1. **规则优先**（低成本）：
   - 法条引用模式：《.+?》、第X条
   - 法律关键词：~26 个核心法律关键词（合同、劳动、工伤……）
   - 非法律关键词：~12 个（天气、游戏、电影……）
   - 敏感内容关键词：色情、暴力、政治、赌博、毒品、翻墙、VPN

2. **LLM 兜底**（高成本但更准确）：
   - 向 LLM 发送："请判断以下问题是否涉及中国法律领域……仅回答'是'或'否'"
   - 响应包含"是"→legal(0.7)，否则→nonLegal(0.7)
   - LLM 调用失败→默认放行 legal(0.5)

**降级原则**："宁可错放，不可错拦"——门控异常时放行到全 Agent 通道处理。

#### 3.1.2 Layer 2：IntentClassifierEnhanced 意图分类

**核心类**：`IntentClassifierEnhanced.java`

**七种意图类型**：

| 意图 | 说明 | 匹配关键词示例 |
|------|------|---------------|
| `ARTICLE_LOOKUP` | 法条查询 | "第几条规定"、"法条原文"、"查法条" |
| `CALCULATION` | 金额计算 | "赔偿多少钱"、"加班费怎么算" |
| `CASE_SEARCH` | 案例检索 | "有没有类似案例"、"判决书" |
| `DOCUMENT_DRAFTING` | 文书起草 | "帮我写起诉状"、"合同模板" |
| `LEGAL_KNOWLEDGE` | 法律知识问答 | "什么是"、"名词解释" |
| `LEGAL_CONSULTATION` | 兜底：法律咨询 | 不匹配上述任何类别 |

**路由建议** (`suggestRoute`)：

根据意图类型判断建议路由：
- `fastIntents`：ARTICLE_LOOKUP、LEGAL_KNOWLEDGE
- `hybridIntents`：DOCUMENT_DRAFTING
- `agentIntents`：LEGAL_CONSULTATION、CALCULATION、CASE_SEARCH

#### 3.1.3 Layer 3a：ComplexityAssessor 复杂度评估

**核心类**：`ComplexityAssessor.java`

**四因子加权模型**：

```
总评分 = 涉及法律数量 × 0.4 + 是否需要计算 × 0.2 + 子句数量 × 0.2 + 是否涉及程序 × 0.2
```

| 因子 | 权重 | 评分方法 |
|------|------|----------|
| 涉及法律数量 | 40% | 匹配复杂法律关键词（刑法、知识产权、破产……），0次=0.0，1次=0.3，每多1个+0.2，上限1.0 |
| 是否需要计算 | 20% | 检测金额模式 + 计算关键词（赔偿、补偿、加班费……），两者都存在=1.0，一个存在=0.6 |
| 子句数量 | 20% | 按句号/问号/分号分割，1句=0.0，2句=0.3，3句=0.6，4+句=1.0 |
| 是否涉及程序 | 20% | 匹配程序关键词（起诉流程、管辖权、上诉……），每命中一次+0.33，上限1.0 |

**分级**：
- `≤ 0.35` → **SIMPLE**
- `0.35 ~ 0.65` → **MEDIUM**
- `≥ 0.65` → **COMPLEX**

#### 3.1.4 Layer 3b：IntentRouter 路由决策

**核心类**：`IntentRouter.java`

**决策矩阵**：

| 意图类型 | SIMPLE | MEDIUM | COMPLEX |
|----------|--------|--------|---------|
| ARTICLE_LOOKUP | FAST (300 token) | FAST (300 token) | **AGENT (升级)** |
| LEGAL_KNOWLEDGE | FAST (300 token) | FAST (300 token) | **AGENT (升级)** |
| LEGAL_CONSULTATION | FAST (降级, 500 token) | AGENT (1500 token) | AGENT (3000 token) |
| CALCULATION | FAST (降级, 500 token) | AGENT (800 token) | AGENT (1600 token) |
| CASE_SEARCH | FAST (降级) | AGENT (1200 token) | AGENT (2400 token) |
| DOCUMENT_DRAFTING | HYBRID | HYBRID | HYBRID |

**关键逻辑**：
- **升格**："意图简单但问题复杂"（如法条查询涉及复杂法律）
- **降格**："简单法律咨询，快速通道处理"（节省 Agent 开销）

---

### 3.2 快慢分流策略

#### 3.2.1 Fast Channel（快速通道）

**核心类**：`FastChannelHandler.java`

**适用场景**：简单法条查询、法律知识问答

**处理流程**：

1. 根据意图确定 `topK`（ARTICLE_LOOKUP=10, CASE_SEARCH=8, 其他=5）
2. 关键词检索（不做向量搜索，不做 Rerank，不做 MMR）
3. 构建精简提示（~15 行，对比 Agent 的 ~85 行系统提示）
4. 单次 LLM 调用（无工具，无 ReAct 循环）

**系统提示**（`FAST_SYSTEM_PROMPT`）：无工具函数描述、无 ReAct 框架、无工具使用策略——只要求加上法条引用和标准输出格式。

#### 3.2.2 Agent Channel（代理通道）

**适用场景**：复杂法律咨询、金额计算、案例检索

**处理流程**：完整的 ReAct 循环（最多 5 次迭代）+ 6 个工具可用 + 记忆注入 + 上下文压缩

#### 3.2.3 Hybrid Channel（混合通道）

**适用场景**：文书起草

**处理流程**：模板填充 + 可选检索，保留结构化输出的灵活性。

#### 3.2.4 分流收益

| 维度 | Fast Channel | Agent Channel |
|------|-------------|---------------|
| 平均 LLM 调用次数 | 1 次 | 2-6 次 |
| 平均耗时 | < 2 秒 | 3-15 秒 |
| Token 消耗 | ~500 | ~2000-6000 |
| 可用工具 | 0 | 6 个 |
| 适用占比 | ~60% 的请求 | ~30% 的请求 |

---

### 3.3 ReAct 循环实现

**核心类**：`AgentRunner.java`

#### 3.3.1 核心循环结构

```java
for (int iteration = 0; iteration < maxIterations; iteration++) {
    // 1. 上下文压缩检查
    if (contextCompressor.needsCompression(messages)) {
        // 构建压缩上下文，直接生成最终答案，退出循环
    }

    // 2. LLM 调用（带工具规范）
    AiMessage response = chatLanguageModel.generate(messages, toolSpecifications);

    // 3. 工具执行
    if (response.hasToolExecutionRequests()) {
        for (ToolExecutionRequest request : response.toolExecutionRequests()) {
            String result = executeTool(request);
            // 工具结果压缩
            String compressed = contextCompressor.compressToolResult(toolName, result, messages, iteration);
            messages.add(ToolExecutionResultMessage.from(toolName, compressed));
        }
        continue; // 下一轮迭代
    }

    // 4. 最终答案
    if (response.text() != null && !response.text().isEmpty()) {
        triggerMemoryExtraction(userId, sessionId, messages);
        return AgentResult.success(response.text(), messages);
    }
}

// 5. 循环耗尽 → 压缩 + 强制最终答案
```

**关键参数**：`maxIterations = 5`

#### 3.3.2 工具发现与注册

**实现在**：`AgentRunner.registerTools()`

通过 Java 反射扫描 `@Tool` 注解：

1. 调用 `ToolSpecifications.toolSpecificationsFrom(toolObject)` 生成工具规范
2. 反射遍历 `getDeclaredMethods()` 寻找 `@Tool` 注解
3. 解析工具名称（`@Tool.name()` 优先于 `method.getName()`）
4. 解析参数名称（`@P` 注解值，`-parameters` 编译标志）
5. 构建 `ToolMethod` 记录（instance + Method + paramNames）存入 `toolRegistry`

#### 3.3.3 两个记忆注入点

**注入点 1 — Agent 执行前**（第 175-180 行）：
```java
String memoryContext = memoryManager.retrieveAndFormat(userId, userQuestion);
enrichedPrompt = effectiveSystemPrompt + "\n" + memoryContext;
```
用户的角色、偏好、历史反馈、近期事项被追加到系统提示末尾，让 Agent 在"有上下文"的状态下开始推理。

**注入点 2 — Agent 成功后**（3 个返回点）：
```java
triggerMemoryExtraction(userId, sessionId, messages);
```
Agent 的对话记录通过 `@Async` 异步提取为记忆，不阻塞用户响应。

#### 3.3.4 可空性设计

`MemoryManager`、`ContextCompressor` 在整个调用链中均可空。当它们为 `null` 时（例如记忆系统配置为 disabled），Agent 行为与原先完全一致。这意味着所有增强功能都是"可插拔"的。

---

### 3.4 工具系统

#### 3.4.1 六个可用工具

| 工具名 | 所在类 | 功能 |
|--------|--------|------|
| `searchLawKnowledge` | LawSearchTools | 检索法律知识库，支持可选的法律类型过滤 |
| `getArticleText` | LawSearchTools | 根据法律名称和条款号查询法条原文 |
| `verifyCitation` | LawVerificationTools | 对答案中引用的法条进行核实 |
| `classifyLegalIntent` | LawIntentTools | 分析用户问题的法律意图类型 |
| `expandLegalQuery` | LawIntentTools | 对原始查询进行法律术语扩展 |
| `retrieveMemory` | RetrieveMemoryTool | 获取指定记忆的详细内容 |

#### 3.4.2 工具调用流程

```
LLM 返回 ToolExecutionRequest
  → executeTool(request)
    → 从 toolRegistry 查找 ToolMethod
    → resolveArgs() 解析 JSON 参数
    → method.invoke(instance, args) 反射调用
    → 记录工具执行时间
    → 返回结果字符串
  → contextCompressor.compressToolResult() 压缩结果
  → 将 ToolExecutionResultMessage 追加到对话历史
```

#### 3.4.3 工具设计模式

所有工具遵循统一契约：

1. 方法返回 `String`（LLM 友好格式）
2. 所有异常被 catch 并返回用户友好的错误消息（不抛出到 Agent 循环）
3. 参数通过 `@Tool` 注解的字符串值提供语义描述
4. 参数通过 `@P` 注解为 LLM 提供自然语言说明

---

## 4. 上下文压缩系统

### 4.1 渐进式四级压缩

**核心类**：`ContextCompressor.java`

这是解决 ReAct 循环中"上下文膨胀"问题的关键设计。每次工具调用都会产生大量结果文本，5 轮下来很容易超过模型的上下文限制。

**CompressionConfig 核心配置**（来自 `application.yml`）：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `enabled` | true | 全局开关 |
| `single-result-threshold` | 500 token | 单条工具结果触发压缩的阈值 |
| `total-context-threshold` | 6000 token | 全局消息列表触发压缩的阈值 |
| `min-savings-ratio` | 2.0 | Layer 2 触发的最小节省比率 |

#### 4.1.1 Layer 0：格式美化

**方法**：`applyLayer0()`

零成本压缩，仅做格式清理：
- 移除 Markdown 分隔线 `---`
- 压缩 3 个以上连续换行为 2 个
- 去除首尾空格

**适用场景**：较短的检索结果（< 500 token），仅去除无意义的格式噪声。

#### 4.1.2 Layer 1：规则提取

**方法**：`applyLayer1()` → `RuleExtractor.extract()`

零 LLM 成本，使用正则表达式提取结构化信息：

| 提取类型 | 正则模式 |
|----------|----------|
| 法条引用 | `《(.+?)》` + `第([...])条` |
| 金额 | `[\d,]+\.?\d* 元/万元` + 法律上下文 |
| 时效 | `\d+ 年/月/日` + (时效/仲裁/诉讼/申请) |

**输出格式**：
```
【法条引用】
《劳动合同法》第47条 | 经济补偿按劳动者在本单位工作的年限...

【金额/计算公式】
赔偿金额 | 月工资 × 工作年限 | 来源: searchLaw(R2)

【时效信息】
1年 仲裁时效 | 来源: searchLaw(R3)
```

#### 4.1.3 Layer 2：LLM 语义压缩

**方法**：`applyLayer2()` → `SummarizingCompressor.summarize()`

**触发条件**（保守触发）：
- 估算节省量 > 压缩成本 × 2.0（minSavingsRatio）
- 原始文本 ≥ 400 token
- 如果原始 < 400 token，回退到 Layer 1

**压缩提示**：将 LLM 定位为"法律文本精简专家"，要求：
- 必须保留：法条编号、适用条件、法律后果、金额公式变量
- 可以删除：重复论述、"根据XX法规定"引导语、案例背景故事
- 输出要求：每条一行，不改写法条原文措辞，总字数不超过原文 40%

**验证**：压缩后 token 数 ≥ 原始 token 数时，丢弃压缩结果，返回原文。

#### 4.1.4 Layer 4：全局上下文折叠

**方法**：`buildFinalContext()` / `buildFinalMessages()`

当 **两种情况之一** 触发：
1. 全局消息 token 超限（> 6000）：`needsCompression()` 返回 true，Agent 立即退出循环
2. ReAct 循环达到 maxIterations（5 轮）

**行为**：
- 调用 `KnowledgeState.toCompactSummary()` 生成结构化知识总结
- 构建 4 条消息的精简窗口：系统提示 + 用户问题 + 知识总结 + "给出最终回答"
- LLM 单次调用生成最终答案

---

### 4.2 KnowledgeState：结构化知识状态

**核心类**：`KnowledgeState.java`

这是压缩系统的核心创新——一种跨轮次、跨工具的增量知识去重机制。

#### 4.2.1 数据结构

维护四个独立的知识类别：

| 类别 | 存储类型 | 内容 |
|------|----------|------|
| `articles` | `List<ArticleEntry>` | 法律条文及其引用来源 |
| `calculations` | `List<CalcEntry>` | 金额计算相关信息 |
| `reminders` | `List<String>` | 时效与程序提醒 |
| `cases` | `List<CaseEntry>` | 参考案例 |

#### 4.2.2 增量摄取 (`ingest`)

每次工具结果压缩时调用，自动提取法律原子：

1. **法条提取** (`extractArticles`)：正则匹配条款号，向后搜索最近的书名号法律名称，提取 120 字符关键规则文本
2. **计算提取** (`extractCalculations`)：匹配金额模式，提取 ±20 字符上下文
3. **时效提取** (`extractDeadlines`)：匹配 `\d+年/月/日 + (时效/仲裁/诉讼)` 模式
4. **案例提取** (`extractCases`)：匹配关键词（判决书、案例、判例），提取前 200 字符

#### 4.2.3 去重与合并

**法条去重** (`findArticle`)：通过 `lawName + articleNumber` 精确匹配。若已存在：
- `citeCount` 递增（引用次数 +1）
- `sources` 合并（记录哪个工具在哪个轮次引用了此法条）
- `verified` 取 OR（一个来源确认即视为确认）

**容量驱逐** (`evictIfNeeded`)：当 articles 数量超过 `maxArticles`（默认 20）时，移除引用次数最少的条目。

#### 4.2.4 最终格式 (`toCompactSummary`)

```
## 知识总结

### 相关法条（按引用频次排序）
《劳动合同法》第47条 | 经济补偿按劳动者在本单位工作的年限... | 引用3次 | 已核实
《劳动合同法》第87条 | 用人单位违反本法规定解除... | 引用2次 | 待核实

### 金额计算
赔偿金额 | 月工资 × 工作年限 | 来源: searchLaw(R2)
N+1 补偿 | 代通知金 | 来源: calcDamages(R3)

### 时效与程序提醒
1年 仲裁时效
```

---

### 4.3 按工具定制的压缩策略

**配置位置**：`application.yml` 的 `lawmind.agent.compression.tool-strategies`

| 工具 | 压缩层 | compress | maxResults | fullDetailTop | 保留原文词 |
|------|--------|----------|------------|---------------|-----------|
| `searchLawKnowledge` | Layer 1 | true | 5 | 3 | 否 |
| `getArticleText` | Layer 0 | true | 0 | 0 | **是** |
| `classifyLegalIntent` | Layer 0 | false | 0 | 0 | 否 |
| `expandLegalQuery` | Layer 0 | false | 0 | 0 | 否 |
| `verifyCitation` | Layer 0 | false | 0 | 0 | 否 |

**设计原则**：

- `searchLawKnowledge`：检索可能返回多条，压缩到 top 5，保留前 3 条完整详情
- `getArticleText`：法条原文必须精确，`preserveOriginalTerms=true` 确保引用准确
- `classifyLegalIntent` / `expandLegalQuery`：结果本身很短，无需压缩
- `verifyCitation`：核实结果本身很短，无需压缩

---

### 4.4 近因加权策略

**配置**：`RecencyConfig(keepFullRecent=2, layer1StartRound=3, layer2StartRound=5)`

| 轮次 | 压缩强度 | 说明 |
|------|----------|------|
| 1-2 | 无压缩（结果 < 1000 token 时） | 保留最新工具的完整结果 |
| 3-4 | Layer 1 规则提取 | 开始结构化精简较早的结果 |
| 5+ | Layer 2 LLM 语义压缩 | 积极压缩历史结果 |

**设计直觉**：越近的检索结果，越可能对当前推理有价值。越早的检索结果，越应该被提炼为结构化摘要。这是一种"时间衰减"的压缩策略。

---

## 5. 记忆系统

### 5.1 四种记忆类型

**定义位置**：`MemoryType.java` enum

采用与 Claude Code 一致的四类型记忆模型：

| 类型 | 枚举值 | 含义 | 示例 |
|------|--------|------|------|
| 用户画像 | `USER` | 用户身份、知识水平、长期偏好 | "HR 经理，法律知识中等"、"偏好表格形式" |
| 历史反馈 | `FEEDBACK` | 用户纠正、补充的新信息 | "用户纠正：试用期最长 6 个月" |
| 近期事项 | `PROJECT` | 咨询问题与结论 | "用户咨询了劳动纠纷赔偿问题" |
| 相关参考 | `REFERENCE` | 常用法条引用、指导案例案号 | "《劳动合同法》第 47 条（经济补偿）" |

**统一存储表**：`ai_memory`（MySQL），`type` 字段使用枚举区分。

**Redis 向量索引**：`idx:memory`，1536 维，COSINE 距离，key 前缀 `memory:vector:`。

---

### 5.2 两级混合检索

**核心类**：`MemoryRetriever.java`

#### 5.2.1 Level 1：索引层（重要性排序）

**方法**：`findTopByImportance(userId, maxIndexItems)`
- 按重要性降序取 top 30
- 格式化为 ~200 token 的索引
- 始终注入到系统提示中

**索引格式**：
```
## 用户记忆索引

### 用户画像 (USER)
- [M1] HR经理，法律知识中等 ★
- [M2] 偏好阅读法条原文

### 近期事项 (PROJECT)
- [M3] 审查劳动合同，发现竞业限制问题 ★

Agent 可调用 retrieveMemory(记忆ID) 获取详细内容。
```

`★` 标记重要性 ≥ 0.8 的高价值记忆。

#### 5.2.2 Level 2：语义层（向量相似度）

**方法**：`searchByVector(queryVector, null, maxAutoInject, similarityThreshold)`
- 将当前问题向量化
- 在 Redis 中做语义相似度检索
- 过滤条件：相似度 ≥ 0.7，最多 3 条
- 返回详情（优先显示 `summary`，无 summary 则显示 `body`）

**详情格式**（~600 token 预算）：
```
## 用户记忆（来自历史交互）

### 用户画像
已记忆: 偏好表格形式返回赔偿标准

### 历史反馈
已记忆: 试用期上限依据《劳动合同法》第19条（6个月）
```

#### 5.2.3 总 Token 预算

| 层级 | 预算 | 内容 |
|------|------|------|
| 索引层 | ~200 token | 所有重要记忆的标题行 |
| 语义详情层 | ~600 token | 语义匹配记忆的详细内容 |
| **总计** | **~800 token** | 确保不挤压推理所需的上下文空间 |

---

### 5.3 LLM 驱动的记忆提取

**核心类**：`MemoryExtractor.java`

#### 5.3.1 提取触发

Agent 回答成功后，异步触发（`@Async`），不阻塞用户响应。

#### 5.3.2 提取 Prompt 设计

将 LLM 定位为"记忆管理分析师"，下发详细的提取规范：

**排除规则**（明确禁止提取）：
1. 合同原文、具体金额、身份证号、手机号（PII 保护）
2. 知识库已有的法条原文（避免冗余）
3. AI 推理过程和技术细节（无跨会话复用价值）
4. 问候语和感谢语

**重要性评估标准**：

| 类型 | 高重要性（8-10） | 低重要性（1-3） |
|------|------------------|-----------------|
| USER | 角色+知识水平 | 格式偏好 |
| FEEDBACK | 纠正法律错误 | 补充一般信息 |
| PROJECT | 发现重大风险 | 简单查询 |
| REFERENCE | 关键案例 | 常用法条 |

**输出格式**：JSON（禁止 Markdown 代码块）

#### 5.3.3 冲突检测

**方法**：`detectAndResolveConflicts()`

简化的冲突检测（基于标题字符重叠）：
- 提取相同用户+类型的现有记忆
- 计算标题共同字符占比
- 相似度 > 50% → 冲突
- 冲突处理：增加现有记忆置信度 +0.1，合并来源会话 ID，**丢弃新记忆**

---

### 5.4 按类型衰减与容量管理

#### 5.4.1 类型特定衰减

**配置**（来自 `application.yml` 的 `lawmind.memory.decay`）：

| 类型 | 衰减触发天数 | 设计理由 |
|------|-------------|----------|
| `PROJECT` | 30 天 | 项目/合同有时间性，过期后价值降低 |
| `REFERENCE` | 60 天 | 法条引用可能过时（修法） |
| `FEEDBACK` | 90 天 | 反馈有一定的持续性 |
| `USER` | 180 天 | 用户画像相对稳定，半年后才考虑衰减 |

#### 5.4.2 容量管理 (`enforceCapacity`)

- 每用户上限：200 条（`maxPerUser`）
- 超限时删除重要性最低的记忆
- 在每次记忆提取前检查，确保不超出容量

---

## 6. 工程化基础设施

### 6.1 AOP 横切关注点

#### 6.1.1 安全审计切面 (`SecurityAuditAspect.java`)

**注解驱动**：`@SecurityAudit(operationType, description, resourceType, logParams)`

**审计字段**：
- 操作人 (`userId`)、操作类型、资源类型、请求 ID
- 请求方法、URI、客户端 IP（检查 10 个标准代理头）
- 资源 ID（从方法参数自动提取）
- 处理结果（SUCCESS / FAIL + errorMessage）

**持久化保证**：`finally` 块中写入 `security_audit_log` 表，即使被审计的方法抛出异常也不会丢失日志。

#### 6.1.2 控制器日志切面 (`ControllerLogAspect.java`)

- 切入点：`execution(* com.lhs.lawmind.controller..*(..))`
- `@Order(10)` 优先级
- 支持 `@NoLog` 跳过，`@Log` 定制日志行为
- MultipartFile 参数格式化为文件名+大小（不打印二进制内容）

#### 6.1.3 调度器日志切面 (`SchedulerLogAspect.java`)

为定时任务（`@Scheduled`）提供统一日志，记录执行时间、成功/失败状态。

---

### 6.2 RAG 评估体系

#### 6.2.1 Golden Dataset 评估

**核心类**：`GoldenDatasetEvaluator.java`

**数据集格式**（`docs/golden-dataset-rag-evaluation.json`）：

每条 record 包含：
- `question`：测试问题
- `expectedSource`：期望来源类型（law_knowledge / non_legal_reject / llm_direct）
- `expectedKeywords`：期望回答包含的关键词列表
- `expectedLawType`：期望关联的法律类型
- `forbiddenContent`：禁止出现的内容（逗号分隔）
- `minRetrievalCount`：最少应检索到的结果数

**6 个内置指标**：

| 指标 | 方法 | 评分逻辑 |
|------|------|----------|
| 来源匹配 | `evaluateSourceMatch()` | 实际来源 == 期望来源 → 1.0 |
| 关键词召回 | `evaluateKeywordRecall()` | 命中关键词数 / 总关键词数 |
| 法律类型匹配 | `evaluateLawTypeMatch()` | 检索结果标题包含期望法律类型 |
| 最小长度 | 直接比较 | 回答长度 ≥ 50 字符 |
| 禁止内容检查 | `evaluateForbiddenContent()` | 回答不包含任何禁止术语 |
| 最小检索数 | `evaluateMinRetrievalCount()` | 检索结果数 ≥ 最小要求 |

#### 6.2.2 RAGAS 双维度评估

**核心类**：`RagasEvaluationService.java`

集成外部 RAGAS 评估框架，提供两个维度的语义评估：

- **Faithfulness（忠实度）**：LLM 回答是否完全忠实于检索到的上下文，有无编造
- **Answer Relevance（回答相关性）**：回答是否紧扣用户问题

**集成方式**：每条 record 同时跑内置指标和 RAGAS 评估。RAGAS 失败时回退为 0.0，不中断整体评估。

#### 6.2.3 持久化与趋势追踪

评估报告通过 `EvalReportPersistenceService` 写入 `eval_report_record` 表。每次评估生成独立的 `EvaluationReport`，包含所有单条 `EvalResult` 的聚合数据，支持追踪每次代码改动后的质量变化。

---

### 6.3 Redis Stack 向量存储方案

#### 6.3.1 向量索引架构

项目中使用 RediSearch 的向量能力，创建三种向量索引：

| 索引名 | Key 前缀 | 维度 | 距离度量 | 用途 |
|--------|----------|------|----------|------|
| `idx:law_knowledge` | `law:vector:` | 1536 | COSINE | 法律知识库向量 |
| `idx:memory` | `memory:vector:` | 1536 | COSINE | 记忆向量 |

#### 6.3.2 向量存储格式

**核心类**：`RedisVectorUtil.java`

- 向量以 **FLOAT32 小端字节数组** 存储在 Redis Hash 的 `vector` 字段中
- 每个 knowledge/memory 对象存储为独立的 Redis Hash
- 关联的元数据字段（如 `memory_id`、`title` 等）存储在同一 Hash 中

#### 6.3.3 向量检索

**核心方法**：`RedisVectorUtil.searchSimilar()`

```bash
FT.SEARCH idx:law_knowledge
  "(*)=>[KNN $K @vector $query_vector AS score]"
  PARAMS 4 query_vector <1536维float32字节数组>
  SORTBY score
  LIMIT 0 $K
  RETURN 1 score
```

返回 `List<SearchResult>`，其中 `SearchResult` 包含 key 和 score（余弦距离），通过 `cosineDistanceToSimilarity()` 转换为相似度。

#### 6.3.4 索引生命周期管理

- **启动时创建**：`RedisIndexInitializer`（CommandLineRunner）检查索引是否存在，不存在则创建
- **检查工具**：`RedisIndexUtil.indexExists(redisTemplate, indexName)`
- **监控接口**：通过 `RedisIndexManagementController` 提供索引重建等管理功能

---

## 7. 技术栈总结

| 技术层面 | 选型 | 说明 |
|----------|------|------|
| **语言 & 框架** | Java 17 + Spring Boot 3.5.12 | 利用 Java 17 的 Record、Sealed Class、Pattern Matching |
| **LLM 框架** | LangChain4j 0.36.0 | 统一 LLM 调用抽象，`@Tool` 注解扫描 |
| **LLM 模型** | qwen-plus（对话）、qwen-turbo（改写）、text-embedding-v2（向量化）、qwen3-rerank（精排） | 阿里 DashScope 全家桶 |
| **关系数据库** | MySQL 8.0 | 所有业务数据和向量元数据的主存储 |
| **向量数据库** | Redis Stack 7.4+ | RediSearch 向量索引，FLOAT32 小端字节存储 |
| **ORM** | MyBatis（纯 XML 映射） | 不使用 MyBatis-Plus，手工编写 SQL |
| **前端** | Vue 3 | 独立 `frontend/` 目录，Vite 构建 |
| **安全** | JWT 认证 + AOP 审计 | 多层安全防护 |
| **并发** | `@Async` + ThreadPoolExecutor | 记忆提取、访问记录全部异步化 |
| **评估** | Golden Dataset + RAGAS | 6 个内置指标 + 2 个语义指标 |

---

> 文档版本：v1.0 | 生成日期：2026-07-12 | 基于项目实际代码分析编写
