# 法律安全守卫

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现（输出幻觉检测 🚧）
> 事实源：`D:\develop\Code\LawMind\docs\06-reference\project-tech-details.md` §2.8、`D:\develop\Code\LawMind\docs\06-reference\rag-gap-analysis.md` §2.5、源码 `D:\develop\Code\LawMind\src\main\java\com\lhs\lawmind\security\`、`LegalArticleChunker.java`、`SecurityAuditAspect.java`、`RagServiceImpl.java`
> 关联：`overview.md`、`rag-retrieval.md`（引用验证）、`memory-system.md`（PII 排除）

---

## 一、模块范围

为法律 AI 输出提供**五层输入守卫 + 引用验证闭环 + 分块安全 + 安全审计**。既防用户输入滥用，也防模型编造/越权引用。

| 子模块 | 关键类 | 涉及表 | 说明 |
|--------|--------|--------|------|
| 敏感话题过滤 | `SensitiveTopicFilter` | — | 6 类敏感词库，业务前拦截 |
| 法律相关性判定 | `rag.LegalQuestionClassifier` | — | 非法律问题拒答 |
| 提示注入防护 | `RagServiceImpl.sanitizeUserInput()` | — | 清理 markdown + 注入模式 |
| PII 脱敏 | `PiiUtil` | — | 身份证/手机/银行卡/邮箱/IPv4 掩码 |
| 合规声明 | `rag.RagPromptBuilder` | — | AI 生成免责声明 |
| 引用验证闭环 | `rag.CitationVerifier` | — | UNVERIFIED 警告 |
| 法律分块 | `LegalArticleChunker` | `knowledge_chunk` | 按"编章节约条款"结构分块 |
| 安全审计 | `SecurityAuditAspect` + `@SecurityAudit` | MySQL `security_audit_log` | 操作审计，异常不丢日志 |

---

## 二、核心业务流程

### 2.1 五层安全守卫（输入侧）

```
用户问题
  │
  ▼
[Layer1] SensitiveTopicFilter.filter()
  │  6 类：国家安全 / 暴力 / 色情赌博毒品 / 自残 / 诈骗方法 / 医疗诊断
  │  命中 → 静态拒答（来源 guard_blocked；自残/医疗给引导语）
  │
  ▼
[Layer2] isLegalRelatedQuestion()
  │  ~200 法律关键词 + ~50 问题模式 → 非法律 → "抱歉，我是一个法律咨询助手…"（non_legal_reject）
  │
  ▼
[Layer3] sanitizeUserInput()
  │  移除 markdown 代码块；过滤 ignore previous instructions / you are now a DAN / system: 等注入模式；截断 2000 字符
  │
  ▼
[Layer4] PiiUtil.maskPii()  ← 记忆提取 & 日志输出前脱敏（前3后4/前4后4 掩码）
  │
  ▼
[Layer5] 合规声明附加  ← law_knowledge / llm_direct 来源追加"以上内容由 AI 生成，仅供参考，不构成法律建议"
```

### 2.2 引用验证闭环（输出侧）

```
LLM 回答
  │
  ▼
verifyCitations()
  ① 正则提取引用：(?:《([^》]{1,30})》)?\s*第([一二三四五六七八九十百千万零〇\d]+)条
  ② 中文数字 → 阿拉伯转换（chineseNumToArabic，"第四十七条"→"第47条"）
  ③ 与检索到的 LawKnowledge 交叉比对（法律名 + 条文号）
  ④ 未匹配 → 追加 "⚠ 注意：以下法律引用未经检索结果验证"（UNVERIFIED_CITATION_WARNING）
```

> 闭环价值：引用必须回溯到知识库真实法条，防 LLM 编造条文号。`rag.citation.verification.enabled: true`。

### 2.3 LegalArticleChunker 法律分块（知识入库侧）

| 步骤 | 规则 |
|------|------|
| 目录剥离 | `stripToc()` 移除目录，避免干扰正文解析 |
| 序言识别 | 首个"第X章"前内容按 1200 字符窗口分块 |
| 结构树构建 | 正则 `第[一二三四五六七八九十百千零\d]+[章节条编款目]` 构建 Part→Chapter→Section→Article 四级树 |
| 上下文前缀 | 每条分块附加 `[法律名称 编名 章名 节名 条名]`，检索可定位出处 |
| 短块合并 | <256 字符短段与相邻段合并 |
| 质量校验 | 过滤 <5 字符碎片；标记不以句号/分号/右括号结尾的潜在截断块 |
| 中文数字转换 | `chineseToInt()`（"一百四十三"→143）用于条款排序 |

### 2.4 安全审计日志

- **注解驱动**：`@SecurityAudit(operationType, description, resourceType, logParams)`。
- **审计字段**：操作人 userId、操作类型、资源类型、请求 ID、HTTP 方法/URI/客户端 IP（检查 10 个标准代理头）、资源 ID（方法参数自动提取）、结果 SUCCESS/FAIL + errorMessage。
- **持久化保证**：`finally` 块写 `security_audit_log`，被审计方法抛异常也不丢失。
- 配套：JWT 双 Token。

---

## 三、业务规则

- 敏感话题按类别返回不同拒答文案；**自残**→引导专业心理帮助，**医疗**→引导医生。
- 门控规则命中即拒答，不进入 LLM；降级原则见 `agent-orchestration.md`（fail-open 面向法律相关性判定）。
- 合规声明跳过 `non_legal_reject`（非法律回答）。
- PII 掩码规则：身份证/手机前 3 后 4；银行卡前 4 后 4；邮箱 user 段掩码；IPv4 后两段 `*.*`；身份证支持校验码验证（`isValidIdCard`）。
- 审计日志不记录 MultipartFile 二进制内容（`ControllerLogAspect` 格式化文件名+大小）。

---

## 四、关键设计决策

| 决策 | 方案 | 理由 |
|------|------|------|
| 纯规则敏感过滤 | 6 类关键词 + 静态拒答 | 零 LLM 成本、响应快，满足第一道防线 |
| 规则 + LLM 兜底 | 关键词优先，LLM 兜底相关性判定 | 低成本覆盖多数，LLM 处理边界 case |
| 引用必须可溯源 | 正则提取 + 知识库交叉比对 | 防幻觉条文号，输出可验证 |
| 审计 finally 落库 | 异常也不丢日志 | 审计完整性优先于异常时的整洁 |
| PII 前置脱敏 | 记忆提取/日志输出前统一 mask | 防止 PII 进入持久化与日志链路 |

---

## 五、实现进度

| 项 | 状态 |
|----|:---:|
| 敏感话题过滤（6 类） | ✅ |
| 法律相关性判定（关键词 + 模式） | ✅ |
| 提示注入防护（sanitize） | ✅ |
| PII 检测与脱敏（PiiUtil） | ✅ |
| 合规声明附加 | ✅ |
| 引用验证闭环（UNVERIFIED 警告） | ✅ |
| LegalArticleChunker 法律结构分块 | ✅ |
| 安全审计日志（@SecurityAudit → security_audit_log） | ✅ |
| JWT 双 Token 认证 | ✅ |
| 输出幻觉检测（LLM/NLI 验证回答与上下文一致） | 🚧（路线图 §2.5 输出护栏，规划中） |
| 知识有效期/废止过滤（effective_date/expiry_date） | 🚧 |
