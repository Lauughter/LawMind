# evaluation_report（评估报告表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：质量评估模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：存储一次 RAG 质量评估运行的汇总报告（通过率、召回率、忠实度、相关性等指标 + 完整 JSON），用于离线评测与回归。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，报告 ID |
| run_id | VARCHAR(64) | 否 | — | 评估运行 ID（唯一标识一次评测） |
| dataset_path | VARCHAR(500) | 是 | NULL | 数据集路径 |
| dataset_version | INT | 是 | NULL | 数据集版本 |
| total_cases | INT | 是 | 0 | 总用例数 |
| passed_cases | INT | 是 | 0 | 通过用例数 |
| failed_cases | INT | 是 | 0 | 失败用例数 |
| avg_keyword_recall | DECIMAL(5,4) | 是 | NULL | 平均关键词召回率 |
| avg_source_match | DECIMAL(5,4) | 是 | NULL | 平均来源匹配率 |
| avg_law_type_match | DECIMAL(5,4) | 是 | NULL | 平均法律类型匹配率 |
| avg_answer_length | DECIMAL(10,2) | 是 | NULL | 平均回答长度 |
| avg_total_score | DECIMAL(5,4) | 是 | NULL | 平均总分 |
| avg_faithfulness | DECIMAL(5,4) | 是 | NULL | 平均忠实度 |
| avg_answer_relevance | DECIMAL(5,4) | 是 | NULL | 平均回答相关性 |
| report_json | JSON | 是 | NULL | 完整报告 JSON |
| created_at | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_run_id | 普通 | run_id | 按运行 ID 定位报告 |
| idx_created_at | 普通 | created_at DESC | 报告时间倒序列表（最近评测） |

## 枚举

无枚举字段。指标语义：

| 指标 | 含义 |
|------|------|
| avg_keyword_recall | 关键词召回率均值（检索覆盖度） |
| avg_source_match | 来源匹配率均值（命中来源与标准答案一致性） |
| avg_law_type_match | 法律类型匹配率均值 |
| avg_answer_length | 平均回答长度 |
| avg_total_score | 综合总分均值 |
| avg_faithfulness | 忠实度均值（回答是否忠于引用内容） |
| avg_answer_relevance | 回答相关性均值 |

## 业务规则

- **运行模型**：一次评估运行生成一份报告；`run_id` 关联数据集（`dataset_path` + `dataset_version`），同数据集可跑多版对比。
- **结果自检**：`total_cases = passed_cases + failed_cases`；通过/失败由用例级判定累加。
- **完整报告**：`report_json` 存逐用例明细与完整指标，供前端报表与详情下钻。
- **关系**：独立表，无外键；与 rag_metrics_daily（线上运行指标）互补，本表为离线评测口径。
- **用途**：RAG 质量回归评估、版本对比与优化依据。
