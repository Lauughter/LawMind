# 多级 RAG 检索管道

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`（旧散文档：检索策略模块详解 / RRF融合与BM25 / MMR算法说明，已分解合并入本文）`、源码 `RagServiceImpl.java`、`rag/` 包（RagRetrievalService / RagPromptBuilder / QueryEnhancer / CitationVerifier / LegalQuestionClassifier / SseStreamHelper）、`HybridSearchServiceImpl.java`、`RerankServiceImpl.java`、`SearchResultDiversifier.java`、`RagConfig.java`
> 关联：`overview.md`、`agent-orchestration.md`（Agent 工具复用本管道）、`evaluation.md`（检索质量评估）

---

## 一、模块范围

本模块负责「查询预处理 → 多路召回 → 融合排序 → 阈值过滤」的完整检索链路，编排器位于 `RagServiceImpl`，同步路径 `processQuestion()`、流式路径 `processQuestionStream()`（SSE）。Agent 通道的工具 `searchLawKnowledge` 复用同一 `HybridSearchService` 管道。

**MVP 边界**：不做知识图谱检索；RRF 仅融合 2 路（向量 + 全文），架构预留 N 路扩展。

| 子模块 | 涉及表/索引 | 说明 |
|--------|------------|------|
| 查询预处理 | — | 清洗、法律相关性、意图、实体、扩展、改写 |
| 混合搜索 | Redis `idx:law_knowledge` + MySQL `law_knowledge` | 向量 KNN + ngram 全文 → RRF |
| 精排 | — | Rerank + MMR + 双阈值 |
| 生成 | `ai_chat` | LLM 生成 + 引用验证 |

---

## 二、核心业务流程

```
用户问题
  │
  ▼
① sanitizeUserInput：移除 markdown 代码块 / 过滤注入模式 / 截断 2000 字符
  ▼
② isLegalRelatedQuestion：~200 法律关键词 + ~50 问题模式 → 非法律 → non_legal_reject
  ▼
③ TextPreprocessUtil.preprocess：去语气词(14) → 口语→术语 → 清理特殊字符 → 英文小写 → MD5
  ▼
④ IntentClassifier：6 类意图 → 调整 topK（ARTICLE_LOOKUP +5 / CASE_SEARCH +3 / CALCULATION -2 …）
  ▼
⑤ LegalEntityExtractor：lawType(120+ 词映射)、articleReference、amountText、partyType
  ▼
⑥ LegalQueryExpander（153 条口语→术语映射，追加式扩展）
  ▼
⑦ LLM 查询改写（qwen-turbo，LRU 缓存 500）失败→回退规则扩展；mergeQueries 合并
  ▼
⑧ Embedding（text-embedding-v2，1536 维）+ 可选 HyDE（生成假设法律分析文档再向量化）
  ▼
⑨ 混合搜索 searchHybridFiltered
      ├─ 路1：Redis KNN（idx:law_knowledge，FLAT COSINE，fetchSize=topK×2~4）
      ├─ 路2：MySQL 全文（MATCH(title,content) AGAINST('+t1 +t2' IN BOOLEAN MODE)，ngram）
      └─ RRF 融合（k=60）→ Min-Max 归一化到 [0,1]
  ▼
⑩ Rerank 精排（qwen3-rerank，候选截断，失败降级返回候选 subList）
  ▼
⑪ MMR 多样化（λ，lawType+chapter 三维结构相似度，贪心迭代）
  ▼
⑫ 双阈值过滤（高质量阈值 / 绝对最低阈值，边缘结果兜底）
  ▼
⑬ LLM 生成（qwen-plus）→ 引用验证 → 合规声明 → 异步后处理
```

---

## 三、业务规则

### 3.1 查询预处理规则

| 步骤 | 规则 | 示例 |
|------|------|------|
| 文本清洗 | 长度截断 2000 字符；过滤 `ignore previous instructions`、`system:`、DAN 等注入模式 | — |
| 法律相关性 | ~200 法律关键词 + ~50 问题模式（"是否合法""怎么维权"） | — |
| 术语扩展 | **153 条** 口语→正式术语映射，**追加式**扩展（不替换原问） | "被开除了"→"解除劳动合同 辞退 用人单位单方解除" |
| 意图→topK | 法条查询 +5（≥15 条）/ 案例 +3（≥13 条）/ 金额计算 -2（≥3 条） | — |
| 实体提取 | lawType 120+ 关键词映射到完整法律名；法条引用正则 `《XX法》第X条`；金额/当事人/时间 | "劳动法"→"中华人民共和国劳动法" |

### 3.2 混合召回规则

- **向量路**：`idx:law_knowledge`，1536 维 FLOAT32，COSINE；`similarity = max(0, min(1, 1 - distance/2))`；fetchSize = topK×4（有 lawType 过滤）/ topK×2（无过滤）；结果校验 `status=EFFECTIVE`。
- **全文路**：MySQL **ngram**（`ngram_token_size=2`）；查询切分为语义单元，保留"第X条"原子单元，过滤 < 2 字符词（`NGRAM_MIN_LENGTH`）与 46 个中文停用词；`+term` 表示必须包含（AND）。
- **查询模式降级**：查询 > 15 字符 → `NATURAL LANGUAGE MODE`；短查询 → `BOOLEAN MODE`；均失败 → `LIKE %term%` 兜底。

### 3.3 RRF 融合（k=60）

```
RRF_score(doc) = 1/(60 + rank_vector) + 1/(60 + rank_fulltext)
→ Min-Max 归一化到 [0,1]（兼容双阈值）
```

k=60 平滑前列差距，避免"排名第 1 垄断分数"。两路都命中的文档分最高（交叉验证）。

### 3.4 Rerank 精排

| 参数 | 默认(dev) | 说明 |
|------|----------|------|
| enabled | true | 是否启用 |
| model | qwen3-rerank | DashScope 精排模型 |
| top-n | 10 | 精排后返回数 |
| candidate-top-k | 30 | 送入精排的候选数（控成本） |

- 文档构建：拼接 `lawType + title + content`；API 失败 → 返回 `candidates.subList(0, topN)`，不中断。

### 3.5 MMR 多样化

```
MMR = λ × relevance(doc) − (1−λ) × max_similarity(doc, selected)
λ = 0.85（application.yml，默认 0.7）
```

| 文档相似度（离散三值，O(1)） | 值 |
|------------------------------|-----|
| 相同 lawType + 相同 chapter | 1.0 |
| 相同 lawType + 不同 chapter | 0.5 |
| 不同 lawType | 0.0 |
| 两者 chapter 均为 null | 1.0 |

贪心迭代 O(K×N²)，N≤20、K≤10 可接受。**在双阈值过滤之前执行**，避免单章节高分结果被过滤后其它结果失去入选机会。

### 3.6 双阈值过滤

| 阈值 | 当前(dev) | 代码默认 | 规则 |
|------|----------|----------|------|
| 高质量 `law-knowledge` | 0.55 | 0.75 | ≥ 阈值 → 直接采用 |
| 绝对最低 `filter` | 0.40 | 0.70 | 达标且 < 高质量 → 边缘结果，高质量为空时兜底；< 此值丢弃 |

> 说明：0.40~0.55 是边缘缓冲带，在精度与召回间取平衡。数值随 `application.yml` 调整，代码默认值见 `RagConfig`。

### 3.7 三级降级链（混合检索）

| 场景 | 向量检索 | 全文检索 | 最终效果 |
|------|:---:|:---:|------|
| 正常 | ✓ | ✓ | 完整 RRF 融合 |
| Embedding 服务挂 | ✗ | ✓ | 纯全文搜索 |
| 查询词全为单字/停用词 | ✓ | ✗ | 纯向量搜索 |
| 两者都挂 | ✗ | ✗ | 返回空，LLM 直答（llm_direct） |

### 3.8 生成与后处理

- **有知识**：`generateAnswerWithTokens()`（系统提示 + 检索知识 + 问题）；**无知识**：`generateDirectAnswerWithTokens()`（来源 `llm_direct`）。
- **流式**：`StreamingChatLanguageModel`（qwen-plus），SseEmitter 事件 `token/knowledge/done/error`。
- **引用验证**：正则 `(?:《([^》]{1,30})》)?\s*第([一二三四五六七八九十百千万零〇\d]+)条` → 与检索知识交叉比对（法律名+条文号，中文数字→阿拉伯转换）；未匹配追加 **"⚠ 注意：以下法律引用未经检索结果验证"**。
- **合规声明**：`law_knowledge`、`llm_direct` 来源自动附免责声明（`non_legal_reject` 跳过）。
- **异步**：记录访问、指标采集（`RagMetricsServiceImpl`）。

---

## 四、关键配置一览

```yaml
rag:
  vector:
    dimension: 1536
  threshold:
    law-knowledge: 0.55        # 高质量阈值
    filter: 0.40               # 绝对最低阈值
  search:
    top-k: 15
    hybrid: { enabled: true }
    mmr: { enabled: true, lambda: 0.85 }
    hyde: { enabled: false }
    rerank: { enabled: true, model: qwen3-rerank, top-n: 10, candidate-top-k: 30 }
  dedup: { enabled: true, threshold: 0.92 }
  citation: { verification: { enabled: true } }
  conversation: { max-history-messages: 10 }
```

---

## 五、关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 用 RRF 而非加权求和 | rank-based 融合（k=60）+ Min-Max 归一化 | 向量分与 BM25 分分布不同、量纲不可比；RRF 免校准、免训练、稳定 |
| fetchSize 放大 2–4 倍 | 向量取 topK×2~4，全文同量级 | RRF + MMR + 双阈值逐层淘汰，需足够候选保证最终仍有优质结果 |
| 双阈值设计 | 高质量 + 边缘兜底缓冲带 | 保证答案基于明确知识，同时避免阈值过高导致"无结果" |
| MMR 在阈值前执行 | 先多样化再过滤 | 防止单章节高分结果被过滤后，其它章节结果无入选机会 |
| Rerank 候选截断 | 仅送 top-30 进精排 | 控制 API 成本；失败降级为候选 subList，不中断 |
| HyDE 默认关闭 | `hyde.enabled: false` | 需额外一次 LLM 调用（1–3s + Token），仅对过短/口语化查询值得开启 |
| 中文全文用 ngram | `ngram_token_size=2` + BOOLEAN MODE | 中文无空格分词，bigram 兼顾召回与精确 |

---

## 六、实现进度

| 项 | 状态 |
|----|:---:|
| 查询预处理（清洗/相关性/意图/实体/153 条扩展/LLM 改写） | ✅ |
| 混合召回（Redis KNN + MySQL ngram）+ RRF | ✅ |
| Rerank 精排（qwen3-rerank） | ✅ |
| MMR 多样化 | ✅ |
| 双阈值过滤 + 三级降级链 | ✅ |
| 引用验证闭环 + 合规声明 | ✅ |
| HyDE 增强 | 🚧（代码已实现，默认关闭，视查询质量需求开启） |
