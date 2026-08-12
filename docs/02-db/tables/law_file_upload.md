# law_file_upload（文件上传表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：文件上传/文档解析模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：记录用户上传法律文档的全生命周期：文件元数据、解析文本、AI 审查与修订、风险定级，解析入库后回填知识 ID。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，上传记录 ID |
| user_id | BIGINT | 否 | — | 上传用户 ID |
| knowledge_id | BIGINT | 是 | NULL | 关联知识 ID（解析入库后回填） |
| processing_status | VARCHAR(32) | 是 | 'PENDING' | 处理状态（见枚举） |
| file_name | VARCHAR(500) | 否 | — | 原始文件名 |
| file_type | VARCHAR(32) | 是 | NULL | 文件类型：pdf/docx/txt |
| file_size | BIGINT | 是 | NULL | 文件大小（字节） |
| file_path | VARCHAR(1000) | 是 | NULL | 存储路径 |
| content | LONGTEXT | 是 | NULL | 解析后的文本内容 |
| ai_review_result | TEXT | 是 | NULL | AI 审查结果 |
| ai_revised_content | TEXT | 是 | NULL | AI 修订后内容 |
| risk_level | VARCHAR(20) | 是 | NULL | 风险等级（见枚举） |
| upload_time | DATETIME | 是 | CURRENT_TIMESTAMP | 上传时间（替代 create_time） |
| is_deleted | INT | 是 | 0 | 软删除标记：0-正常 1-已删除 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_user_id | 普通 | user_id | 按用户查上传记录 |
| idx_knowledge_id | 普通 | knowledge_id | 按知识条目反查来源文件 |
| idx_processing_status | 普通 | processing_status | 待处理/失败文件批量扫描 |
| idx_upload_time | 普通 | upload_time DESC | 上传时间倒序列表 |

## 枚举

`processing_status`（VARCHAR(32)）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| PENDING | PENDING | 待处理 | 默认，文件已上传未解析 |
| PROCESSING | PROCESSING | 处理中 | 解析任务运行中 |
| COMPLETED | COMPLETED | 已完成 | 解析+审查完成并入库（回填 knowledge_id） |
| FAILED | FAILED | 失败 | 解析/审查异常 |

`risk_level`（VARCHAR(20)）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| NULL | — | 未定级 | 默认，审查未完成 |
| LOW | LOW | 低风险 | AI 审查判定无风险 |
| MEDIUM | MEDIUM | 中风险 | 存在一般性风险 |
| HIGH | HIGH | 高风险 | 存在较高风险内容 |
| CRITICAL | CRITICAL | 极高风险 | 严重风险，需人工复核 |

## 业务规则

- **状态流转**：`PENDING → PROCESSING → COMPLETED`（成功，回填 knowledge_id）或 `→ FAILED`。
- **解析链路**：文件 → `content`（LONGTEXT 解析文本）→ AI 审查 `ai_review_result` → 必要时 `ai_revised_content` 修订 → 定级 `risk_level` → 入 law_knowledge 并回填 `knowledge_id`。
- **关系**：`user_id` → user.id；`knowledge_id` → law_knowledge.id（解析入库后回填）。
- **时间戳特例**：本表无 create_time/update_time，用 `upload_time`（上传时间）记录创建。
- **软删除**：`is_deleted=1` 逻辑删除上传记录。
- **用途**：文档导入的完整审计台账与 AI 审查门禁。
