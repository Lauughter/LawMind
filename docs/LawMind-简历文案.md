# LawMind 项目简历文案

---

## 项目简介

LawMind 是一个面向法律领域的智能问答平台，基于 Spring Boot + LangChain4j 构建，实现了从文档检索到多轮 Agent 推理的完整链路。系统支持法律知识检索、法条查询、金额计算、合同审查、文书起草等场景，核心包含 RAG 多级混合检索管道、ReAct 多工具调用 Agent、上下文压缩、跨会话记忆系统等模块。

**项目周期**：5 个月（单人开发） ｜ **代码规模**：约 300 个 Java 源文件

---

## 主要技术栈

| 层级 | 技术选型 |
|------|----------|
| **框架** | Spring Boot 3.5、Java 17、Maven |
| **LLM** | LangChain4j 0.36 + 阿里 DashScope（qwen-plus / qwen3-rerank / text-embedding-v2） |
| **向量存储** | Redis Stack 7.4（RediSearch + FLOAT32 向量索引） |
| **数据库** | MySQL 8.0 + MyBatis（纯 XML 映射） |
| **前端** | Vue 3 + Vite + Axios + SSE 流式响应 |
| **安全与运维** | Spring AOP（限流/审计/日志）、JWT 认证、Sentinel 熔断降级、Nginx |

---

## 项目亮点

### 亮点一：多级 RAG 检索管道 —— 从混合召回到精排去重的完整链路

构建了「BM25 全文 + 向量语义 → RRF 融合 → Rerank 精排 → MMR 多样化 → 双阈值过滤」的五阶段检索管道。

- **混合召回**：Redis KNN 向量检索与 MySQL Ngram 全文搜索并行执行，通过 RRF（K=60）融合两路结果并做 Min-Max 归一化。全文搜索设计了三级降级链（BOOLEAN MODE → NATURAL LANGUAGE MODE → LIKE 搜索），任一分支故障不影响整体可用性，同时支持法律类型元数据过滤精确缩小检索范围。
- **多级精排**：接入 DashScope qwen3-rerank 模型对候选集重新评分，再通过 MMR 算法（λ=0.7）基于法律类型和章节结构做去重多样化，避免同一法条占据全部结果。最后经双阈值过滤（高质量 ≥0.55，绝对下限 ≥0.40），边缘结果作为后备，避免一定精度下丢失召回。
- **查询侧优化**：实现了三件套预处理——LegalEntityExtractor（纯规则提取法律类型、法条引用、金额等 5 类实体）、LegalQueryExpander（153 条口语→正式术语映射）、LLM 查询改写（qwen-turbo + LRU 缓存 500），合并后的查询同时驱动关键词搜索和向量检索。
- **法律文档分块**：设计了双策略分块——LegalArticleChunker 利用法律层级结构（编/章/节/条/款）构建文档树并附加出处前缀；FixedWindowChunker 实现 768 字符滑动窗口 + 智能断点（句号 > 分号 > 换行优先）。两块策略互补，分别服务于结构化法律文本和通用场景。

### 亮点二：多工具 ReAct Agent + 四级意图门控

实现了完整的 LangChain4j ReAct Agent，支持 7 个法律工具自主调用，并通过门控系统实现「快慢分流」。

- **Agent 循环**：最多 5 轮迭代的 ReAct 循环，每轮 LLM 返回 Thought + ToolCall 或 Final Answer。工具调用结果经上下文压缩后追加到对话历史，避免 token 膨胀。通过 Java 反射扫描 `@Tool` 注解自动构建工具注册表，生成 LangChain4j ToolSpecification 供 LLM 进行 function calling。
- **7 个法律工具**：searchLawKnowledge（带法律类型过滤）、getArticleText（精确法条原文）、classifyLegalIntent（意图分类 + 策略建议）、expandLegalQuery（术语扩展）、searchSimilarQuestions（历史相似问题）、verifyCitation（引用事实核查）、retrieveMemory（跨会话记忆按需查询）。所有工具方法返回 String 并捕获全部异常，确保工具调用失败不中断 Agent 循环。
- **四级门控流水线**：DomainGate（规则+LLM 兜底判断法律相关性）→ IntentClassifierEnhanced（7 类意图，规则匹配 0.9 置信度 / LLM 兜底 0.7）→ ComplexityAssessor（四因子加权模型：涉及法律数量×0.4 + 是否需要计算×0.2 + 子句数量×0.2 + 是否涉及程序×0.2）→ IntentRouter（意图×复杂度决策矩阵，支持升格/降格）。
- **快慢分流**：简单法条查询走 Fast Channel（单次 LLM 调用，无工具，平均 <2 秒）；复杂咨询走 Agent Channel（7 工具 ReAct 循环）；文书起草走 Hybrid Channel。预计 60% 的请求走快速通道，大幅节省 API 成本和响应延迟。

### 亮点三：渐进式上下文压缩 —— 解决 ReAct 循环中的上下文膨胀

设计了一套四层渐进压缩策略，解决多轮工具调用导致的上下文长度爆炸问题。

- **压缩层级递进**：Layer 0（去装饰空白，零成本）→ Layer 1（RuleExtractor 正则提取法条/金额/时效，零 LLM 成本）→ Layer 2（SummarizingCompressor LLM 语义压缩至原文 40%，带保守触发条件）→ 全局折叠（消息超 6000 token 或循环耗尽时用 KnowledgeState 结构化摘要替换全部对话）。
- **近因加权**：最近 2 轮保留完整内容，3-4 轮触发 Layer 1，5 轮以上触发 Layer 2，确保最新检索结果不损失精度。
- **按工具定制策略**：searchLawKnowledge 压缩到 top 5 保留前 3 详情；getArticleText 设置为 preserveOriginalTerms=true 确保法条原文精确；classifyLegalIntent 等短结果工具直接关闭压缩。
- **KnowledgeState 增量去重**：跨轮次/跨工具累积法律知识原子（法条/金额/时效/案例），通过 lawName+articleNumber 精确去重并统计引用次数，循环结束时输出按引用频次排序的结构化摘要，替换所有分散的工具结果。

### 亮点四：跨会话记忆系统 —— 让 Agent 拥有长期记忆

实现了类似 Claude Code 的四类型记忆模型，支持 LLM 自动提取和两级混合检索。

- **四类型记忆模型**：USER（用户画像）、FEEDBACK（用户纠正）、PROJECT（近期咨询事项）、REFERENCE（常用法条引用），统一存储在 MySQL 的 `ai_memory` 表，向量化后写入 Redis 向量索引（1536 维，COSINE 距离）。
- **两级检索策略**：Level 1 索引层按重要性取 Top 30 记忆，按类型分组注入系统提示（~200 token）；Level 2 语义层通过问题向量相似度检索匹配记忆（≥0.7，最多 3 条），显示详情（~600 token）。总计控制在 800 token 预算内。
- **LLM 自动提取 + 冲突检测**：Agent 每次会话结束后通过 @Async 异步调用 LLM 从对话中提取记忆（JSON 结构化输出），包含详细的提取/排除规则（如禁止提取合同原文、金额、PII）。冲突检测基于标题字符重叠（>50% 视为重复），重复时提升已有记忆置信度并合并来源。
- **类型特定衰减**：PROJECT 30 天、REFERENCE 60 天、FEEDBACK 90 天、USER 180 天，按记忆自然半衰期差异化淘汰。容量上限 200 条/用户，超限时驱逐重要性最低的记忆。

### 亮点五：法律领域深度适配 —— 让通用 RAG 变成「懂法律的 AI」

针对法律场景的特殊需求，在检索、生成、安全三个层面做了领域化定制，使系统输出从「通用问答」提升到「可引用的法律分析」。

- **法律文档专属分块**：通用分块器切法律文档会破坏条文层级关系——"第47条"可能被切成两半。LegalArticleChunker 解析法律文档的结构树（编→章→节→条→款），每条条文独立成块并自动附加出处前缀（如「《劳动合同法》第四章 劳动合同的解除和终止 第47条」），检索命中后可直接引用定位。配套 FixedWindowChunker（768 字符窗口 + 句号优先断点）处理非结构化法律文本，双策略互补。
- **查询侧法律化改造**：LegalEntityExtractor 纯规则引擎在 10ms 内从用户输入中提取 5 类法律实体——法律类型（匹配 13 个法律领域 115 个关键词）、法条引用（正则提取《X法》第Y条）、涉案金额、当事人类型（用人单位/劳动者/消费者/卖家）、时效信息。提取的法律类型直接作为检索过滤条件，将候选集从全库缩小到单一法律领域。LegalQueryExpander 维护 153 条口语→正式术语映射（如"被开除"→"解除劳动合同 辞退"，"N+1"→"经济补偿 代通知金 解除劳动合同 第四十七条"），弥合大众表达与法言法语之间的语义鸿沟。
- **引用验证闭环**：LLM 生成回答后，系统用正则提取回答中的所有法条引用（《X法》第Y条），逐条与检索到的知识库原文做包含检查。无法在知识库中找到依据的引用会被标记为 UNVERIFIED，追加警告提示。这一机制将 LLM 的法条幻觉风险从「事后发现」变成了「当场拦截」。
- **多层安全守卫**：SensitiveTopicFilter 在业务逻辑之前拦截敏感话题 → isLegalRelatedQuestion（200+ 法律关键词 + 50+ 问题模式）过滤非法律问题 → sanitizeUserInput 防御提示注入（过滤 "ignore previous instructions"、"system:" 等攻击模式，截断超长输入至 2000 字符）→ PiiUtil 检测并脱敏身份证号、手机号等隐私信息 → 合规声明自动附加（"以上内容由 AI 生成，仅供参考，不构成法律建议"）。五层递进，逐级收敛风险。
- **热点问题三层缓存**：不同于简单的「访问 N 次就缓存」，系统用三个独立时间窗口（5 分钟 3 次 / 1 小时 10 次 / 1 天 30 次）并行统计，任一阈值触发即自动升级为热点（TTL 30 天）。这种设计能同时捕捉「突发热点」（短时间集中访问）和「常青问题」（持续高频访问），命中后直接短路所有 LLM 调用。配合相似问题向量匹配（阈值 0.85 + 问题类型必须一致），热点覆盖率远超单阈值方案。

---

> 面试用文档 | 基于项目实际代码编写 | 详细技术文档见 `docs/LawMind-项目技术详解.md`
