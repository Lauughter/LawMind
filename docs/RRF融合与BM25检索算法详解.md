# RRF 融合 与 BM25 检索算法详解

## 背景

当你向 LawMind 搜索 "被开除后怎么维权" 时，系统并不是只用一种方式来检索，而是**同时跑两条检索管线**，然后把两边的结果"融合"起来，最终给你一个综合排名。这两条管线就是：

| 管线 | 技术 | 擅长 | 弱点 |
|------|------|------|------|
| **向量检索** | Embedding + KNN | 语义相似（"开除"="辞退"） | 精确关键词匹配弱 |
| **全文检索** | MySQL Ngram + Boolean Mode | 精确关键词命中 | 不懂同义词 |

两者互补后通过 **RRF（Reciprocal Rank Fusion）** 融合排名，这就是 **混合检索** 的核心。

本文档会分别讲清楚 BM25（全文检索的经典算法）、RRF（融合策略），以及它们在 LawMind 中的实际代码实现。

---

## 第一部分：BM25（全文检索算法）

### 什么是 BM25？

BM25（Best Match 25）是搜索引擎领域最经典的**基于关键词的排序算法**。它回答一个核心问题：

> 给定一个查询词，数据库里哪篇文档最相关？

BM25 的核心思想：**一个词在一篇文档中出现越频繁（TF），且这个词在整个数据库里越稀有（IDF），这篇文档就越相关。**

### BM25 公式

```
BM25(D, Q) = Σ [ IDF(qi) × TF(qi, D) ]

其中：

IDF(qi) = log( (N - n(qi) + 0.5) / (n(qi) + 0.5) + 1 )

                            f(qi, D) × (k1 + 1)
TF(qi, D)  = ──────────────────────────────────────────
              f(qi, D) + k1 × (1 - b + b × |D| / avgDL)
```

**参数说明：**

| 符号 | 含义 |
|------|------|
| `N` | 数据库中文档的总数 |
| `n(qi)` | 包含词 qi 的文档数 |
| `f(qi, D)` | 词 qi 在文档 D 中出现的次数（词频） |
| `\|D\|` | 文档 D 的总长度（字数） |
| `avgDL` | 所有文档的平均长度 |
| `k1` | 词频饱和度参数，通常 1.2-2.0 |
| `b` | 长度归一化参数，通常 0.75 |

### 逐项理解

#### 1. IDF（逆文档频率）—— "这个词有多稀有？"

```
IDF = log( (文档总数 - 包含这个词的文档数 + 0.5) / (包含这个词的文档数 + 0.5) + 1 )
```

- **"劳动合同"** → 几乎每篇文档都出现 → n(q) 很大 → IDF 很小 → **区分度低，不加分**
- **"竞业限制"** → 只有少数文档出现 → n(q) 很小 → IDF 很大 → **区分度高，加分多**

这就是为什么搜索 "竞业限制" 时不会先跑出来一堆 "劳动合同" 的文档。

#### 2. TF（词频）—— "这个词在这篇文档里出现了多少次？"

关键词法的 TF 不是简单地 "出现越多越好"，而是有**饱和度控制**：

```
                  f × (k1 + 1)
TF = ────────────────────────────────
      f + k1 × (1 - b + b × |D| / avgDL)
```

**饱和度现象：** 出现了 1 次和出现了 100 次的差距没有 100 倍那么大——曲线先陡后平。这防止了一篇异常长的文档垄断结果。

**长度归一化 `b`：** 同样的词频，长文档的 TF 被压低（因为长文档本身就更容易包含更多词），短文档的 TF 被抬高。这防止了长文档"占便宜"。

### LawMind 中用的不是 BM25 而是 Ngram

**MySQL 的 `MATCH...AGAINST IN BOOLEAN MODE` 在 InnoDB 引擎上内置的全文检索使用的就是 BM25 的变体**。但中文分词比较特殊——英文天然按空格分词，中文没有空格。

LawMind 的解决方式是用 **Ngram 分词器**（在 `V12_fulltext_ngram.sql` 中设置 `ngram_token_size=2`）：

```
原始文本: "解除劳动合同需要什么条件"

Ngram 分词 (token_size=2):
解 解除 除劳 劳动 动合 合同 同需 需要 要什 什么 么条 条件 件

```

每个相邻的两个字组成一个 token，这样中文就能被 MySQL 全文索引检索了。

### LawMind 中的 Boolean Mode 查询

在 `HybridSearchService.buildBooleanQuery()` 中：

```java
private String buildBooleanQuery(List<String> terms) {
    StringBuilder sb = new StringBuilder();
    for (String term : terms) {
        if (!sb.isEmpty()) sb.append(" ");
        String escaped = term.replaceAll("[+\\-><()~*\"@]", "");  // 转义特殊字符
        sb.append("+").append(escaped);  // + 表示"必须包含"
    }
    return sb.toString();
}
// 例如 terms=["解除","劳动合同"] → "+解除 +劳动合同"
```

`+` 前缀表示该词**必须出现**（AND 逻辑），这就是 `IN BOOLEAN MODE` 的语法。对应的 SQL：

```sql
SELECT * FROM law_knowledge
WHERE MATCH(title, content) AGAINST('+解除 +劳动合同' IN BOOLEAN MODE)
```

所以 LawMind 的全文检索实际上是 **Ngram 分词 + MySQL Boolean Mode + BM25变体内置排序**。

---

## 第二部分：RRF（倒数排名融合）

### 为什么需要 RRF？

混合检索的两条管线各自返回一份排名：

```
向量检索返回:       全文检索返回:
1. doc_A (0.92)    1. doc_C (BM25分高)
2. doc_B (0.89)    2. doc_A (BM25分中)
3. doc_D (0.85)    3. doc_E (BM25分低)
```

**问题：** 向量分数（0-1 余弦相似度）和全文分数（BM25，无上限）不在同一个量纲上，不能直接相加或比较。不能简单地 `0.92 + BM25分` 这样算。

### RRF 的核心思想

**不去管绝对值是多少，只看排名（rank）**。排第 1 就比排第 2 好，跟具体分数无关。

```
RRF_score(doc) =  Σ  1 / (k + rank_i(doc))
                 i∈{向量,全文}
```

**说明：**

| 符号 | 含义 |
|------|------|
| `rank_i(doc)` | 文档在第 i 路检索中的排名（1-based） |
| `k` | 平滑常数，防止 1/1 太大的极端情况，通常 k=60 |
| `Σ` | 对每路检索的分数求和 |

### 为什么用 1/(k+rank) 而不是 1/rank？

```
1 / 1  = 1.000
1 / 60 = 0.017   ← k=60 时，排名 1 的值被压到了 0.016

各排名对应的 RRF 贡献：
rank=1:  1 / (60+1)  = 0.01639
rank=2:  1 / (60+2)  = 0.01613    差距很小
rank=5:  1 / (60+5)  = 0.01538
rank=10: 1 / (60+10) = 0.01429
rank=50: 1 / (60+50) = 0.00909
```

k=60 让前列结果之间的差距非常小，避免了"排第 1 的文档垄断所有分数"。这是一个经验值，在各种数据集上表现稳定。

### 逐步演算

假设用户搜索 "被开除后怎么维权"，系统运行两条管线：

**向量检索结果：**

| 排名 | 文档 ID | 向量相似度 |
|------|---------|------------|
| 1 | 100 | 0.92 |
| 2 | 200 | 0.89 |
| 3 | 300 | 0.85 |
| 4 | 400 | 0.78 |
| 5 | 500 | 0.72 |

**全文检索结果（Ngram Boolean Mode）：**

| 排名 | 文档 ID | BM25 分数 |
|------|---------|-----------|
| 1 | 300 | 12.5 |
| 2 | 600 | 10.2 |
| 3 | 100 | 9.8 |
| 4 | 200 | 8.1 |
| 5 | 700 | 7.5 |

注意：文档 600 和 700 只在全文结果中出现（向量检索没命中），文档 400 和 500 只在向量结果中出现（全文检索没命中）。这就是**互补**。

**RRF 计算（k=60）：**

| 文档 ID | 向量排名 | 向量 RRF | 全文排名 | 全文 RRF | **总 RRF** |
|---------|----------|----------|----------|----------|------------|
| 100 | 1 | 1/61=0.01639 | 3 | 1/63=0.01587 | **0.03226** |
| 300 | 3 | 1/63=0.01587 | 1 | 1/61=0.01639 | **0.03226** |
| 200 | 2 | 1/62=0.01613 | 4 | 1/64=0.01563 | **0.03176** |
| 600 | — | 0 | 2 | 1/62=0.01613 | **0.01613** |
| 400 | 4 | 1/64=0.01563 | — | 0 | **0.01563** |
| 500 | 5 | 1/65=0.01538 | — | 0 | **0.01538** |
| 700 | — | 0 | 5 | 1/65=0.01538 | **0.01538** |

**最终 RRF 排名：**

```
1. doc_100 (0.03226) ← 两路都排名靠前，冠军！
2. doc_300 (0.03226) ← 与100同分，可并列
3. doc_200 (0.03176)
4. doc_600 (0.01613) ← 只在全文中命中，但排第2，捞回来了
5. doc_400 (0.01563) ← 只在向量中命中，也保留了
```

观察：
- doc_100 和 300 在两路都表现好 → **得分最高**（被两路交叉验证）
- doc_600 只在全文中出现，但排第 2 → 被 RRF **捞回**（向量遗漏但关键词强匹配）
- doc_400 只在向量中出现 → 也被保留（全文遗漏但语义匹配）

这就是 RRF 的最大优势：**不会因为某一路的"盲区"而丢掉好结果。**

---

## 第三部分：LawMind 中的代码实现

### 3.1 整体架构

```
用户查询 "被开除后怎么维权"
         │
         ▼
   LegalQueryExpander
   扩展 → "被开除后怎么维权 解除劳动合同 辞退 用人单位单方解除"
         │
    ┌────┴────┐
    ▼         ▼
 向量化      全文检索
    │         │
    ▼         ▼
 KNN搜索    Ngram Boolean
    │         │
    ▼         ▼
 向量排名    全文排名
    │         │
    └────┬────┘
         ▼
      RRF 融合
         │
         ▼
    分数归一化 (0-1)
         │
         ▼
      MMR 多样化
         │
         ▼
    双阈值过滤 (0.70/0.65)
         │
         ▼
    Top-K 结果 → LLM
```

### 3.2 HybridSearchService 关键代码

```java
// 在 HybridSearchService.searchHybrid() 中

// 步骤 1: 向量检索
Map<Long, Integer> vectorRanks = new LinkedHashMap<>();
List<RedisVectorUtil.SearchResult> vectorResults =
    lawKnowledgeRedisUtil.searchLawKnowledge(queryVector, fetchSize);
int rank = 1;
for (RedisVectorUtil.SearchResult vr : vectorResults) {
    vectorRanks.put(extractId(vr.getKey()), rank);
    rank++;
}

// 步骤 2: 全文检索 (MySQL Ngram + Boolean Mode)
Map<Long, Integer> fulltextRanks = new LinkedHashMap<>();
List<String> terms = splitSearchTerms(expandedQuery);
String booleanQuery = buildBooleanQuery(terms);
List<LawKnowledge> fulltextResults =
    lawKnowledgeMapper.searchFulltext(booleanQuery, 0, fetchSize);
rank = 1;
for (LawKnowledge ft : fulltextResults) {
    fulltextRanks.put(ft.getId(), rank);
    rank++;
}

// 步骤 3: RRF 融合
Map<Long, Double> rrfScores = computeRRF(vectorRanks, fulltextRanks);

// 步骤 4: 分数归一化到 0-1，兼容现有双阈值配置
normalizeScores(rrfScores);

// 步骤 5: 按 RRF 分数降序取 Top-K
```

### 3.3 RRF 融合的具体实现

```java
private static final int RRF_K = 60;

private Map<Long, Double> computeRRF(
        Map<Long, Integer> vectorRanks,
        Map<Long, Integer> fulltextRanks) {
    Map<Long, Double> scores = new LinkedHashMap<>();

    // 向量这一路: score = 1 / (60 + rank)
    for (Map.Entry<Long, Integer> e : vectorRanks.entrySet()) {
        scores.put(e.getKey(), 1.0 / (RRF_K + e.getValue()));
    }

    // 全文这一路: score = 1 / (60 + rank)，用 merge 累加到已有分数
    for (Map.Entry<Long, Integer> e : fulltextRanks.entrySet()) {
        scores.merge(e.getKey(), 1.0 / (RRF_K + e.getValue()), Double::sum);
    }

    return scores;
}
```

**关键细节：** 如果某文档只在向量结果中出现，`scores` 中只有向量那一路的分；如果两路都出现，`merge` 会把两路分加起来。

### 3.4 分数归一化

RRF 原始分数范围很小（约 0.009 到 0.033），直接用于双阈值（0.70/0.65）会全部被过滤。

```java
private void normalizeScores(Map<Long, Double> scores) {
    if (scores.isEmpty()) return;
    double minScore = scores.values().stream().min(Double::compare).orElse(0.0);
    double maxScore = scores.values().stream().max(Double::compare).orElse(1.0);
    double range = maxScore - minScore;
    if (range > 0) {
        for (Map.Entry<Long, Double> e : scores.entrySet()) {
            // Min-Max 归一化: (x - min) / (max - min)
            scores.put(e.getKey(), (e.getValue() - minScore) / range);
        }
    }
}
```

归一化后分数范围为 [0, 1]，可以直接用 `ragConfig.getLawKnowledgeThreshold()`（0.70）等阈值进行过滤。

### 3.5 降级策略

```java
boolean hasVector = queryVector != null && queryVector.length > 0;

// 向量可用 → 做向量检索；不可用 → 跳过
if (hasVector) {
    // ... 向量检索
}

// 查询词满足最小长度要求 → 做全文检索；不满足 → 跳过
if (!terms.isEmpty() && canUseFulltext(terms)) {
    // ... 全文检索
}
```

三种降级场景：

| 场景 | 向量检索 | 全文检索 | 最终效果 |
|------|----------|----------|----------|
| 正常 | ✓ | ✓ | 完整的 RRF 融合 |
| Embedding 服务挂了 | ✗ | ✓ | 降级为纯全文搜索 |
| 查询词全是单字 | ✓ | ✗ | 降级为纯向量搜索 |
| 两者都挂了 | ✗ | ✗ | 返回空，LLM 直接回答 |

---

## 第四部分：RRF vs BM25 vs 向量检索 对比总结

| 维度 | BM25（全文检索） | 向量检索（Embedding） | RRF（融合） |
|------|------------------|----------------------|-------------|
| 原理 | 关键词频率统计 | 语义向量距离 | 排名融合 |
| 同义词 | ✗ "开除"≠"辞退" | ✓ 语义相近能匹配 | 兼有两路 |
| 精确关键词 | ✓ 搜"第39条"精确命中 | 弱 可能被语义干扰 | 兼有两路 |
| 长文档偏好 | 自动长度归一化 | 无此问题 | — |
| 调参 | k1, b | 模型固定 | k（通常 60） |
| 分数含义 | 自身得分（无上限） | 余弦相似度 [0,1] | 排名融合分 |
| 冷启动 | 需要建立索引 | 需要向量化 | — |

**RRF 为什么不是简单取平均或加权？**

如果用 `0.7 × 向量相似度 + 0.3 × BM25归一化分` 来做加权融合，需要：
1. 对 BM25 分数做归一化（难，因为分布未知）
2. 确定权重（需要大量实验验证）

RRF 绕过了这些问题——它只关心**排名**，排名天然是可比较的（第 1 就是第 1，跟分数大小无关）。这也是为什么 RRF 在实践中被广泛采用：简单、稳定、不需要调参。

---

## 第五部分：延伸知识

### RRF 可以融合更多路

目前 LawMind 融合了 2 路（向量 + 全文），但 RRF 天然支持 N 路：

```
RRF(doc) = 1/(k+rank_vector) + 1/(k+rank_fulltext) + 1/(k+rank_knowledge_graph) + ...
```

未来如果要加入**知识图谱检索**或**法律法规层级检索**，直接在 `computeRRF()` 里加一路循环就行。

### BM25 与向量检索各有所长

BM25 的强项是**精确召回**——如果你的查询词恰好跟文档中的词匹配，BM25 不会漏。向量检索的强项是**语义泛化**——即使用词不同，只要意思相近就能匹配。

两者不是竞争关系，而是互补关系。RRF 就是让它们各展所长、分数可比的桥梁。

### k=60 为什么是 60？

来自 2009 年的一篇研究论文（Cormack et al.）。研究者实验发现 k=60 在各种数据集上表现最稳定。业界普遍接受这个默认值，对大多数场景不需要调整。
