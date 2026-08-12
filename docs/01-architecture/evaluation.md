# 质量评估体系

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现（A/B 测试 🚧）
> 事实源：`（旧文档：质量评估体系文档 / 质量评估体系优化方案，已分解合并入本文）`、源码 `D:\develop\Code\LawMind\src\main\java\com\lhs\lawmind\evaluation\`、`scripts\compare_eval_results.py`、`.github\workflows\rag-evaluation.yml`
> 关联：`overview.md`、`rag-retrieval.md`（被评估对象）、`observability.md`（在线质量指标）

---

## 一、模块范围

基于 **Golden Dataset** 的离线评估 + 用户反馈闭环 + 在线质量指标，确保每次 RAG 变更后质量可量化、可追溯、可拦截。

| 子模块 | 关键类/文件 | 涉及表 | 说明 |
|--------|------------|--------|------|
| Golden Dataset | `docs/golden-dataset-rag-evaluation.json`、`GoldenDatasetLoader` | — | 标注问答集（事实源路径） |
| 评估执行器 | `GoldenDatasetEvaluator` | — | 逐条跑 RAG + 多维度评分 |
| RAGAS 语义评估 | `RagasEvaluationService`、`FaithfulnessEvaluator`、`AnswerRelevanceEvaluator` | — | 忠实度 + 相关性 LLM 评分 |
| 报告持久化 | `EvalReportPersistenceService` | MySQL `evaluation_report` | 每次评估一行 + 完整 JSON |
| 反馈闭环 | `AiChatServiceImpl.triggerPostReviewActions` | `review_log`、`ai_chat` | 点踩→审核→追加入口/记缺口 |
| CI 回归门禁 | `scripts/compare_eval_results.py`、`.github/workflows/rag-evaluation.yml`、`GoldenDatasetEvaluatorTest` | — | 基线对比，回归拦截 |

---

## 二、核心业务流程

```
docs/golden-dataset-rag-evaluation.json（标注数据，可人工审核自动追加）
  │
  ▼
GoldenDatasetEvaluator.evaluate()（或指定 datasetPath）
  ├─ 逐条：ragService.processQuestion(1L, question, null) 真实调用 RAG 管道
  ├─ 6 项规则指标：来源匹配 / 关键词召回 / 法条类型匹配 / 最小长度 / 禁止内容 / 最小检索数
  ├─ 2 项 RAGAS 指标：faithfulness + answerRelevance（失败回退 0.0，不中断）
  └─ 综合得分 totalScore = (sourceMatch + keywordRecall + lawTypeMatch + answerMinLength) / 4
  │
  ▼
EvalReportPersistenceService.saveReport → evaluation_report（run_id + 各维度均值 + report_json）
  │
  ▼
管理端质量看板（GET /api/admin/metrics/quality/*）+ CI 基线对比（compare_eval_results.py）
  │
  ▼
反馈闭环：用户点踩 → 管理员审核 → ADD_TO_GOLDEN_DATASET / KNOWLEDGE_GAP / CONFIRMED_ISSUE
```

---

## 三、业务规则

### 3.1 Golden Dataset 记录结构

| 字段 | 类型 | 含义 |
|------|------|------|
| `id` | String | 唯一标识（`GR{timestamp}` 自动追加） |
| `question` | String | 测试问题 |
| `intent` | String | 意图分类 |
| `expected_law_type` | String | 期望关联法律类型 |
| `difficulty` | String | easy / medium / hard |
| `expected_answer_contains` | List | 回答应包含关键词 |
| `source_requirement` | String | 期望来源：law_knowledge/llm_direct/non_legal_reject |
| `forbidden_content` | String | 不应出现内容（逗号分隔，负向用例） |
| `min_retrieval_count` | Int | 最少检索条数 |

### 3.2 评估维度

| 维度 | 方法 | 权重/说明 |
|------|------|----------|
| 来源匹配 `sourceMatch` | 实际来源 == 期望来源 | 计入总分（硬性） |
| 关键词召回 `keywordRecall` | 期望关键词命中数 / 总数 | 计入总分 |
| 法条类型匹配 `lawTypeMatch` | 检索结果标题含期望法律类型 | 计入总分 |
| 最低长度 `answerMinLength` | 回答 ≥ 50 字符 | 计入总分 |
| 禁止内容 `forbiddenContentClean` | 不含禁止术语 | 单独统计 |
| 最小检索满足 `minRetrievalOk` | 检索条数 ≥ 要求 | 单独统计 |
| 忠实度 `faithfulness`（RAGAS） | LLM-as-judge 逐句判断是否可从上下文推断 | 单独统计 |
| 答案相关性 `answerRelevance`（RAGAS） | 基于回答反向生成问题 → 与原问题余弦相似度 | 单独统计 |

**通过标准**：`totalScore ≥ 0.5 → passed`，否则 `failed`。

### 3.3 报告持久化（`evaluation_report` 表）

`run_id`(UUID)、`dataset_path`、`dataset_version`、`total/passed/failed_cases`、`avg_keyword_recall`、`avg_source_match`、`avg_law_type_match`、`avg_answer_length`、`avg_total_score`、`avg_faithfulness`、`avg_answer_relevance`、`report_json`(LONGTEXT)、`created_at`。

### 3.4 反馈闭环

| 审核动作 | 触发匹配 | 自动动作 |
|----------|----------|----------|
| `ADD_TO_GOLDEN_DATASET` | `wrong_citation` / "引用的法条有误" | 追加 Golden Dataset（负面用例）+ 写 review_log |
| `KNOWLEDGE_GAP` | `inaccurate`/`irrelevant`/"回答不准确" | 标记知识缺口 + 写 review_log |
| `CONFIRMED_ISSUE` | 所有已确认问题 | 写 review_log |

管理员 API：`GET /api/admin/chat/review/list`（分页）、`POST /api/admin/chat/review`。`review_log` 表：`chat_id`、`question`(500)、`action_type`、`action_detail`、`feedback_reason`、`processed`、`processed_at`。

### 3.5 CI 回归门禁

- **触发**：`workflow_dispatch` 或 PR 修改 `RagServiceImpl.java` / `RagConfig.java` / `application.yml`。
- **步骤**：启动 Redis+MySQL → 迁移 V*.sql → `mvn test -P evaluation`（`@Tag("evaluation")`）→ 基线对比 → 上传报告制品（保留 30 天）。
- **基线对比**：`python scripts/compare_eval_results.py`（`--update-baseline` 更新基线）；容忍阈值 `keywordRecall/faithfulness/answerRelevance` 0.10，`sourceMatch/lawTypeMatch/answerMinLength/passedCases/totalScore/minRetrievalOk` 0.05，`forbiddenContentClean` 0.02；退出码 0 通过 / 1 回归 / 2 参数错误。
- **回归测试**：`GoldenDatasetEvaluatorTest` 要求关键词召回 ≥40%、综合得分 ≥0.4。

### 3.6 在线质量指标（metrics 采集）

| API | 说明 |
|-----|------|
| `GET /api/admin/metrics/today` | 请求量、缓存命中率、来源分布、延迟分解、P50/P95 |
| `GET /api/admin/metrics/quality/today` | LLM 兜底率、点赞/点踩、反馈原因分布 |
| `GET /api/admin/metrics/quality/trend?days=7/14/30` | 质量趋势（Redis 实时 + MySQL 回退） |
| `GET /api/admin/metrics/eval/reports?limit=20` | 评估报告历史 |

> 指标持久化细节见 `observability.md`。管理端 API 受 `lawmind.admin-user-id`（默认 4）权限控制。

---

## 四、关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 纯规则评分先行（Phase A） | 6 项规则指标不依赖外部 LLM | 40+ 条用例 <3 分钟，适合作 CI 门禁 |
| RAGAS 自建而非 Python 集成 | Java 侧实现 Faithfulness + Answer Relevance | 复用现有 qwen-plus 作 LLM-as-judge，避免跨语言维护成本 |
| 评估用真实 Spring 上下文 | `@SpringBootTest` + 真实 Embedding/Redis/LLM | 验证真实管道而非 mock |
| `@Tag("evaluation")` 隔离 | `-P evaluation` 才运行 | 评估慢（涉及 LLM 调用），不与快速单测混跑 |
| 反馈闭环自动化 | 审核确认后自动追加数据集/记缺口 | 让点踩数据真正反哺质量，而非停流在入库 |

---

## 五、实现进度

| 项 | 状态 |
|----|:---:|
| Golden Dataset（40+ 条）+ 自动追加 | ✅ |
| GoldenDatasetEvaluator（6 规则指标） | ✅ |
| RAGAS 双维度（faithfulness + answerRelevance） | ✅ |
| evaluation_report 持久化 + 历史趋势 | ✅ |
| review_log + 反馈闭环 + 管理员审核 API | ✅ |
| 在线质量指标 API + 质量看板 | ✅ |
| CI 回归门禁（workflow + 基线对比脚本 + 回归测试） | ✅ |
| Golden Dataset 扩展到 100–200 条 | 🚧（规划中） |
| A/B 测试（两套 Prompt/参数对比点赞率） | 🚧（路线图 Phase 5，DAU>1000 后做） |
| Context Precision / Recall 评估 | 🚧（需人工 ground_truth 标注） |
