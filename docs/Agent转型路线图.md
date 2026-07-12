# LawMind Agent 转型路线图

> 基于当前 LawMind 项目架构（LangChain4j 0.36.0 + DashScope qwen-plus + Redis 向量存储），规划从固定 RAG 管线向 LangChain4j Agent 架构演进的完整路径。

---

## 文档导航

本路线图与 [Agent转型-实施计划.md](./Agent转型-实施计划.md) 配套使用：

| 路线图章节 | 对应实施计划阶段 | 关系说明 |
|-----------|----------------|---------|
| 一、现状与目标 | 全部阶段 | 整体背景和设计目标 |
| 二、改造阶段详解 | 阶段一～四 | 阶段一～四的详细设计 |
| 三、技术选型 | 全部阶段 | 技术决策依据 |
| 四、改造难度与风险评估 | 全部阶段 | 风险识别和缓解措施 |
| 五、Token 消耗监控与成本追踪 | 阶段五 | 监控体系设计 |
| 六、上下文压缩策略与实现 | 阶段六 | 压缩策略设计 |
| 七、意图识别门控模块 | 前置模块（0.1-0.7） | 门控模块的架构设计 |
| 八、合同审查 Skill | Skill 模块一（S.1-S.7） | 合同审查 Skill 的架构设计 |
| 九、记忆系统 | Skill 模块二（M.1-M.9） | 记忆系统的架构设计 |
| 十、测试策略 | 阶段七 | 测试方法论 |
| 十一、推荐实施路径 | 全部阶段 | 总体实施顺序编排 |
| 十二、与现有功能的集成细节 | 阶段三、前置模块 | 前端和后端集成方案 |
| 十三、关键 Q&A | — | 常见疑问解答 |
| 十四、总结 | — | 项目总结与展望 |

> **阅读建议**：新加入的开发者先读路线图（理解设计意图），再查实施计划（跟踪任务进度）。两个文档通过上表相互索引，章节可一一对应。

---

## 零、实施前环境确认

### 当前项目技术栈（已验证）

| 组件 | 版本/配置 | 说明 |
|------|----------|------|
| Java | 17 | `pom.xml` `<java.version>` |
| Spring Boot | 3.5.12 | `spring-boot-starter-parent` |
| LangChain4j | **0.36.0** | 所有 langchain4j-* 依赖统一版本 |
| Chat Model | qwen-plus (DashScope) | 通过 `langchain4j-dashscope-spring-boot-starter` 自动配置 |
| Embedding | text-embedding-v2 (1536维) | DashScope embedding 模型 |
| Rerank | qwen3-rerank | 重排序模型 |
| Redis | Jedis 客户端 | 向量索引 + 热点缓存 |
| MySQL | MyBatis-Plus 3.0.5 | 业务数据持久化 |
| 前端 | Vue 3 + Element Plus + Pinia | Vite 构建 |

### 现有 Bean 注入方式（关键）

当前项目中，`ChatLanguageModel` 和 `StreamingChatLanguageModel` 由 `langchain4j-dashscope-spring-boot-starter` **自动创建**，通过 `application.yml` 配置：

```yaml
langchain4j:
  dashscope:
    chat-model:
      api-key: ${DASHSCOPE_API_KEY:}
      model-name: qwen-plus
    streaming-chat-model:
      api-key: ${DASHSCOPE_API_KEY:}
      model-name: qwen-plus
    embedding-model:
      api-key: ${DASHSCOPE_API_KEY:}
      model-name: text-embedding-v2
```

`RagServiceImpl` 中的注入方式（使用 `Optional<>` 包装，因为自动配置可能不生效）：

```java
public RagServiceImpl(
        Optional<ChatLanguageModel> chatLanguageModel,
        Optional<StreamingChatLanguageModel> streamingChatLanguageModel,
        // ...
) {
    this.chatLanguageModel = chatLanguageModel.orElse(null);
    this.streamingChatLanguageModel = streamingChatLanguageModel.orElse(null);
}
```

**这意味着 Agent 改造时可以直接注入这两个 Bean，不需要手动创建。**

### 版本说明

LangChain4j 0.36.0 的 Agent 相关 API：
- `@Tool` / `@P` 注解：✅ 支持，位于 `dev.langchain4j.agent.tool`
- `AiServices` 构建器：✅ 支持，位于 `dev.langchain4j.service`
- `TokenStream` 流式输出：✅ 支持
- `ChatMemory` / `MessageWindowChatMemory`：✅ 支持

**不需要升级 LangChain4j 版本**，0.36.0 完全可以实现阶段一到阶段三的 Agent 改造。如果后续需要 `maxToolExecutions` 等高级配置，建议升级到 ≥ 0.40.0。

---

## 一、现状与目标

### 当前架构：固定管线

```
用户问题
  ↓
文本预处理 + 意图分类（硬编码规则，IntentClassifier.classify()）
  ↓
查询扩展（LegalQueryExpander：规则 + LLM 改写）
  ↓
混合检索 = 向量检索（RedisVectorUtil）+ 全文检索 → RRF 融合
  ↓
Rerank（qwen3-rerank）+ MMR 去重（SearchResultDiversifier）
  ↓
LLM 生成答案（qwen-plus，流式 SSE）
  ↓
后处理（引用校验 + PII脱敏 + 敏感话题过滤 + 免责声明）
  ↓
返回答案 → 异步记录日志 + 更新相似问题库 + 热点缓存升级
```

**当前管线入口**：`RagService.processQuestion()` / `processQuestionStream()`
**当前管线实现**：`RagServiceImpl`（~800 行，注入 22 个依赖）

**特点**：每一步都是代码硬编码的顺序，LLM 没有自主权，流程固定不变。无论什么问题，检索→重排序→生成的步骤不可跳过或调整。

### 目标架构：Agent 自主决策

```
用户问题
  ↓
LLM 思考（规划需要哪些信息）
  ↓
决定调用哪个 Tool（或是否调用）
  ↓
Tool 执行，返回结果
  ↓
LLM 根据结果决定下一步（继续调用 / 换策略 / 给出答案）
  ↓
（循环，直到信息充足或达到最大步数）
  ↓
生成最终答案 → 后处理 → 返回
```

**特点**：LLM 自主规划、自主调用工具、自主验证结果，流程动态可变。简单问题一步完成，复杂问题多轮检索验证。

### 对比：固定管线 vs Agent

| 维度 | 固定管线（当前） | Agent（目标） |
|------|-----------------|--------------|
| 流程控制 | 代码硬编码 | LLM 自主决策 |
| 工具调用 | 固定顺序 | 动态选择 + 可跳过 |
| 错误恢复 | 无（失败即返回） | 可重试、换策略 |
| 复杂问题 | 单次检索+生成 | 多步推理+验证 |
| 可观测性 | 日志散落各处 | Tool 调用链天然可追溯 |
| 扩展方式 | 修改管线代码 | 新增 `@Tool` 方法 |
| Token 消耗 | 固定（1次 LLM 调用） | 可变（2~5+ 次 LLM 调用） |

---

## 二、改造阶段详解

### 阶段一：将现有服务封装为 LangChain4j `@Tool`

**目标**：让现有业务逻辑可以被 Agent 调用，不影响现有功能。

**工作量**：约 300 行代码，1~2 天。

#### 1.1 需要封装的 Tool 清单

| Tool 名称 | 对应现有服务 | 功能描述 | 当前方法签名 |
|-----------|-------------|---------|-------------|
| `searchLawKnowledge` | `HybridSearchService` | 混合检索法律知识库 | `searchHybrid(float[], String, int)` |
| `getArticleText` | `LawKnowledgeService` | 查询具体法条原文 | `LawKnowledgeService` 中的 CRUD 方法 |
| `classifyLegalIntent` | `IntentClassifier` | 法律意图分类 | `classify(String) → Intent` |
| `expandLegalQuery` | `LegalQueryExpander` | 法律查询扩展改写 | `LegalQueryExpander` 中的扩展方法 |
| `verifyCitation` | 现有引用校验逻辑 | 校验法条引用是否准确 | 需从 `RagServiceImpl` 提取 |
| `searchSimilarQuestions` | `SimilarQuestionService` | 检索历史相似问题 | `SimilarQuestionService` 中的检索方法 |

#### 1.2 完整代码示例

**新建目录**：`src/main/java/com/lhs/lawmind/agent/tool/`

**文件 1：`LawSearchTools.java`**

```java
package com.lhs.lawmind.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import com.lhs.lawmind.service.HybridSearchService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.utils.EmbeddingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class LawSearchTools {

    private final HybridSearchService hybridSearchService;
    private final LawKnowledgeService lawKnowledgeService;
    private final EmbeddingUtil embeddingUtil;

    /**
     * 混合检索法律知识库。
     * 
     * Tool 返回 String（非对象），LangChain4j 会自动将返回值传给 LLM，
     * LLM 从文本中提取关键信息用于后续推理。
     */
    @Tool("搜索法律知识库，返回与用户问题相关的法律条文、司法解释和案例摘要。" +
          "适用于需要查找法律依据的场景。返回最多 10 条结果，每条包含标题、法条内容和来源。")
    public String searchLawKnowledge(
            @P("用户的搜索查询，使用自然语言描述，例如'劳动合同法关于经济补偿的规定'") 
            String query,
            @P("法律类型过滤，如：刑法、民法典、劳动法、劳动合同法、公司法等。" +
               "如果用户问题没有明确指代特定法律类型，传空字符串''") 
            String lawType) {
        try {
            // 1. 向量化查询
            float[] queryVector = embeddingUtil.embed(query);
            
            // 2. 混合检索（向量 + 全文 + RRF 融合）
            List<LawKnowledge> results;
            if (lawType != null && !lawType.isEmpty()) {
                results = hybridSearchService.searchHybridFiltered(
                        queryVector, query, 10, lawType);
            } else {
                results = hybridSearchService.searchHybrid(queryVector, query, 10);
            }
            
            // 3. 格式化为 LLM 可读文本
            if (results.isEmpty()) {
                return "[检索结果] 未找到相关法律知识。建议：尝试更换关键词或扩大搜索范围。";
            }
            
            return results.stream()
                    .map(k -> String.format(
                            "[%s] %s\n内容：%s\n来源：%s",
                            k.getLawType() != null ? k.getLawType() : "法律",
                            k.getTitle(),
                            k.getContent(),
                            k.getSource() != null ? k.getSource() : "法律知识库"
                    ))
                    .collect(Collectors.joining("\n\n---\n\n"));
                    
        } catch (Exception e) {
            log.error("[Agent Tool] searchLawKnowledge 执行失败: query={}, lawType={}", 
                    query, lawType, e);
            return "[Tool 错误] searchLawKnowledge 执行失败：" + e.getMessage() 
                    + "。请尝试换一种方式检索，或直接告知用户当前无法检索。";
        }
    }

    /**
     * 查询具体法条原文。
     * 
     * 用法示例：getArticleText("劳动合同法", "第三十九条")
     */
    @Tool("根据法律名称和条款号查询具体法条的原文完整内容。" +
          "适用于需要精确定位某一条法律条文的场景。")
    public String getArticleText(
            @P("完整的法律名称，包含书名号，如《劳动合同法》、《民法典》") 
            String lawName,
            @P("条款号，如'第三十九条'、'第一千零六十二条'。可以是条、款、项。") 
            String articleNumber) {
        try {
            // 通过 LawKnowledgeService 查询匹配的知识条目
            // 注意：需要根据实际 Mapper 方法调整查询逻辑
            List<LawKnowledge> knowledges = lawKnowledgeService
                    .selectByTitleKeyword(lawName.replaceAll("[《》]", ""));
            
            if (knowledges.isEmpty()) {
                return "[查询结果] 未找到《" + lawName + "》的相关内容。";
            }
            
            // 筛选匹配条款号的内容
            String result = knowledges.stream()
                    .filter(k -> k.getTitle() != null && k.getTitle().contains(articleNumber))
                    .map(k -> String.format("【%s】\n%s", k.getTitle(), k.getContent()))
                    .collect(Collectors.joining("\n\n"));
            
            if (result.isEmpty()) {
                return "[查询结果] 在《" + lawName + "》中未找到" + articleNumber 
                        + "的内容，请确认条款号是否正确。";
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("[Agent Tool] getArticleText 执行失败: lawName={}, articleNumber={}", 
                    lawName, articleNumber, e);
            return "[Tool 错误] getArticleText 执行失败：" + e.getMessage();
        }
    }
}
```

**文件 2：`LawIntentTools.java`**

```java
package com.lhs.lawmind.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import com.lhs.lawmind.utils.IntentClassifier;
import com.lhs.lawmind.utils.LegalQueryExpander;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LawIntentTools {

    private final IntentClassifier intentClassifier;
    private final LegalQueryExpander legalQueryExpander;

    /**
     * 分类用户的法律意图。
     * 
     * 返回当前的意图分类结果，供 Agent 了解用户想问什么类型的问题，
     * 从而决定后续应该调用哪个检索工具。
     */
    @Tool("分析用户问题的法律意图类型。" +
          "返回意图类别包括：劳动纠纷、婚姻家庭、合同纠纷、刑事咨询、行政法律、公司法务等。" +
          "用于帮助后续检索更有针对性。")
    public String classifyLegalIntent(
            @P("用户的原始问题文本") String question) {
        try {
            IntentClassifier.Intent intent = intentClassifier.classify(question);
            int topK = intentClassifier.adjustTopK(intent, 15);
            boolean deepRetrieval = intentClassifier.useDeepRetrieval(intent);
            
            return String.format("""
                    [意图分析]
                    意图类别：%s
                    建议检索数量：%d
                    是否需要深度检索：%s
                    建议：检索时优先使用 %s 类型的法律知识。
                    """,
                    intent.name(),
                    topK,
                    deepRetrieval ? "是" : "否",
                    intent.name()
            );
        } catch (Exception e) {
            log.error("[Agent Tool] classifyLegalIntent 执行失败: question={}", question, e);
            return "[Tool 错误] 意图分类失败：" + e.getMessage();
        }
    }

    /**
     * 扩展法律查询，补充同义词、相关法律术语。
     */
    @Tool("对用户的原始查询进行法律术语扩展，补充同义词、相关法条表述，" +
          "生成更适合法律检索的查询语句。")
    public String expandLegalQuery(
            @P("用户的原始查询文本") String originalQuery) {
        try {
            String expanded = legalQueryExpander.expand(originalQuery);
            return "[查询扩展结果]\n原始查询：" + originalQuery 
                    + "\n扩展后查询：" + expanded;
        } catch (Exception e) {
            log.error("[Agent Tool] expandLegalQuery 执行失败: query={}", originalQuery, e);
            return "[Tool 错误] 查询扩展失败：" + e.getMessage();
        }
    }
}
```

**文件 3：`LawVerificationTools.java`**

```java
package com.lhs.lawmind.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import com.lhs.lawmind.service.SimilarQuestionService;
import com.lhs.lawmind.entity.SimilarQuestion;
import com.lhs.lawmind.utils.EmbeddingUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LawVerificationTools {

    private final SimilarQuestionService similarQuestionService;
    private final EmbeddingUtil embeddingUtil;

    /**
     * 检索历史上已解答的相似问题，复用已有的高质量回答。
     */
    @Tool("检索历史已解答的相似问题，返回匹配的问答对。" +
          "如果匹配度高（≥0.85），可以直接复用历史回答，节省检索时间。")
    public String searchSimilarQuestions(
            @P("用户当前问题文本") String question) {
        try {
            float[] questionVector = embeddingUtil.embed(question);
            SimilarQuestion similar = similarQuestionService.searchSimilar(
                    question, questionVector);
            
            if (similar == null) {
                return "[相似问题] 未找到高匹配度的历史问题（相似度 < 0.85），" +
                        "需要通过知识库检索获取信息。";
            }
            
            return String.format("""
                    [相似问题匹配]
                    相似度：%.1f%%
                    历史问题：%s
                    历史回答：%s
                    注意事项：以上为历史回答，请核实其中引用的法条是否仍然有效。
                    """,
                    similar.getSimilarity() * 100,
                    similar.getQuestion(),
                    similar.getAnswer()
            );
        } catch (Exception e) {
            log.error("[Agent Tool] searchSimilarQuestions 执行失败: question={}", question, e);
            return "[Tool 错误] 相似问题检索失败：" + e.getMessage();
        }
    }

    /**
     * 核实 LLM 生成答案中的法条引用是否准确。
     * 
     * 注意：此 Tool 不直接查询原文，而是要求 LLM 在给出答案后自我检查。
     * 实际校验逻辑在下游（后处理层），Tool 提供校验提示。
     */
    @Tool("对答案中引用的法条进行核实提示。" +
          "返回每个引用的验证状态，提醒 LLM 在输出前核对准确性。")
    public String verifyCitation(
            @P("需要核实的法条引用文本，如'根据《劳动合同法》第三十九条'") String citation,
            @P("生成答案中对应的原始上下文") String sourceText) {
        // 校验逻辑：
        // 1. 检查引用格式是否正确（法律名称 + 条款号）
        // 2. 检查法条号是否在合理范围
        // 3. 在 sourceText 中搜索是否包含该引用
        boolean found = sourceText != null && citation != null 
                && sourceText.contains(citation.replaceAll("[《》根据]", ""));
        
        return String.format("""
                [引用校验]
                引用：%s
                在检索结果中%s找到对应原文。
                请%s输出该引用%s。
                """,
                citation,
                found ? "" : "未",
                found ? "确保" : "谨慎",
                found ? "并标注来源" : "，如不确定请标注'待核实'"
        );
    }
}
```

#### 1.3 Tool 设计规范（重要）

**返回格式规范**：

所有 Tool 的返回字符串遵循以下约定，确保 LLM 能正确解析：

```
[Tool 名称/类别] <结果摘要>
<详细信息>
```

**错误处理规范**：

Tool 执行失败时，返回 **描述性错误文本**（不抛异常），让 LLM 理解失败原因并调整策略：

```
[Tool 错误] <Tool名称> 执行失败：<原因>
请尝试：<替代建议>
```

**设计原则**：
- 一个 Tool 做一件事（单一职责）
- 返回 `String` 而非复杂对象（LLM 更容易理解）
- 返回文本包含足够上下文（让 LLM 不需要"猜测"）
- Tool 内不调用 LLM（避免嵌套调用导致 Token 爆炸）
- 所有 Tool 都要 catch 异常并返回错误描述

---

### 阶段二：构建 `AiServices` 接口

**目标**：用 LangChain4j 的 Agent 框架把 Tool 串起来，创建可流式对话的法律 Agent。

**工作量**：约 150 行代码，半天。

#### 2.1 定义 Agent 接口

**新建文件**：`src/main/java/com/lhs/lawmind/agent/LawAgent.java`

```java
package com.lhs.lawmind.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.MemoryId;

public interface LawAgent {

    @SystemMessage("""
        你是一位专业的中国法律智能助手，名为 LawMind。
        
        ## 你的能力
        你可以使用以下工具来获取法律信息：
        1. searchLawKnowledge —— 搜索法律知识库获取相关法条和解释
        2. getArticleText —— 查询具体法条原文内容
        3. classifyLegalIntent —— 分析用户问题的法律意图类型
        4. expandLegalQuery —— 对查询进行法律术语扩展
        5. searchSimilarQuestions —— 查找历史上已解答的相似问题
        6. verifyCitation —— 核实法条引用的准确性
        
        ## 工作原则
        - 回答法律问题前，必须先搜索知识库，不要凭记忆回答
        - 主动调用工具获取信息，不要猜测法律条文的具体内容
        - 如果第一次检索结果不理想，尝试换关键词重新搜索
        - 所有法条引用格式：《法律名称》第XX条
        - 如果信息不足或不确定，明确告知用户，不要编造
        - 回答末尾附上免责声明："以上内容仅供参考，不构成法律意见。具体案件请咨询专业律师。"
        
        ## 思考流程
        对于简单问题（如查询单条法条）：
          直接调用 getArticleText 或 searchLawKnowledge → 给出答案
        
        对于复杂问题（如劳动纠纷咨询）：
          1. classifyLegalIntent 分析意图
          2. expandLegalQuery 扩展查询
          3. searchLawKnowledge 检索相关法条
          4. searchSimilarQuestions 查看是否有类似解答
          5. verifyCitation 核实引用
          6. 综合所有信息给出答案
        
        ## 注意事项
        - 不要在工具已经返回"未找到"时反复调用同一个工具
        - 最多调用 5 次工具，超过后直接基于已有信息给出最佳答案
        - 涉及金额计算时，截图工具返回的公式和参数
        - 涉及刑事问题的，额外提醒用户寻求律师帮助
        """)
    TokenStream answer(
            @MemoryId String memoryId,
            @UserMessage String question
    );
}
```

#### 2.2 装配 Agent（基于项目实际 Bean 注入方式）

**新建文件**：`src/main/java/com/lhs/lawmind/config/AgentConfig.java`

```java
package com.lhs.lawmind.config;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import com.lhs.lawmind.agent.LawAgent;
import com.lhs.lawmind.agent.tool.LawSearchTools;
import com.lhs.lawmind.agent.tool.LawIntentTools;
import com.lhs.lawmind.agent.tool.LawVerificationTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置类。
 * 
 * 关键点：
 * - ChatLanguageModel / StreamingChatLanguageModel 由 DashScope starter 自动创建
 * - 直接注入使用，无需手动 new
 * - 每个 memoryId（用户+会话）独立的 ChatMemory 窗口
 */
@Slf4j
@Configuration
public class AgentConfig {

    /**
     * 创建 LawAgent 实例。
     * 
     * 注意：直接注入 ChatLanguageModel 和 StreamingChatLanguageModel，
     * 它们由 langchain4j-dashscope-spring-boot-starter 自动配置，
     * 与 RagServiceImpl 中的注入方式一致。
     */
    @Bean
    public LawAgent lawAgent(
            ChatLanguageModel chatLanguageModel,
            StreamingChatLanguageModel streamingChatLanguageModel,
            LawSearchTools lawSearchTools,
            LawIntentTools lawIntentTools,
            LawVerificationTools lawVerificationTools) {
        
        log.info("[Agent] 初始化 LawAgent，chatModel={}, streamingModel={}",
                chatLanguageModel != null ? "available" : "unavailable",
                streamingChatLanguageModel != null ? "available" : "unavailable");
        
        return AiServices.builder(LawAgent.class)
                .chatLanguageModel(chatLanguageModel)
                .streamingChatLanguageModel(streamingChatLanguageModel)
                .tools(lawSearchTools, lawIntentTools, lawVerificationTools)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory
                        .withMaxMessages(10))
                .build();
    }
}
```

**说明**：这份代码与文档第一版的区别在于，它直接注入 Spring 容器中已有的 `ChatLanguageModel` / `StreamingChatLanguageModel` Bean（由 DashScope starter 自动创建），而不是通过不存在的 `LangChain4jConfig.ChatModelConfig` 内部类。这与项目中 `RagServiceImpl` 的注入方式一致。

#### 2.3 新增 Agent Controller

**新建文件**：`src/main/java/com/lhs/lawmind/controller/AgentController.java`

```java
package com.lhs.lawmind.controller;

import com.lhs.lawmind.agent.LawAgent;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class AgentController {

    private final LawAgent lawAgent;

    /**
     * Agent 流式问答接口（SSE）。
     * 
     * 与 /api/ai-chat/ask-stream 接口并存，
     * 前端可通过开关选择"传统模式"或"Agent 模式"。
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody AgentAskRequest request) {
        // 安全加固：从 RequestContext 获取用户ID（不由客户端传入）
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"message\":\"用户未登录，请先登录\"}"));
                errorEmitter.complete();
            } catch (IOException e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        String question = request.getQuestion();
        Long conversationId = request.getConversationId();
        
        if (question == null || question.trim().isEmpty()) {
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"message\":\"问题不能为空\"}"));
                errorEmitter.complete();
            } catch (IOException e) {
                errorEmitter.completeWithError(e);
            }
            return errorEmitter;
        }

        // memoryId = userId + "_" + conversationId，每个会话独立记忆
        String memoryId = userId + "_" + (conversationId != null ? conversationId : "new");

        SseEmitter emitter = new SseEmitter(120_000L);
        
        final String finalQuestion = question.trim();
        
        // 敏感话题过滤（复用现有过滤器）
        // 注意：此处省略具体调用，实际集成时添加
        
        lawAgent.answer(memoryId, finalQuestion)
                .onNext(token -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onComplete(response -> {
                    try {
                        emitter.send(SseEmitter.event()
                                .name("done")
                                .data("{\"status\":\"completed\"}"));
                        emitter.complete();
                        log.info("[Agent] 对话完成: userId={}, memoryId={}", userId, memoryId);
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onError(error -> {
                    log.error("[Agent] 对话出错: userId={}, memoryId={}, error={}", 
                            userId, memoryId, error.getMessage());
                    try {
                        emitter.send(SseEmitter.event()
                                .name("error")
                                .data("{\"message\":\"回答生成失败，请稍后重试\"}"));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .start();

        return emitter;
    }
}
```

**新建文件**：`src/main/java/com/lhs/lawmind/dto/AgentAskRequest.java`

```java
package com.lhs.lawmind.dto;

import lombok.Data;

@Data
public class AgentAskRequest {
    /**
     * 用户问题
     */
    private String question;

    /**
     * 会话ID（可为null，首次发送时前端传null）
     */
    private Long conversationId;
}
```

#### 2.4 注册到安全配置（JWT 拦截器）

Agent 接口需要复用现有的 JWT 认证和限流机制。在 `WebConfig.java` 或安全拦截器配置中添加 `/api/agent/**` 路径：

```java
// 在现有的拦截器注册中添加
registry.addInterceptor(jwtInterceptor)
        .addPathPatterns("/api/agent/**", "/api/ai-chat/**")  // Agent 复用相同认证
        .excludePathPatterns("/api/user/login", "/api/user/register");
```

---

### 阶段三：引入规划能力（Plan-and-Execute / ReAct）

**目标**：让 Agent 处理复杂问题时能先规划再执行，而非一步一调。

**工作量**：约 200 行代码（主要是 SystemMessage 优化 + 后处理），1 天。

#### 3.1 为什么需要规划

```
简单问题（不需要规划，一步到位）：
  Q: "《劳动合同法》第三十九条是什么？"
  流程: getArticleText → 返回原文 → 完成

复杂问题（需要多步推理和规划）：
  Q: "我在公司工作了3年，月薪8000，公司无故辞退我，我该怎么办？"
  理想流程:
    Step1: classifyLegalIntent → 判断为"劳动纠纷"
    Step2: expandLegalQuery → "用人单位违法解除劳动合同 经济补偿金 计算标准 劳动仲裁"
    Step3: searchLawKnowledge → 检索《劳动合同法》第47、48、87条
    Step4: 根据月薪8000和工作3年来计算经济赔偿：8000×3×2 = 48000
    Step5: searchSimilarQuestions → 找到类似案例参考
    Step6: verifyCitation → 核实关键法条
    Step7: 综合给出维权建议（协商→劳动仲裁→诉讼） + 赔偿金额
```

#### 3.2 实现方案：增强版 SystemMessage（ReAct 模式）

不引入额外的 Plan-and-Execute 框架，直接在 SystemMessage 中嵌入 ReAct 思考模式：

```java
@SystemMessage("""
    你是一位专业的中国法律智能助手，名为 LawMind。
    
    ## 思考模式（ReAct）
    对于每个用户问题，请按照以下框架思考：
    
    1. **理解**：用户的核心诉求是什么？
    2. **规划**：回答这个问题需要哪些信息？列出需要调用的工具。
    3. **执行**：按规划调用工具，每次调用后评估结果。
    4. **调整**：如果结果不理想，调整查询策略重新检索。
    5. **回答**：综合所有信息，给出完整的法律建议。
    
    ## 可用工具
    - searchLawKnowledge(query, lawType): 搜索法律条文和解释
    - getArticleText(lawName, articleNumber): 查询法条原文
    - classifyLegalIntent(question): 分析问题类型
    - expandLegalQuery(originalQuery): 扩展法律查询
    - searchSimilarQuestions(question): 查找历史相似问题
    - verifyCitation(citation, sourceText): 核实引用准确性
    
    ## 工具使用规则
    - 不要在同一轮对话中连续调用相同工具3次以上
    - 不要用完全相同的参数调用同一工具
    - 如果相似问题匹配度 ≥ 85%，优先复用并核实
    - 不确定法条内容时必须调用 getArticleText 获取原文
    
    ## 输出规则
    - 最终回答使用以下结构：
      1. 问题分析（简要）
      2. 相关法律依据（引用法条）
      3. 具体建议/解答
      4. 注意事项
      5. 免责声明
    - 引用格式：《法律名称》第X条第Y款
    - 如果涉及金额计算，逐步列出计算过程
    
    ## 安全规则
    - 不回答如何违法犯罪的问题
    - 涉及刑事犯罪，必须提醒用户寻求律师帮助
    - 不确定的内容明确标注"仅供参考，建议核实"
    """)
```

#### 3.3 工具调用次数限制（防止死循环）

在 `AgentConfig.java` 中，通过 `AiServices` builder 无法在 0.36.0 版本中直接设置 `maxToolExecutions`。替代方案：

**方案 A（推荐）**：在 SystemMessage 中明确规定 + 后端监控超时

```
SystemMessage 中加入：
"最多调用 5 次工具。超过 5 次后，即使信息不完整，也必须基于已获取的信息给出最佳回答。"
```

**方案 B**：升级到 LangChain4j ≥ 0.40.0 后使用：

```java
return AiServices.builder(LawAgent.class)
        .chatLanguageModel(chatLanguageModel)
        .streamingChatLanguageModel(streamingChatLanguageModel)
        .tools(lawSearchTools, lawIntentTools, lawVerificationTools)
        .maxToolExecutions(5)  // 0.40.0+ 支持
        .chatMemoryProvider(memoryId -> MessageWindowChatMemory
                .withMaxMessages(10))
        .build();
```

#### 3.4 思考过程可视化（可选）

在前端 UI 中展示 Agent 的思考过程（调用了哪个 Tool，返回了什么），增强用户信任。

**后端**：通过 SSE 推送 `thinking` 事件

```java
lawAgent.answer(memoryId, finalQuestion)
        .onPartialResponse(partial -> {
            // 检测思考标记，发送 thinking 事件
            if (partial.contains("[调用工具]")) {
                emitter.send(SseEmitter.event()
                        .name("thinking")
                        .data(partial));
            }
        })
        // ...
```

**前端**：在消息气泡中展示思考折叠面板。详细实现见阶段六前端改造。

---

### 阶段四：多 Agent 协作（进阶，可选）

**目标**：不同专长的 Agent 协作完成复杂任务。

**工作量**：约 600 行代码，高风险高收益。适合作为毕业设计/论文亮点。

#### 4.1 多 Agent 架构设计

```
                    ┌─────────────────────┐
                    │   协调 Agent (Router) │
                    │  分析问题 → 分配任务   │
                    │  汇总结果 → 生成答案   │
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
   ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
   │  检索 Agent   │     │  校验 Agent   │     │  计算 Agent   │
   │              │     │              │     │              │
   │ 专长：        │     │ 专长：        │     │ 专长：        │
   │ 法律知识检索   │     │ 引用核实      │     │ 赔偿金额计算   │
   │ 案例查找      │     │ 答案一致性    │     │ 时效计算      │
   │ 法条定位      │     │ 法条有效性    │     │ 诉讼费估算    │
   └─────────────┘     └─────────────┘     └─────────────┘
```

#### 4.2 实现方式（基于 LangChain4j 0.36.0）

每个子 Agent 是一个独立的 `AiServices` 接口，协调 Agent 通过"把子 Agent 声明为 Tool"的方式调用它们：

**检索 Agent 接口**：

```java
public interface SearchAgent {
    @SystemMessage("你是法律检索专家，专注于从知识库中查找最相关的法律条文和案例。")
    String search(@UserMessage String query);
}
```

**校验 Agent 接口**：

```java
public interface VerifyAgent {
    @SystemMessage("你是法律条文校验专家，负责核实法条引用的准确性、有效性和一致性。")
    String verify(@UserMessage String content);
}
```

**协调 Agent（将子 Agent 注册为 Tool）**：

```java
@Bean
public LawAgent lawAgent(
        ChatLanguageModel chatLanguageModel,
        StreamingChatLanguageModel streamingChatLanguageModel,
        SearchAgent searchAgent,
        VerifyAgent verifyAgent) {
    
    return AiServices.builder(LawAgent.class)
            .chatLanguageModel(chatLanguageModel)
            .streamingChatLanguageModel(streamingChatLanguageModel)
            .tools(searchAgent, verifyAgent)  // 子 Agent 作为 Tool
            .chatMemoryProvider(memoryId -> MessageWindowChatMemory
                    .withMaxMessages(10))
            .build();
}
```

#### 4.3 适合多 Agent 的场景

| 场景 | 是否推荐多 Agent | 原因 |
|------|----------------|------|
| 复杂法律咨询（劳动纠纷、婚姻财产分割） | ✅ 推荐 | 需要检索 + 计算 + 案例参考，多条逻辑线独立 |
| 简单法条查询（"XX法第几条是什么"） | ❌ 不推荐 | 单 Agent 足够，多 Agent 增加延迟和 Token 消耗 |
| 法律文书生成 | ✅ 可选 | 检索 Agent 找模板 + 生成 Agent 填充 |
| 案例相似度分析 | ✅ 可选 | 检索 Agent + 分析 Agent 各有专长 |
| 法律知识学习/问答 | ❌ 不推荐 | 单 Agent 检索+回答即可 |

#### 4.4 多 Agent 风险

| 风险 | 说明 | 应对 |
|------|------|------|
| 协调失败 | Router Agent 选错子 Agent | SystemMessage 中定义清晰的路由规则 |
| Token 爆炸 | 多个 Agent 的上下文叠加 | 每个子 Agent 限制独立 context 窗口 |
| 延迟叠加 | 串行调用导致总延迟 = 各 Agent 延迟之和 | 对无依赖的子 Agent 考虑并行调用 |
| 调试困难 | 出错时难以定位是哪个 Agent 的问题 | 每个 Agent 独立日志 + 调用链追踪 |

---

## 三、技术选型

### 3.1 现有依赖（直接利用）

| 依赖 | 用途 | Agent 改造后 |
|------|------|-------------|
| `langchain4j-core` 0.36.0 | @Tool、AiServices、ChatMemory | 直接使用 |
| `langchain4j-dashscope` 0.36.0 | qwen-plus 模型 | 直接使用 |
| `langchain4j-dashscope-spring-boot-starter` | 自动配置 ChatModel / EmbeddingModel Bean | 直接使用 |
| `langchain4j-redis` 0.36.0 | Redis 向量存储 | 直接使用 |
| `spring-boot-starter-web` | SSE 流式接口 | 直接使用 |
| `spring-boot-starter-security` | JWT 认证 | 复用 |
| `spring-boot-starter-aop` | 日志/限流/审计切面 | 复用 |

### 3.2 不需要新增的依赖

- **Python 生态**（LangChain / LlamaIndex）：**不需要**
- **Spring AI**：**不需要**（LangChain4j 已满足需求）
- **Elasticsearch / Qdrant**：**不需要**（Redis 向量存储已够用）

### 3.3 版本升级建议（可选）

| 组件 | 当前 | 建议 | 原因 |
|------|------|------|------|
| LangChain4j | 0.36.0 | **保持** | Agent 改造不需要升级 |
| Spring Boot | 3.5.12 | **保持** | 无需变动 |
| Java | 17 | **保持** | 无需变动 |
| DashScope Model | qwen-plus | 复杂场景可换 qwen-max | 增强推理能力 |

**升级 LangChain4j 的唯一理由**：如果需要 `.maxToolExecutions(N)` 方法以编程方式限制 Tool 调用次数，需要升级到 ≥ 0.40.0。

---

## 四、改造难度与风险评估

### 4.1 各阶段评估

| 改造项 | 代码量 | 新增文件 | 风险 | 收益 | 推荐优先级 |
|--------|--------|---------|------|------|-----------|
| 现有服务加 `@Tool` 注解 | ~300 行 | 3 个 Tool 类 | 低（不影响现有接口） | 中 | ⭐⭐⭐⭐⭐ |
| 构建 `AiServices` 接口 | ~150 行 | 3 个文件 | 低（新增接口） | 高 | ⭐⭐⭐⭐⭐ |
| 替换主流程为 Agent（灰度） | ~100 行 | 前端 1 个开关 | 中（需保留旧流程） | 高 | ⭐⭐⭐⭐ |
| 引入 ReAct 规划模式 | ~200 行（SystemMessage） | 0（修改现有配置） | 中 | 高 | ⭐⭐⭐ |
| 多 Agent 协作 | ~600 行 | 5+ 个文件 | 高（调试复杂、Token 消耗） | 研究价值 | ⭐⭐ |
| Token 监控 + 成本追踪 | ~150 行 | 1 个拦截器 | 低 | 高 | ⭐⭐⭐⭐ |

### 4.2 关键风险与应对

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|---------|
| Agent 调用 Tool 失败（API 超时/网络问题） | 用户得到不完整答案 | 中 | Tool 内部 catch 异常，返回描述性错误文本；LLM 可据此调整策略 |
| Agent 陷入死循环（反复调用同一 Tool） | 大量 Token 消耗 + 超时 | 中 | SystemMessage 限制 + SseEmitter 超时 + 升级后 maxToolExecutions(5) |
| LLM 产生幻觉（忽略 Tool 返回结果） | 答案不准确 | 中 | 后处理层保留引用校验 + 评估监控 |
| 流式返回时暴露思考过程 | 用户体验差 | 低 | 思考过程通过单独的 SSE thinking 事件发送（可选展示） |
| Token 消耗显著增加 | 成本上升 3~10 倍 | 高 | 添加 Token 计数器 + 控制台监控（见第五章） |
| LangChain4j 版本升级兼容问题 | 编译失败 | 低 | 先在单独分支验证，确认无误再合并 |
| Agent 模式下 PII/敏感词绕过 | 安全风险 | 低 | Controller 层复用现有的 SecurityAuditAspect + PiiUtil + SensitiveTopicFilter |

---

## 五、Token 消耗监控与成本追踪

### 5.1 为什么需要

Agent 模式比固定管线多 3~10 倍的 Token 消耗（多轮 LLM 调用 + Tool 调用往返），需要埋点监控以便：
1. 对比两种模式的成本
2. 发现异常的 Token 消耗（如死循环）
3. 优化 SystemMessage 和 Tool 返回格式

### 5.2 实现方案

**新建文件**：`src/main/java/com/lhs/lawmind/agent/monitor/AgentMetricsInterceptor.java`

```java
package com.lhs.lawmind.agent.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent Token 消耗与 Tool 调用次数追踪器。
 * 
 * 当前版本使用日志记录，后续可接入 Actuator Metrics 或 Prometheus。
 */
@Slf4j
@Component
public class AgentMetricsCollector {

    private final AtomicLong totalAgentCalls = new AtomicLong(0);
    private final AtomicLong totalToolCalls = new AtomicLong(0);
    private final AtomicLong totalFallbackCalls = new AtomicLong(0);  // Agent 降级到固定管线

    public void recordAgentCall(String memoryId, String question) {
        totalAgentCalls.incrementAndGet();
        log.info("[Agent Metrics] 新请求: memoryId={}, questionLen={}, totalCalls={}", 
                memoryId, question.length(), totalAgentCalls.get());
    }

    public void recordToolCall(String toolName, long durationMs) {
        totalToolCalls.incrementAndGet();
        log.info("[Agent Metrics] Tool调用: tool={}, duration={}ms, totalToolCalls={}", 
                toolName, durationMs, totalToolCalls.get());
    }

    public void recordFallback(String reason) {
        totalFallbackCalls.incrementAndGet();
        log.warn("[Agent Metrics] Agent降级: reason={}, totalFallbacks={}", 
                reason, totalFallbackCalls.get());
    }

    public AgentMetricsSnapshot getSnapshot() {
        return new AgentMetricsSnapshot(
                totalAgentCalls.get(),
                totalToolCalls.get(),
                totalFallbackCalls.get()
        );
    }

    public record AgentMetricsSnapshot(
            long totalAgentCalls, 
            long totalToolCalls, 
            long totalFallbackCalls) {}
}
```

### 5.3 成本估算参考

| 模式 | 单次回答 Token 消耗 | 相对成本 |
|------|-------------------|---------|
| 固定管线 | ~2000 tokens（1次 LLM 调用） | 基准 1× |
| Agent 简单问题 | ~3000 tokens（1次 LLM + 1~2个 Tool） | 1.5× |
| Agent 复杂问题 | ~8000 tokens（1次 LLM + 3~5个 Tool） | 4× |
| Agent 极端复杂 | ~15000 tokens（多次 LLM + 多 Tool） | 7.5× |

**建议**：将 Agent 的复杂问题 Token 上限控制在 15000 tokens 以内，通过 SseEmitter 超时（120秒）+ SystemMessage 限制双重保障。

---

## 六、上下文压缩策略与实现

### 6.1 为什么需要上下文压缩

Agent 模式的核心特征是多轮 LLM 调用——每次迭代都会在消息列表中追加 `AiMessage`（含工具调用请求）和 `ToolExecutionResultMessage`（工具返回结果）。随着推理深入，上下文会迅速膨胀：

```
消息累积公式（单次 Agent 请求）：
  SystemMessage（~900 tokens）
+ UserMessage（~100 tokens）
+ (AiMessage + ToolExecutionResultMessage_1 + ToolExecutionResultMessage_2 + ...) × N 轮迭代
────────────────────────────────────────────────
  5 轮迭代、每轮 2 个工具 = 约 6,000~10,000 tokens
```

**法律场景的特殊性**：工具返回的法律条文内容往往很长（民法典一条可能有 300+ 字），`searchLawKnowledge` 返回 10 条结果时单次可达 800~1500 tokens。如果不做压缩：

| 场景 | 保守估算 | 极端情况 |
|------|---------|---------|
| 简单法条查询（1 轮 1 工具） | ~2,000 tokens | ~3,000 tokens |
| 劳动纠纷咨询（3 轮 5 工具） | ~5,000 tokens | ~10,000 tokens |
| 复杂案件分析（5 轮 8+ 工具） | ~8,000 tokens | ~16,000 tokens |

**三大痛点**：

1. **成本线性增长**：qwen-plus 按 token 计费，上下文越长费用越高。5 轮 Agent 对话的 token 消耗是固定管线的 3~5 倍
2. **质量衰减（Lost-in-the-Middle）**：LLM 对上下文中间位置的信息关注度显著低于开头和结尾。早期的检索结果被后续工具结果"淹没"，Agent 可能遗忘关键法条
3. **窗口溢出风险**：qwen-plus 上下文窗口为 32K tokens，虽在日常使用中不易触及，但未来引入多轮对话记忆（ChatMemory）或多 Agent 协同时，多个请求的上下文叠加可能接近上限

**上下文压缩要解决的核心矛盾**：工具返回的结果越多，LLM 可利用的信息越多，但有效利用这些信息的能力反而下降。压缩的目标是在信息量和可处理性之间找到平衡点。

### 6.2 设计目标

| 目标 | 指标 | 优先级 |
|------|------|--------|
| 降低 Token 消耗 | 工具结果部分压缩 ≥ 50% | P0 |
| 保持信息完整性 | 法条编号、金额、关键事实不丢失 | P0 |
| 不增加额外 LLM 调用 | 优先使用规则提取，LLM 压缩仅在净节省时触发 | P1 |
| 不影响回答质量 | 压缩前后 RAGAS faithfulness 无显著差异 | P1 |
| 可配置 | 压缩阈值、策略均可通过配置调整 | P2 |

### 6.3 压缩策略：四层递进 + 结构化知识状态

> **设计参考**：本策略综合了 Claude Code 的上下文压缩架构（渐进式摘要、结构化知识维护、引用溯源）和 LawMind 法律场景的实际特点（法条检索结果冗长、信息层级分明、法条编号为关键锚点）。

```
┌─────────────────────────────────────────────────────────────────┐
│              LawMind Agent 上下文压缩策略（增强版）                 │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  Layer 0: 工具输出格式优化（零成本，始终开启，内化在 Tool 层）          │
│  ├─ 结构化模板返回：每条结果以"法条编号 | 核心规则 | 适用条件"格式       │
│  ├─ 合并重复：同法律的多个条款合并输出，减少法律名称重复                 │
│  └─ 估算节省：10~20%                                             │
│                                                                │
│  Layer 1: 规则提取 → 结构化知识片段（零 LLM 成本，超阈值触发）         │
│  ├─ 法律 NER 提取：《法律名称》+ 第X条 + 金额 + 时效关键词            │
│  ├─ 结构化输出：{法条}{金额公式}{时效}{参考案例} 四字段模板            │
│  ├─ 触发条件：单条 Tool 结果 > 500 tokens                         │
│  └─ 估算节省：30~50%                                             │
│                                                                │
│  Layer 2: LLM 语义摘要（有成本，净节省时触发）                       │
│  ├─ 轻量 prompt（~80 tokens）：只删不改，保留所有法律原文表述          │
│  ├─ 触发条件：(原始-压缩) > 压缩成本 × 2.0                          │
│  └─ 估算节省：60~80%                                             │
│                                                                │
│  ★ Layer 3: 结构化知识状态维护（NEW — 核心创新点）                  │
│  ├─ 增量式更新：每次 Tool 调用后，提取关键事实合并到知识状态中          │
│  ├─ 消歧去重：同一法条被多次检索时，仅保留最完整版本，标记引用次数       │
│  ├─ 层次记忆：近期结果（最近 2 轮）保持详细，远期摘要归档              │
│  ├─ 引用溯源：每条结论附带引用来源（哪个 Tool + 哪次调用）            │
│  └─ 估算节省：累计 50~70%（叠加 Layer 0~2 效果）                  │
│                                                               │
└───────────────────────────────────────────────────────────────┘
```

#### Layer 0: 工具输出格式优化（始终开启）

当前工具返回格式示例：
```
[劳动法] 中华人民共和国劳动合同法第四十七条
内容：经济补偿按劳动者在本单位工作的年限，每满一年支付一个月工资的标准
向劳动者支付。六个月以上不满一年的，按一年计算；不满六个月的，向劳动者
支付半个月工资的经济补偿。劳动者月工资高于用人单位所在直辖市、设区的
市级人民政府公布的本地区上年度职工月平均工资三倍的，向其支付经济补偿
的标准按职工月平均工资三倍的数额支付，向其支付经济补偿的年限最高不超
过十二年。本条所称月工资是指劳动者在劳动合同解除或者终止前十二个月的
平均工资。
来源：法律知识库
```

优化后（结构化 + 信息密度提升）：
```
《劳动合同法》第47条 | 经济补偿=工作年限×月工资 | 上限:12年+3倍社平工资 | 月工资=离职前12月均值
```

这不是在压缩阶段做的，而是在 **Tool 实现层**让工具返回更紧凑的格式。需要在三个阶段 Tool 中修改返回格式模板。

#### Layer 1: 规则提取器（RuleExtractor）

当单条工具返回结果超过阈值时（默认 500 tokens），用规则提取关键信息：

```
输入：searchLawKnowledge 返回的 800+ tokens 文本
处理：
  1. 正则匹配所有《法律名称》 → 去重保留
  2. 正则匹配所有"第X条" / "第X款" → 关联对应法律
  3. 提取金额（\d+元）和计算公式线索
  4. 提取时效相关日期和关键词（年/月/日 + 时效/仲裁/诉讼）
  5. 按优先级排序：法条 > 金额 > 时效 > 其他
输出：结构化摘要，约 200~400 tokens
```

**规则提取器不调用 LLM**，零额外成本。但存在局限性——无法理解语义，可能丢失隐含信息（如"参照前款规定"的跨条引用）。

#### Layer 2: LLM 语义摘要（SummarizingCompressor）

当规则提取无法覆盖（如包含复杂论述的案例文本），或者 Layer 1 处理后仍超阈值时，调用 LLM 做语义压缩：

```
系统提示（轻量，~80 tokens）：
"你是法律文本精简专家。将以下检索结果压缩为关键事实摘要。
 必须保留：法条编号、适用条件、法律后果、金额公式中的变量
 可以删除：重复论述、'根据XX法规定'等引导语、案例背景故事
 输出要求：每条信息一行，总字数 ≤ 原文40%，不改写法条原文措辞"

调用条件（保守触发）：
  compressed_tokens + compression_cost < original_tokens
  即：节省的 tokens > 压缩 prompt + 压缩结果的 tokens
```

**关于压缩成本的核算**：LLM 压缩本身也消耗 token（压缩 prompt ~80 + 输入 ~800 + 输出 ~250 = ~1130 tokens），所以需要评估净收益。只有满足 `原始大小 - 压缩后大小 > 压缩成本` 时才触发 Layer 2。对于短工具结果（< 300 tokens），Layer 2 永远不会触发；对于长结果（> 800 tokens），净节省通常在 40~60%。

#### ★ Layer 3: 结构化知识状态（KnowledgeState）— 核心创新

这是整个压缩策略中最关键的概念，借鉴自 Claude Code 的渐进式上下文维护机制。Claude Code 不是等上下文满了再做一次大压缩，而是在处理过程中持续维护一个结构化的"理解状态"。LawMind 同样可以维护一个随推理进程逐步更新的知识摘要：

**概念模型**：

```
┌─────────────────────────────────────────────────────┐
│              结构化知识状态（KnowledgeState）            │
│  随每次 Tool 调用增量更新，维护可追溯的知识原子          │
├─────────────────────────────────────────────────────┤
│                                                       │
│  ┌─ 法条索引（ArticleIndex）                          │
│  │  《劳动合同法》第47条 | 经济补偿=N×月工资 | 上限12年 │
│  │    来源: searchLawKnowledge(轮次2)                  │
│  │    引用次数: 2                                      │
│  │  《劳动合同法》第87条 | 违法解除=补偿金×2            │
│  │    来源: searchLawKnowledge(轮次2) + getArticleText(轮次3) │
│  │    引用次数: 1                                      │
│  │  《劳动争议调解仲裁法》第27条 | 仲裁时效1年           │
│  │    来源: searchLawKnowledge(轮次3)                  │
│  │    引用次数: 1                                      │
│  ├─────────────────────────────────────────────────┤
│  │ 状态：已核实 3 条 | 待核实 1 条 | 冲突 0 条          │
│  └─────────────────────────────────────────────────┘
│                                                       │
│  ┌─ 金额计算（AmountCalc）                             │
│  │  月工资: 8000元（用户提供）                          │
│  │  工作年限: 3年（用户提供）                           │
│  │  经济补偿 = 8000 × 3 = 24000元                      │
│  │  赔偿金（违法解除）= 24000 × 2 = 48000元             │
│  │  状态：已计算 | 来源: 用户输入 + 第47/87条推导        │
│  └─────────────────────────────────────────────────┘
│                                                       │
│  ┌─ 时效与程序提醒                                     │
│  │  劳动争议仲裁时效: 1年（自知道权利受侵害之日起）       │
│  │  程序: 协商 → 劳动仲裁（前置） → 法院诉讼             │
│  └─────────────────────────────────────────────────┘
│                                                       │
│  ┌─ 参考案例                                           │
│  │  案例A: 类似违法解除案，法院支持双倍赔偿 (来源: 轮次3) │
│  │  案例B: 加班费计算争议，需考勤记录证明 (来源: 轮次4)  │
│  └─────────────────────────────────────────────────┘
│                                                       │
└─────────────────────────────────────────────────────┘
```

**KnowledgeState 的维护逻辑**：

```
每次 Tool 调用完成后：
  1. 从 Tool 结果中提取"知识原子"（法条、金额、时效、案例）
  2. 与现有 KnowledgeState 合并：
     a. 新法条 → 追加到 ArticleIndex，标记来源和轮次
     b. 已存在法条 → 合并来源（多源证实），增加引用计数
     c. 冲突法条 → 保留两者，标记为"需核实"
  3. 重新计算 Token 预算：KnowledgeState 当前大小占比
  4. 如超限 → 对最旧的知识原子执行 Layer 2 摘要压缩
```

**KnowledgeState 的核心优势**：

| 对比维度 | 无 KnowledgeState | 有 KnowledgeState |
|---------|------------------|-------------------|
| 重复法条处理 | 每次检索都追加全文，3 次检索同一法条 = 3 份全文 | 首次保留完整，后续仅增加引用计数，去重 |
| 信息追溯 | 无法快速定位"某法条来自哪次检索" | 每条结论带来源标记，Agent 可以核实 |
| 冲突发现 | 依赖 LLM 在长上下文中自行发现 | 结构化的 dedup 逻辑更可靠 |
| 最终答案生成 | 5 轮工具结果散落在 10+ 条消息中 | 给 LLM 一份清理过的知识索引作为参考 |
| 跨轮次记忆 | 早期检索结果被后续消息挤出注意力 | KnowledgeState 始终在消息列表顶部附近 |

#### 递归加权（Recency-Weighted）压缩策略

借鉴 Claude Code 的做法——最近的对话保持完整细节，越远的部分越激进压缩：

```
迭代轮次:  第1轮         第2轮         第3轮         第4轮         第5轮
           │             │             │             │             │
工具结果:  R1            R2            R3            R4            R5
           │             │             │             │             │
压缩策略:  (最旧)                                 (最新)
           ▼                                          ▼
      Layer 2 激进     Layer 1 规则    保留原文      保留原文      保留原文
      或移入 KState     提取          (近2轮不压缩)  (近2轮不压缩)
```

具体规则：
- **最近 2 轮**的工具结果 → **保留原文**（保证 Agent 能充分利用最新信息）
- **倒数第 3~4 轮**的结果 → **Layer 1 规则提取**（结构化的保留）
- **倒数第 5 轮及更早**的结果 → **移入 KnowledgeState 摘要**或 **Layer 2 压缩**（只保留关键结论）
- 同一法条被后续轮次再次检索时 → **新结果替换旧结果**，旧结果仅保留引用计数

这个策略的核心洞察是：**Agent 在推理时对最新获取的信息依赖最大，对早期信息只需保留"结论"即可。** 早期检索的法条如果重要，会被后续检索再次提到（法律检索天然有反复交叉引用的特点），自然形成"新结果替代旧结果"的去重效果。

#### 按工具类型的差异化压缩配置

不同的工具产生不同特征的输出，一刀切的压缩策略效果不佳。借鉴 Claude Code 对不同类型内容（代码/文本/数据）使用不同摘要策略的思路：

| 工具 | 典型输出大小 | 信息密度 | 压缩策略 | 激进程度 |
|------|-------------|---------|---------|---------|
| `classifyLegalIntent` | ~100-200 tokens | 高 | **不压缩**，直接透传 | 0% |
| `expandLegalQuery` | ~200-300 tokens | 高 | **不压缩**，直接透传 | 0% |
| `getArticleText` | ~500-1500 tokens | 极高 | Layer 0 格式优化 + 保留原文关键词 | 保守（法条原文一字千金） |
| `searchLawKnowledge` | ~800-2000 tokens | 中（10条结果，前3条最相关） | Layer 1 规则提取（保留全部法条编号 + 前3条摘要） | 激进（产出多、冗余高） |
| `searchSimilarQuestions` | ~400-1000 tokens | 中 | Layer 1 提取：相似度 + 问题摘要 + 答案关键点 | 中等 |
| `verifyCitation` | ~200-400 tokens | 高 | **不压缩**，核实结果必须完整保留 | 0% |

**配置化实现**：

```yaml
# application.yml
lawmind:
  agent:
    compression:
      enabled: true
      # 按工具配置压缩策略
      tool-strategies:
        searchLawKnowledge:
          layer: 1                    # 默认使用 Layer 1 规则提取
          max-results: 5              # 最多保留 5 条结果
          full-detail-top: 3          # 前 3 条保留完整摘要
        getArticleText:
          layer: 0                    # 默认仅格式优化
          preserve-original-terms: true # 保留法条原文关键措辞
        searchSimilarQuestions:
          layer: 1
          max-results: 3
        classifyLegalIntent:
          compress: false             # 不压缩
        expandLegalQuery:
          compress: false
        verifyCitation:
          compress: false
      # 递归加权阈值
      recency:
        keep-full-recent: 2           # 最近 2 轮保留原文
        layer1-start-round: 3         # 第 3 轮起使用 Layer 1
        layer2-start-round: 5         # 第 5 轮起使用 Layer 2 / KnowledgeState
      # KnowledgeState 配置
      knowledge-state:
        enabled: true
        max-articles: 20              # 最多保存 20 条法条索引
        merge-duplicates: true         # 合并重复法条
```

### 6.4 架构设计（增强版）

```
agent/
├── AgentRunner.java                # Agent 执行器（集成压缩检查点）
├── compress/
│   ├── ContextCompressor.java      # 压缩调度器（策略选择 + 阈值判断）
│   ├── KnowledgeState.java         # ★ 结构化知识状态（增量维护）
│   ├── KnowledgeAtom.java          # ★ 知识原子 record（法条/金额/时效/案例）
│   ├── RuleExtractor.java          # Layer 1：规则提取（法律 NER + 结构化）
│   ├── SummarizingCompressor.java  # Layer 2：LLM 语义摘要
│   ├── TokenEstimator.java         # Token 估算器
│   └── CompressionConfig.java      # 压缩配置属性类
└── monitor/
    └── AgentMetricsCollector.java   # 已有，增加压缩统计
```

**增强后的核心类设计**：

```java
/**
 * ★ 结构化知识状态 —— 压缩策略的核心。
 * 在 Agent 推理过程中，逐步积累和维护已获取的法律知识。
 * 
 * 类比：Claude Code 的 conversation summary ——
 * 一个独立于原始消息列表的结构化理解，随对话逐步更新。
 */
public class KnowledgeState {

    // 已提取的法条索引（去重合并）
    private final List<ArticleEntry> articles = new ArrayList<>();
    // 金额计算公式
    private final List<CalcEntry> calculations = new ArrayList<>();
    // 时效和程序提醒
    private final List<String> reminders = new ArrayList<>();
    // 参考案例摘要
    private final List<CaseEntry> cases = new ArrayList<>();

    /**
     * 从 Tool 结果中提取知识原子并合并到状态中。
     * @return 新发现的知识数量（用于判断是否需要更新上下文）
     */
    public int ingest(String toolName, String toolResult, int roundIndex) {
        int newAtoms = 0;

        // 1. 提取法条编号（正则匹配）
        List<ArticleEntry> extracted = ArticleEntry.extractFrom(toolResult, toolName, roundIndex);
        for (ArticleEntry entry : extracted) {
            // 去重合并：同一法条 → 合并来源，增加引用计数
            ArticleEntry existing = findExisting(entry.lawName(), entry.articleNumber());
            if (existing != null) {
                existing.mergeSource(entry);  // 多源证实
            } else {
                articles.add(entry);
                newAtoms++;
            }
        }

        // 2. 提取金额和计算线索
        // 3. 提取时效关键词
        // (实现细节略)

        return newAtoms;
    }

    /**
     * 将当前知识状态格式化为 LLM 可读的摘要文本。
     * 在最终答案生成前替代散落的工具结果。
     */
    public String toCompactSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("[已检索到的关键法律信息]\n\n");
        
        // 法条索引（按引用次数排序，最常引用的排前面）
        sb.append("■ 相关法条：\n");
        articles.stream()
            .sorted((a,b) -> b.citeCount() - a.citeCount())
            .forEach(a -> sb.append(a.toCompactString()).append("\n"));
        
        // 金额计算
        if (!calculations.isEmpty()) {
            sb.append("\n■ 金额计算：\n");
            calculations.forEach(c -> sb.append(c.toCompactString()).append("\n"));
        }
        // ... reminders, cases ...
        
        return sb.toString();
    }

    // 内部 record：知识原子
    record ArticleEntry(
        String lawName,        // 法律名称
        String articleNumber,  // 条款号
        String keyRule,        // 核心规则（一句话）
        int citeCount,         // 被引用次数
        List<String> sources,  // 来源追溯：["searchLawKnowledge(R2)", "getArticleText(R3)"]
        boolean verified       // 是否已被 verifyCitation 核实
    ) {
        String toCompactString() {
            return String.format("《%s》第%s条 | %s | 引用%d次 | %s",
                lawName, articleNumber, keyRule, citeCount,
                verified ? "已核实" : "待核实");
        }
    }
}
```

**ContextCompressor 增强（集成 KnowledgeState）**：

```java
public class ContextCompressor {

    private final KnowledgeState knowledgeState;  // ★ 新增
    
    /**
     * 压缩单条工具结果。
     * 增强：同时更新 KnowledgeState（无论是否触发压缩）。
     */
    public String compressToolResult(String toolName, String rawResult,
                                      List<ChatMessage> messages, int roundIndex) {
        // ★ 始终更新 KnowledgeState（增量式知识积累）
        knowledgeState.ingest(toolName, rawResult, roundIndex);
        
        // 按工具差异化策略选择 Layer
        CompressStrategy strategy = config.getStrategy(toolName);
        
        int estimatedTokens = tokenEstimator.estimate(rawResult);
        
        switch (strategy.layer()) {
            case 0: return applyLayer0(rawResult);
            case 1: return estimateTokens > config.getSingleResultThreshold()
                ? applyLayer1(toolName, rawResult) : rawResult;
            case 2: return shouldUseLayer2(estimatedTokens, rawResult)
                ? applyLayer2(toolName, rawResult) : applyLayer1(toolName, rawResult);
        }
        
        // ★ 递归加权：根据轮次数决定压缩激进程度
        if (roundIndex <= config.getRecencyKeepFull()) {
            return rawResult;  // 最近 2 轮不压缩
        }
        
        return rawResult;
    }

    /**
     * ★ 生成最终上下文时，用 KnowledgeState 摘要替代历史工具结果。
     * 这是 KnowledgeState 发挥作用的关键时刻。
     */
    public String buildFinalContext(String userQuestion) {
        return knowledgeState.toCompactSummary();
    }
}
```

**AgentRunner 增强集成点**（在原有基础上增加 KnowledgeState 维护）：

```java
// 集成点 1：每次 Tool 执行后，更新 KnowledgeState
for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
    String toolResult = executeTool(toolRequest);
    if (contextCompressor != null) {
        // ★ 传入当前轮次号，实现递归加权
        toolResult = contextCompressor.compressToolResult(
                toolRequest.name(), toolResult, messages, iteration);
    }
    messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult));
}

// 集成点 2：最终答案生成前，用 KnowledgeState 替代散落的工具结果
if (contextCompressor != null) {
    String knowledgeSummary = contextCompressor.buildFinalContext(userQuestion);
    messages.clear();
    messages.add(SystemMessage.from(systemPrompt));
    messages.add(UserMessage.from(userQuestion));
    // ★ 不再是原始工具结果的摘要，而是结构化的知识索引
    messages.add(UserMessage.from("以下是检索到的关键法条和信息的结构化摘要：\n\n" 
            + knowledgeSummary));
    messages.add(UserMessage.from("请基于以上信息直接给出最终回答，不要再调用工具。"));
}
```

### 6.5 分阶段实施计划

| 子任务 | 内容 | 工作量 | 优先级 |
|--------|------|--------|--------|
| 6.5.1 Token 估算器 | 实现 `TokenEstimator`：中文按字符数÷1.5、英文按单词数×1.3 的混合估算算法 | 0.5h | P0 |
| 6.5.2 Level 0 工具格式优化 | 修改 3 个 Tool 类的返回格式模板，去除冗余，压缩 10~20% | 1h | P0 |
| 6.5.3 Level 1 规则提取器 | 实现 `RuleExtractor`：法律命名实体识别 + 结构化重排 + 低相关度截断 | 3h | P0 |
| 6.5.4 Level 2 LLM 摘要器 | 实现 `SummarizingCompressor`：轻量 prompt + 净节省判断 + 调用 ChatLanguageModel | 2h | P1 |
| 6.5.5 ContextCompressor 调度器 | 实现主调度逻辑：阈值判断 + 策略选择 + AgentRunner 集成 + 配置项 | 2h | P0 |
| 6.5.6 压缩效果监控 | 在 `AgentMetricsCollector` 中增加压缩统计字段：压缩次数、节省 tokens 估计值 | 1h | P1 |
| 6.5.7 压缩效果对比测试 | 用阶段四的 10 题测试集，对比压缩前后的回答质量和 token 消耗 | 2h | P1 |
| **合计** | | **约 1.5 天** | |

### 6.6 Token 节省估算模型

以典型"劳动纠纷咨询"（3 轮 5 工具）为例：

| 消息 | 压缩前 | Level 0 | Level 1 | Level 2 |
|------|--------|---------|---------|---------|
| SystemMessage（ReAct） | ~900 | ~900 | ~900 | ~900 |
| UserMessage | ~100 | ~100 | ~100 | ~100 |
| Tool: classifyLegalIntent | ~150 | ~150 | ~150 | ~150 |
| Tool: expandLegalQuery | ~200 | ~180 | ~120 | ~100 |
| Tool: searchLawKnowledge（10条） | ~1200 | ~900 | ~400 | ~250 |
| Tool: getArticleText | ~600 | ~480 | ~200 | ~150 |
| Tool: searchSimilarQuestions | ~400 | ~350 | ~180 | ~120 |
| AiMessage（LLM 思考+调用） | ~500 | ~500 | ~500 | ~500 |
| AiMessage（最终答案） | ~600 | ~600 | ~600 | ~600 |
| **累计** | **~4,650** | **~4,160** | **~3,150** | **~2,870** |
| **节省比例** | — | **10.5%** | **32.3%** | **38.3%** |

### 6.7 风险与权衡

| 风险 | 影响 | 概率 | 应对措施 |
|------|------|------|---------|
| 规则提取遗漏关键法条 | 回答不完整，Agent 被迫重试 | 中 | 规则提取失败时保留原文，规则提取仅作为"优化"而非"替代" |
| LLM 摘要歪曲法律含义 | 回答错误，引用变形 | 低 | 摘要 prompt 明确要求保留"原文法条编号+适用条件"，不做改写只做删减 |
| 压缩成本 > 节省 | 额外消耗 token | 中 | 保守触发策略：仅当 net_savings > cost × 2.0 时触发 Level 2 |
| 过度压缩导致上下文空洞 | Agent 反复调用同一工具（死循环） | 低 | 保留最近 2 轮工具结果为原文，仅压缩更早的消息 |
| 压缩增加响应延迟 | 用户体验下降 | 低 | Level 0/1 无额外 Latency；Level 2 仅对长结果触发，增加 < 500ms |

**核心权衡**：上下文压缩本质上是在 **Token 成本** 和 **信息完整性** 之间做折衷。对于法律场景，宁可多保留 10% 的冗余信息，也不能丢失法条编号和适用条件。因此压缩策略的设计偏向保守：

- Level 0/1 激进（零成本，即使信息有损也可接受，因为原文仍在消息历史中）
- Level 2 保守（有成本的 LLM 调用，必须确保净收益，且不过度改写法律文本）

### 6.8 与其他模块的关系


                    ┌──────────────────┐
                    │  AgentMetricsCollector  │  ← 提供压缩统计（压缩次数、节省量）
                    └────────┬─────────┘
                             │
    ┌────────────────────────┼────────────────────────┐
    │                        ▼                         │
    │  AgentRunner ────── ContextCompressor            │
    │       │                    │                      │
    │       │            ┌───────┴───────┐              │
    │       │            │               │              │
    │       ▼            ▼               ▼              │
    │  executeTool()  RuleExtractor  SummarizingCompressor
    │       │         (Level 1)     (Level 2)           │
    │       │            │               │              │
    │       │            └───────────────┘              │
    │       │                    │                      │
    │       ▼                    ▼                      │
    │  toolResult ──→ 压缩后的 toolResult               │
    │                                                    │
    │  与阶段六（测试）关联：                              │
    │  - 压缩效果需要集成测试覆盖                          │
    │  - 黄金数据集评估对比：压缩前后 faithfulness 指标    │
    │                                                    │
    │  与阶段七（多 Agent）关联：                          │
    │  - 子 Agent 间传递压缩后的摘要，而非完整检索结果     │
    │  - 协调 Agent 汇总子 Agent 结果时使用 Level 2 压缩  │
    └────────────────────────────────────────────────────┘


### 6.9 未来扩展方向

1. **语义缓存去重**：同一工具在同一请求中被多次调用时（不同参数），检测返回结果中重复引用的法条，仅保留首次出现的完整内容，后续以引用标记替代
2. **动态阈值调整**：根据问题复杂度（由 `classifyLegalIntent` 判断）动态调整压缩激进程度——简单问题激进压缩，复杂问题保守压缩
3. **结构化 JSON 输出**：让 Tool 直接返回结构化 JSON 而非自然语言文本，压缩器直接操作 JSON 字段，效率更高且信息定位更精准
4. **向量摘要嵌入**：将压缩后的摘要向量化存储，后续相似问题时直接通过向量相似度匹配，跳过完整的检索+压缩流程

---

## 七、意图识别门控模块

### 为什么意图识别是领域 Agent 的"守门人"

通用 Agent（如 ChatGPT、Claude）可以回答任何领域的问题——它们不设前置门槛，LLM 自行判断能否回答。但 **LawMind 是法律领域专用 Agent**，这一前提改变了架构设计的根本逻辑：

| 对比维度 | 通用 Agent | 领域 Agent（LawMind） |
|---------|-----------|----------------------|
| 问题范围 | 开放域（任何话题） | 封闭域（仅法律） |
| 入口控制 | LLM 自行判断 | **必须前置过滤** |
| 非领域问题 | 自由回答 | **礼貌拒绝 + 引导** |
| 路由策略 | 统一处理 | **按意图类型分流** |
| 资源效率 | Token 消耗无所谓 | **简单问题不应走 Agent 全流程** |
| 稳定性 | 偶尔胡说可接受 | **法律场景对准确性要求极高** |

**核心问题**：当前架构中，`classifyLegalIntent` 是 Agent 的一个 Tool —— Agent 在推理过程中"可能"调用它来了解用户意图。这导致三个严重缺陷：

1. **非法律问题不设防**：用户问"今天天气怎么样"，Agent 仍会启动完整的推理循环，浪费 Token 和时间，最终给出一个不专业的回答
2. **Agent 不知道自己的边界**：LLM 倾向于"尽力回答"，即使问题超出法律领域，它也会尝试用有限的工具去应对，结果不可控
3. **简单问题过度处理**：用户问"合同法第一条是什么"，本应直接检索返回，却被送入 Agent 多轮推理，无谓消耗资源

**正确的架构**：意图识别不应是 Agent 的"可选工具"，而应是 Agent 的"门控守门人"——在 Agent 循环启动之前，先完成领域判断和路由决策。

```
当前架构（错误）：                      目标架构（正确）：
                          
  用户问题                               用户问题
    ↓                                      ↓
  Agent 循环启动                      ┌─ 意图识别门控 ─┐
    ↓                                 │  1. 领域判断    │
  LLM 思考...                         │  2. 意图分类    │
    ↓                                 │  3. 复杂度评估  │
  (可能)调用 classifyLegalIntent      │  4. 路由决策    │
    ↓                                 └──────┬─────────┘
  继续推理...                                │
    ↓                           ┌────────────┼────────────┐
  最终回答                        ▼            ▼            ▼
                             快速通道      Agent通道      拒绝响应
                           (直接检索)    (多步推理)    (非法律问题)
```

### 设计目标

| 目标 | 指标 | 优先级 |
|------|------|--------|
| 准确拦截非法律问题 | 非法律问题拒绝率 ≥ 95% | P0 |
| 法律问题分类准确 | 意图分类准确率 ≥ 90% | P0 |
| 简单问题不走 Agent | 快速通道命中率 ≥ 60%（减少 Token 浪费） | P1 |
| 降低非法律问题的 Token 消耗 | 非法律问题 0 LLM 调用（纯规则判断） | P1 |
| 响应速度提升 | 简单问题延迟 ≤ 传统管线延迟 + 50ms（门控开销） | P2 |

### 架构设计

#### 三层门控流水线

```
                        ┌──────────────────────────────────────────┐
                        │        意图识别门控（IntentGate）          │
                        │          Agent 入口前置处理                │
                        ├──────────────────────────────────────────┤
                        │                                          │
  ┌───────────┐    ┌────▼─────┐    ┌──────────────┐    ┌─────────┐ │
  │ 用户问题   │───→│ Layer 1  │───→│  Layer 2     │───→│ Layer 3 │ │
  └───────────┘    │ 领域门控  │    │ 意图细分      │    │ 路由决策 │ │
                   │ (Domain  │    │ (Intent      │    │ (Router) │ │
                   │  Gate)   │    │  Classifier) │    │          │ │
                   └────┬─────┘    └──────┬───────┘    └────┬────┘ │
                        │                │                  │       │
                        │ 非法律         │                  │       │
                        ▼                ▼                  │       │
                   ┌─────────┐   ┌──────────────┐          │       │
                   │ 拒绝响应  │   │ 意图标签:     │          │       │
                   │ "请咨询   │   │ ARTICLE_     │          │       │
                   │  法律问题" │   │ LOOKUP       │          │       │
                   └─────────┘   │ CONSULTATION  │          │       │
                                 │ CALCULATION   │          │       │
                                 │ CASE_SEARCH   │          │       │
                                 │ DOCUMENT      │          │       │
                                 └──────────────┘          │       │
                                                           │       │
                        ┌──────────────────────────────────┘       │
                        │                                          │
                        ▼           ▼           ▼                  │
                   ┌─────────┐ ┌─────────┐ ┌─────────┐            │
                   │ 快速通道 │ │Agent通道│ │ 混合通道 │            │
                   │ (Fast)  │ │ (Agent) │ │ (Hybrid)│            │
                   └─────────┘ └─────────┘ └─────────┘            │
                                                                    │
                        └──────────────────────────────────────────┘
```

#### Layer 1: 领域门控（DomainGate）

**职责**：判断用户输入是否属于法律领域。

**实现方式**：采用"规则优先 + LLM 兜底"的双层策略：

```
输入 → 规则快速判断（正则 + 关键词，< 1ms）
  ├─ 明显法律问题（含"法"、"起诉"、"赔偿"、"合同"等法律核心词）
  │   → 直接通过，进入 Layer 2
  ├─ 明显非法律问题（含"天气"、"游戏"、"做饭"等非法律主题词）
  │   → 直接拒绝，返回引导语
  └─ 模糊边界（无法用规则判断，约占 10~15%）
      → LLM 轻量判断（~50 tokens prompt，二元分类：法律/非法律）
```

**法律核心词库**（规则匹配，零延迟）：

```
法、律、条、款、法规、条例、司法解释
起诉、应诉、被告、原告、诉讼、仲裁、判决、裁定
赔偿、补偿、罚款、罚金、违约金
合同、协议、证据、鉴定、公证
犯罪、刑事、公安、检察、法院、律师
劳动、工伤、社保、劳动仲裁、辞退、加班
婚姻、离婚、抚养、继承、遗嘱、房产
公司、股权、股东、破产、清算、商标、专利
债权、债务、担保、抵押、借贷、利息
```

**非法律主题词库**（规则排除）：

```
天气、游戏、电影、音乐、美食、旅游、运动、健身
编程、代码、Python、Java（非法律上下文中）
```

**模糊边界 LLM 判断 prompt**（约 50 tokens，仅在规则无法判断时调用）：

```
判断以下用户问题是否属于法律咨询领域（是/否）：
"用户问题"
仅回答"是"或"否"。
```

#### Layer 2: 意图细分（IntentClassifier）

**职责**：将法律问题细分为具体的意图类型，指导后续处理策略。

**意图分类体系**：

| 意图类型 | 说明 | 典型问法 | 预期行为 |
|---------|------|---------|---------|
| `ARTICLE_LOOKUP` | 法条查询 | "合同法第三十九条是什么"、"查一下民法典第1043条" | 直接检索 + 返回原文 |
| `LEGAL_CONSULTATION` | 法律咨询 | "公司无故辞退我怎么办"、"离婚财产怎么分" | Agent 多步推理 |
| `CALCULATION` | 金额计算 | "工伤九级能赔多少钱"、"加班费怎么算" | Agent + 计算专项 |
| `CASE_SEARCH` | 案例检索 | "有没有类似加班费胜诉的案例"、"别人怎么判的" | 相似案例搜索 |
| `DOCUMENT_DRAFTING` | 文书生成 | "帮我写一份劳动仲裁申请书"、"借条怎么写" | 模板 + 参数填充 |
| `LEGAL_KNOWLEDGE` | 法律知识问答 | "什么是诉讼时效"、"行政复议和行政诉讼的区别" | 知识库检索 + 解释 |

**实现方式**：增强现有 `IntentClassifier`：

```
当前（纯关键词匹配，4 种意图）:
  if (question.contains("第几条")) → ARTICLE_LOOKUP
  if (question.contains("赔偿多少钱")) → CALCULATION
  ...

增强后（规则 + 轻量 LLM，6 种意图）:
  Step 1: 关键词规则快速匹配（覆盖 80%+ 常见问法）
  Step 2: 规则未命中 → LLM 轻量分类（~60 tokens prompt）
  Step 3: 返回 IntentResult { intent, confidence, suggestedRoute }
```

**关键词规则增强**（扩充至 6 种意图 + 更多关键词变体）：

```java
// 法条查询 — 精确问法
"第几条规定", "第几条", "法条原文", "查法条", "法条内容",
"哪一条法律", "法律规定是什么", "法律怎么规定", "法律如何规定"

// 法律咨询 — 场景化问法
"怎么办", "怎么维权", "怎么处理", "被辞退", "被开除",
"离不了婚", "争抚养权", "合同纠纷", "欠钱不还", "被撞了"

// 金额计算 — 计算诉求明确
"赔偿多少", "赔多少钱", "赔偿标准", "赔偿金额", "能赔多少",
"工伤赔偿", "加班费怎么算", "经济补偿金", "精神损失费"

// 案例检索 — 明确要找案例
"类似案例", "判例", "判决案例", "别人怎么判", "参考案例",
"有没有判过", "法院怎么判", "胜诉率", "类似案件"

// 文书生成 — 明确要生成文档
"帮我写", "怎么写", "范本", "模板", "格式",
"起诉状", "申请书", "协议书", "合同怎么签", "借条"

// 法律知识问答 — 概念性提问
"什么是", "是什么意思", "区别", "定义", "适用条件",
"构成要件", "法律后果", "有效无效"
```

#### Layer 3: 路由决策（IntentRouter）

**职责**：根据意图类型 + 复杂度评估，决定使用哪种处理通道。

**复杂度评估因子**：

| 因子 | 权重 | 简单 → 复杂 |
|------|------|-----------|
| 涉及法律数量 | 高 | 1 部法律 → 多部法律交叉 |
| 是否需要计算 | 中 | 无需计算 → 需要多步计算 |
| 是否需要案例参考 | 中 | 纯法条 → 需要类案参考 |
| 问题分句数 | 低 | 单个问题 → 多个子问题 |
| 是否涉及程序 | 中 | 纯实体问题 → 包含仲裁/诉讼程序 |

**路由决策矩阵**：

```
                        意图类型
                          
  复杂度    ARTICLE_   LEGAL_      CALCU-    CASE_     DOCUMENT   LEGAL_
              LOOKUP    CONSULT    LATION    SEARCH    DRAFTING   KNOWLEDGE
  ─────────────────────────────────────────────────────────────────────
  简单      → 快速通道  → 快速通道  → 快速通道 → 快速通道 → 快速通道  → 快速通道
           (单条检索) (知识库+LLM)(公式+检索)(案例搜索)(模板填充)(知识检索)
           
  中等      → Agent     → Agent    → Agent    → Agent    → Agent     → 快速通道
           (检索+核实)(多步推理)(计算+法条)(案例+法条)(模板+检索)(知识检索)
           
  复杂      → Agent     → Agent    → Agent    → Agent    → Agent     → Agent
           (多次检索) (完整推理)(计算+核验)(类案分析)(生成+核验)(综合解释)
```

**三种处理通道**：

| 通道 | 适用场景 | Token 消耗 | 延迟 | 说明 |
|------|---------|-----------|------|------|
| **快速通道（Fast）** | 简单法条查询、法律知识问答 | ~2,000 tokens | ~2s | 直接检索 + LLM 生成，不走 Agent 循环 |
| **Agent 通道（Agent）** | 复杂咨询、金额计算、案例搜索 | ~5,000~8,000 tokens | ~8~15s | 多步推理 + 工具调用 |
| **混合通道（Hybrid）** | 文书生成等 | ~3,000 tokens | ~5s | 模板 + 参数化 + 可选检索 |

### 核心类设计

```
agent/
├── gate/
│   ├── IntentGate.java              # 门控主入口（协调三层流水线）
│   ├── DomainGate.java              # Layer 1: 领域判断（规则 + LLM 兜底）
│   ├── IntentClassifierEnhanced.java # Layer 2: 意图细分（规则 + LLM）
│   ├── ComplexityAssessor.java      # 复杂度评估（多因子加权）
│   ├── IntentRouter.java            # Layer 3: 路由决策
│   ├── IntentGateConfig.java        # 门控配置（关键词库、阈值）
│   └── model/
│       ├── IntentType.java          # 意图类型枚举（6 种）
│       ├── RouteDecision.java       # 路由决策 record
│       ├── IntentResult.java        # 意图分析结果 record
│       └── DomainVerdict.java       # 领域判断结果 record
```

**IntentGate 主入口**（伪代码）：

```java
public class IntentGate {

    private final DomainGate domainGate;
    private final IntentClassifierEnhanced intentClassifier;
    private final IntentRouter router;

    /**
     * 门控处理入口。在 AgentRunner 启动前调用。
     * 
     * @param question 用户原始问题
     * @return 路由决策（包含处理通道和预处理结果）
     */
    public GateResult process(String question) {
        // Layer 1: 领域判断
        DomainVerdict verdict = domainGate.judge(question);
        if (!verdict.isLegal()) {
            return GateResult.reject(verdict.reason());
        }

        // Layer 2: 意图分类
        IntentResult intent = intentClassifier.classify(question);

        // Layer 3: 路由决策
        RouteDecision route = router.decide(question, intent);

        return GateResult.accept(intent, route);
    }
}
```

**DomainGate 双层策略**：

```java
public class DomainGate {

    // 法律核心词库（约 80 个关键词，覆盖 85%+ 的法律问题）
    private static final Set<String> LEGAL_KEYWORDS = Set.of(
        "法", "律", "起诉", "诉讼", "仲裁", "赔偿", "合同", "协议",
        "犯罪", "刑事", "公安", "法院", "律师", "劳动", "工伤",
        "婚姻", "离婚", "继承", "遗嘱", "公司", "股权", "破产",
        "债权", "债务", "担保", "借贷", "利息", "商标", "专利",
        "判决", "裁定", "证据", "公证", "鉴定", "处罚", "罚款",
        "侵权", "违约", "解除", "终止", "撤销", "变更"
    );

    // 明确的非法律主题词（减少 LLM 调用）
    private static final Set<String> NON_LEGAL_TOPICS = Set.of(
        "天气", "游戏", "电影", "音乐", "美食", "旅游", "运动", "健身"
    );

    public DomainVerdict judge(String question) {
        // Step 1: 快速规则判断（零延迟）
        if (containsLegalKeywords(question)) {
            return DomainVerdict.LEGAL;
        }
        if (isClearlyNonLegal(question)) {
            return DomainVerdict.nonLegal("非法律问题，请咨询法律相关问题");
        }

        // Step 2: 模糊边界 → LLM 轻量判断（约 50 tokens prompt）
        // 仅对 ~10-15% 的问题触发
        return llmClassify(question);
    }
}
```

### 与 AgentRunner 的集成

门控在 `AgentController` 中，**先于 AgentRunner 执行**：

```java
// AgentController.askStream() 中的集成
public SseEmitter askStream(AgentAskRequest request) {
    String question = request.getQuestion();

    // ★ 前置：意图识别门控
    GateResult gate = intentGate.process(question);

    switch (gate.route().channel()) {
        case REJECT:
            // 非法律问题 → 快速拒绝，零 LLM 调用
            return buildRejectResponse(gate.reason());

        case FAST:
            // 简单法律问题 → 快速通道（直接检索 + LLM 生成）
            return fastChannel.handle(question, gate.intent());

        case AGENT:
            // 复杂法律问题 → Agent 多步推理
            // ★ 门控结果作为前缀注入 SystemMessage，减少 Agent 猜测
            return agentChannel.handle(question, gate.intent(), gate.route());

        case HYBRID:
            // 混合通道 → 模板 + 可选检索
            return hybridChannel.handle(question, gate.intent());
    }
}
```

### 非法律问题的拒绝策略

拒绝不是简单返回"我不知道"，而是分级的引导式响应：

| 问题类型 | 示例 | 拒绝响应 |
|---------|------|---------|
| 完全无关 | "今天天气怎么样" | "您好，我是法律智能助手 LawMind，专注于回答法律相关问题。如需查询天气信息，建议使用天气应用。" |
| 擦边但非法律 | "怎么追女生" | "您好，这超出了我的法律咨询范围。如果您有婚姻家庭方面的法律问题（如婚前协议、离婚财产分割等），我很乐意帮助。" |
| 格式异常 | 乱码、纯数字、"test" | "您好，请描述您想咨询的法律问题，我会尽力为您解答。" |
| 敏感话题 | 攻击性内容 | "您好，请提出合法的法律咨询问题。" |

### 快速通道 vs Agent 通道的收益分析

以 100 次用户请求为例（假设 60% 简单问题、30% 复杂问题、10% 非法律问题）：

| 通道 | 请求数 | 每次 Token | 总 Token | 每次延迟 | 总延迟 |
|------|--------|-----------|---------|---------|--------|
| 无门控（全部走 Agent） | 100 | ~5,000 | 500,000 | ~8s | 800s |
| 有门控（快速 + Agent + 拒绝） | — | — | 237,000 | — | 300s |
| ├ 快速通道 | 50 | ~2,000 | 100,000 | ~2s | 100s |
| ├ Agent 通道 | 30 | ~5,000 | 150,000 | ~8s | 240s |
| ├ 拒绝（规则） | 10 | 0 | 0 | <0.1s | 1s |
| └ 混合通道 | 10 | ~3,000 | 30,000 | ~5s | 50s |
| **节省** | — | — | **52.6%** | — | **62.5%** |

### 实施计划

| 子任务 | 内容 | 工作量 | 优先级 |
|--------|------|--------|--------|
| 1. 意图分类体系设计 | 定义 6 种意图类型 + 关键词规则库扩充 + 法律/非法律词库 | 2h | P0 |
| 2. DomainGate 实现 | 双层策略（规则 + LLM 兜底）+ 分级拒绝响应模板 | 2h | P0 |
| 3. IntentClassifier 增强 | 从 4 种意图扩展到 6 种 + 关键词规则覆盖 80%+ + LLM 兜底 | 2h | P0 |
| 4. ComplexityAssessor + IntentRouter | 多因子复杂度评估 + 路由决策矩阵实现 | 2h | P1 |
| 5. IntentGate 主控 + AgentController 集成 | 三层流水线串联 + 三个处理通道分发 | 2h | P0 |
| 6. 单元测试 | 领域判断、意图分类、路由决策的覆盖测试（60+ 用例） | 2h | P0 |
| 7. 门控效果验证 | 用测试集对比有/无门控的 Token 消耗和响应质量 | 1h | P1 |
| **合计** | | **约 1.5 天** | |

### 风险与应对

| 风险 | 影响 | 概率 | 应对 |
|------|------|------|------|
| 规则过于严格，误拒法律问题 | 用户无法获得服务 | 中 | 规则偏向"通过"而非"拒绝"；模糊边界走 LLM 二次判断 |
| 规则过于宽松，非法律问题穿透 | Token 浪费，回答质量差 | 中 | 非法律拒绝对话模板明确引导；记录穿透率持续优化词库 |
| 意图分类错误导致路由不当 | 简单问题变慢，复杂问题答不好 | 中 | 前端保留手动模式切换作为逃生舱；日志记录分类准确率 |
| LLM 判断增加延迟 | 模糊边界问题变慢 | 低 | LLM 判断仅对 ~10% 问题触发，prompt 极短（50 tokens），延迟 < 500ms |
| 词库维护负担 | 关键词库随法律领域演化需更新 | 低 | 词库外置到 YAML 配置，支持热更新；定期分析穿透日志补充词库 |

---

## 八、合同审查 Skill

### 背景与定位

合同审查是 LawMind 在咨询问答之外的自然延伸。用户上传一份合同，Agent 逐条分析其合法性、公平性和完整性，输出结构化审查报告。

**为什么用 Skill 而非写代码**：

| 维度 | 文书生成（代码方案） | 合同审查（Skill 方案） |
|------|-------------------|---------------------|
| 核心能力 | 结构保证 —— 格式固定、法条嵌入 | 语义理解 —— 风险识别、公平性判断 |
| 确定性 | 高 —— 起诉状格式全国统一 | 低 —— 每份合同条款结构不同 |
| LLM 角色 | 辅助（仅润色措辞） | 主力（分析 + 判断） |
| 代码价值 | 模板引擎保证合规 | 几乎没有可模板化的东西 |
| 维护成本 | 模板变更需改 YAML + 重新部署 | 审查清单更新无需重启服务 |
| 法规变化 | 影响较小（格式规范稳定） | 频繁（民法典司法解释陆续出台），Skill 热更新即可 |

**核心设计原则**：
1. **所有法律依据来自系统知识库**：Agent 不凭 LLM 自身记忆判断条款合法性，每条判断前必须先调用 `LawKnowledgeService.search()` 检索真实法条原文，再以检索到的法条为证据做出判断
2. **Skill 封装审查方法论**：审查清单 + 模式库 + 输出模板 → 告诉 Agent "审什么、怎么审"
3. **知识库提供法律依据**：法条原文 + 司法解释 + 判例参考 → 告诉 Agent "凭什么这么判断"
4. **LLM 负责理解与推理**：阅读理解合同 + 对照法条判断 + 生成审查报告 → "看懂合同 + 依法判断"

```
审查流程（知识库锚定）：
  合同条款
    ↓
  Agent 调用 searchLawKnowledge("违约金 过高 调整")  ← 从知识库检索
    ↓ 获得真实法条原文
  《民法典》第585条："约定的违约金过分高于造成的损失的，
   当事人可以请求人民法院或者仲裁机构予以适当减少..."
    ↓
  Agent 对照法条原文判断条款  ← 基于检索结果推理
    ↓
  审查结论 + 引用检索到的法条原文  ← 可验证、可追溯
```

> **为什么这样设计**：LLM 的法条知识是"训练记忆"——可能过时、可能遗漏、可能编造（幻觉）。LawMind 的知识库（混合检索 + Rerank + Redis 向量存储）已经索引了权威法律法规，这是系统最可靠的资产。合同审查不应绕开它。**检索到的法条原文是证据，LLM 的推理是判断**——两者职责分离。

### 一、Skill 结构

```
skills/contract-review/
├── manifest.yaml                    # Skill 元数据（名称、版本、触发条件）
├── prompt/
│   ├── system-message.md            # Agent 审查时的系统提示词
│   └── user-prompt-template.md      # 用户问题模板（含合同文本占位符）
├── checklists/
│   ├── general-clauses.yaml         # 通用条款审查清单（10 维度）
│   ├── labor-contract.yaml          # 劳动合同专项清单
│   ├── loan-agreement.yaml          # 借款合同专项清单
│   └── rental-contract.yaml         # 租赁合同专项清单
├── patterns/
│   └── unfair-clauses.yaml          # 常见不公平条款模式库
├── templates/
│   └── review-report.md             # 审查报告输出模板
└── references/
    ├── civil-code-contracts.md      # 《民法典》合同编核心条文摘要
    └── typical-cases.md             # 典型合同纠纷判例参考
```

### 二、审查清单与风险维度

#### 2.1 通用审查清单（10 大风险维度）

审查任何合同时，Agent 逐项对照以下维度：

| # | 风险维度 | 审查要点 | 严重程度 | 知识库检索关键词（Agent 必须先搜索再判断） |
|---|---------|---------|---------|------------------------------------------|
| 1 | 主体资格 | 签约方是否适格（自然人/法人/非法人组织），是否有履约能力 | HIGH | `民事主体资格 合同法 签约主体` |
| 2 | 标的明确性 | 合同标的是否清晰具体（商品名称/规格/数量、服务内容/标准） | CRITICAL | `合同标的 明确性 民法典 合同成立要件` |
| 3 | 价款与支付 | 金额是否明确、支付方式/期限/条件是否合理 | HIGH | `合同价款 支付期限 民法典 买卖合同` |
| 4 | 履行期限与方式 | 履行时间、地点、方式是否明确；是否存在单方任意变更权 | HIGH | `合同履行期限 履行方式 民法典 第510条` |
| 5 | 违约责任 | 违约情形是否对等（双方义务平衡）；违约金是否过高（>30% 实际损失）；是否有免责条款过宽 | CRITICAL | `违约金 过高 调整 民法典第585条 违约责任` |
| 6 | 争议解决 | 管辖法院/仲裁机构是否对己方有利；是否有不合理的管辖约定 | MEDIUM | `合同管辖 约定管辖 民事诉讼法第24条 仲裁协议` |
| 7 | 权利义务对等 | 是否存在一方权利过多、义务过少；免责条款是否仅免除一方责任 | CRITICAL | `格式条款 无效 民法典第497条 权利义务对等 免责条款` |
| 8 | 知识产权与保密 | 知识产权归属是否明确；保密义务范围和期限是否合理 | MEDIUM | `知识产权归属 保密协议 反不正当竞争法 商业秘密` |
| 9 | 合同终止与解除 | 解除条件是否公平；是否有不合理的单方解除权 | HIGH | `合同解除 法定解除权 民法典第563条 终止条件` |
| 10 | 格式条款风险 | 是否存在"最终解释权归XX所有"等无效格式条款 | CRITICAL | `格式条款 无效情形 民法典第496条 第497条 最终解释权` |

> **使用方式**：Agent 审查每个维度时，先以"知识库检索关键词"调用 `LawKnowledgeService.search()`，获取法条原文后再对照合同条款做出判断。检索结果中的法条原文直接写入审查报告的"引用法条"列。

#### 2.2 专项审查清单

不同合同类型叠加专项审查维度：

- **劳动合同**：竞业限制范围与补偿、试用期长度、加班费计算基数、社保缴纳约定
- **借款合同**：利率是否超过 LPR 四倍、是否有砍头息、逾期利率计算方式
- **租赁合同**：维修责任归属、转租条件、押金退还条件、涨租条款

### 三、不公平条款模式库

#### 3.1 常见无效格式条款

```yaml
# unfair-clauses.yaml — 常见不公平条款识别模式
patterns:
  - id: UNILATERAL_INTERPRETATION
    keywords: ["最终解释权归", "本公司保留最终解释权"]
    legal_basis: "《民法典》第496条 — 格式条款提供方不得利用格式条款排除对方主要权利"
    knowledge_search: "格式条款 无效 最终解释权 民法典第496条 第497条"
    severity: CRITICAL
    suggestion: "删除该条款；合同解释应遵循《民法典》第142条、第466条的通常解释原则"

  - id: EXCESSIVE_LIQUIDATED_DAMAGES
    keywords: ["违约金.*(?:三倍|五倍|十倍)", "每日.*%.*违约金"]
    legal_basis: "《民法典》第585条 — 违约金过高的，当事人可以请求人民法院减少"
    knowledge_search: "违约金 过高 调整 民法典第585条 实际损失 百分之三十"
    severity: HIGH
    suggestion: "建议将违约金调整为实际损失的30%以内"

  - id: UNILATERAL_TERMINATION
    keywords: ["甲方有权随时(解除|终止)", "乙方不得(单方)?解除"]
    legal_basis: "《民法典》第563条 — 合同解除权应基于法定或约定条件"
    knowledge_search: "合同解除权 法定解除 民法典第563条 单方解除 效力"
    severity: CRITICAL
    suggestion: "明确解除合同的具体条件，双方解除权应对等"

  - id: WAIVER_OF_RIGHTS
    keywords: ["放弃(诉讼|仲裁|索赔|追索)", "不得(起诉|申请仲裁)"]
    legal_basis: "《民法典》第197条 — 诉讼时效的约定无效；诉权不得预先放弃"
    knowledge_search: "诉权 预先放弃 无效 民法典第197条 诉讼时效 法定"
    severity: CRITICAL
    suggestion: "删除该条款；诉权是法定权利，不得通过约定预先放弃"

  - id: AMBIGUOUS_OBLIGATIONS
    keywords: ["尽力(配合|协助|完成)", "尽最大努力", "合理期限内"]
    legal_basis: "《民法典》第510条 — 约定不明确的，可以协议补充；不能达成补充协议的，按照合同相关条款或交易习惯确定"
    knowledge_search: "合同约定不明确 补充协议 民法典第510条 交易习惯 履行标准"
    severity: MEDIUM
    suggestion: "明确具体的行为标准和期限，避免使用模糊表述"

  - id: UNFAIR_GOVERNING_LAW
    keywords: ["由甲方所在地(法院|仲裁委)", "在.*所在地(起诉|仲裁)"]
    legal_basis: "《民事诉讼法》第24条 — 合同纠纷由被告住所地或合同履行地管辖"
    knowledge_search: "合同纠纷 管辖 被告住所地 民事诉讼法第24条 约定管辖 效力"
    severity: MEDIUM
    suggestion: "争取约定己方所在地管辖，或选择中立第三地"

  - id: AUTOMATIC_RENEWAL_TRAP
    keywords: ["到期.*自动(续期|续约|续签)", "未提出异议.*视为(同意|续约)"]
    legal_basis: "《民法典》第496条 — 格式条款应以合理方式提示对方注意"
    knowledge_search: "自动续约 格式条款 提示义务 民法典第496条 默示同意"
    severity: HIGH
    suggestion: "明确续约需双方另行书面确认；如保留自动续约，应设置提前通知机制"
```

### 四、Skill Prompt 设计

#### 4.1 System Message（系统提示词）

```markdown
你是一位资深合同审查律师，拥有15年民商事合同审查经验。
你的审查意见必须基于系统知识库中的真实法条，不得凭记忆引用法律。

## 你的任务
对用户提供的合同文本进行逐条审查，识别法律风险和不公平条款。

## ★ 核心规则：先检索，再判断（CRITICAL）

在对任何条款做出法律判断之前，你必须先用 searchLawKnowledge 工具检索相关法条原文。
检索到的法条原文是你做出判断的**唯一法律依据**，不得凭自身知识编造法条。

审查流程：
1. 识别当前审查的条款涉及哪个法律问题（如违约金、解除权、管辖权等）
2. 调用 searchLawKnowledge("相关检索关键词") 获取法条原文
3. 对照检索到的法条原文判断条款是否合法/公平
4. 将检索到的法条原文写入审查报告的"检索法条依据"列

## 审查原则
1. **先检索，再理解，后判断**：检索法条 → 理解合同 → 对照法条判断
2. **以检索到的法条为准绳**：每次判断必须引用 searchLawKnowledge 返回的真实法条原文
3. **检索不到时如实标注**：如果知识库中未检索到相关法条，标注「未检索到直接相关法条，以下分析基于合同编一般原则」
4. **区分事实与判断**：合同写了什么（事实）vs 检索到的法条怎么规定 vs 两者是否冲突（判断）
5. **给出可操作建议**：不只说"有问题"，要给出具体修改建议和示范条款
6. **不创造不存在的问题**：不要过度解读，不要凭空编造风险

## 审查流程（每一条款均执行）
1. 提取条款核心法律问题
2. **调用 searchLawKnowledge 检索相关法条** ← 强制执行
3. 获得法条原文后，对照合同条款判断
4. 输出：「条款原文 → 检索到的法条原文 → 法律分析 → 风险等级 → 修改建议」

## 风险等级标准
- 🔴 严重（CRITICAL）：条款违反法律强制性规定或严重不公平
- 🟡 需关注（HIGH/MEDIUM）：条款存在一定风险或不够明确
- 🟢 合规（LOW）：条款合法、公平、明确

## 行为约束
- 绝不编造法条内容，所有法条引用必须来源于 searchLawKnowledge 的返回结果
- 使用法律文书规范语言，但避免过于晦涩
- 不确定的判断请标注「需进一步核实」
- 不评价合同当事人的主观意图，只评价合同条款的法律效果
- 不提供税务、会计建议
```

#### 4.2 User Prompt 模板

```markdown
请审查以下合同：

【合同类型】{contract_type}

【合同文本】
{contract_text}

请按照以下格式输出审查报告：
1. 合同概况（一句话总结）
2. 逐条审查意见（表格）
3. 总体评分（三维度）
4. 签约建议
```

### 五、审查报告输出模板

```markdown
# 合同审查报告

## 一、基本信息
| 项目 | 内容 |
|------|------|
| 合同名称 | {contract_name} |
| 合同类型 | {contract_type} |
| 审查日期 | {review_date} |
| 甲方 | {party_a} |
| 乙方 | {party_b} |
| 合同金额 | {amount} |
| 合同期限 | {term} |

## 二、总体评分

| 维度 | 评分（1-10） | 说明 |
|------|------------|------|
| 合法性 | {legality_score}/10 | {legality_comment} |
| 公平性 | {fairness_score}/10 | {fairness_comment} |
| 完整性 | {completeness_score}/10 | {completeness_comment} |
| **综合** | **{overall_score}/10** | |

## 三、逐条审查意见

| # | 条款 | 原文摘要 | 检索法条依据 | 风险等级 | 法律分析 | 修改建议 |
|---|------|---------|------------|---------|---------|---------|
| 1 | {clause_name} | {excerpt} | {retrieved_law_text} | 🔴/🟡/🟢 | {analysis} | {suggestion} |
> 注：「检索法条依据」列的内容来源于 searchLawKnowledge 返回的真实法条原文，不是 LLM 记忆。

## 四、高风险条款汇总

{critical_items_summary}

## 五、签约建议

**建议：{recommendation}**

{detailed_reasoning}

## 六、引用法条清单

| 法条 | 相关内容 |
|------|---------|
| {law_ref} | {relevance} |

---
> 本报告由 LawMind AI 生成，仅供法律参考，不构成正式法律意见。
> 重大合同建议在 AI 审查后由执业律师复核。
```

### 六、文件上传实现

#### 6.1 现有基础设施分析

项目已有完整的文件上传基础设施，合同审查上传将直接复用：

| 现有组件 | 路径 | 用途 |
|---------|------|------|
| FileController | `controller/FileController.java` | 通用文件上传 API (`POST /api/file/upload`) |
| FileUtil | `utils/FileUtil.java` | 文本提取（PDF/Word/TXT） |
| LawFileUpload 实体 | `entity/LawFileUpload.java` | 文件元数据持久化 |
| Apache Tika | `pom.xml` (tika-core 2.9.2 + tika-parsers) | 统一文档解析接口（.doc/.docx/.pdf/.html/.rtf） |
| Apache PDFBox | `pom.xml` (pdfbox 3.0.1) | PDF 文本提取 |
| Apache POI | `pom.xml` (poi 5.2.3 + poi-ooxml 5.2.3) | Word 文本提取 |
| 前端 el-upload | `frontend/src/views/Upload.vue` | Element Plus 拖拽上传组件 |

#### 6.2 合同审查上传流程

```
用户选择合同文件（PDF/Word/图片）
  ↓
┌──────────────────────────────────────────────────┐
│ 前端 (Vue 3 + Element Plus)                       │
│                                                    │
│ 1. <el-upload> 组件                               │
│    - accept=".pdf,.doc,.docx,.txt,.jpg,.png"       │
│    - :before-upload 校验：文件类型 + 大小 < 10MB   │
│    - :on-success 回调：获取提取的文本内容          │
│                                                    │
│ 2. 上传到 POST /api/contract-review/upload         │
│    - FormData: file + mode="contract_review"       │
│    - Headers: Authorization: Bearer <token>         │
└──────────────────────────────────────────────────┘
  ↓ HTTP POST (multipart/form-data)
┌──────────────────────────────────────────────────┐
│ 后端 (Spring Boot)                                │
│                                                    │
│ 3. ContractReviewController.uploadContract()      │
│    - @RequestParam("file") MultipartFile file      │
│    - 校验文件类型（白名单）                         │
│    - 调用 FileUtil.extractText(file) 提取文本      │
│    - 返回 { fileId, fileName, extractedText,       │
│             textLength, wordCount }                │
│                                                    │
│ 4. 合同文本传给 Agent（不回写磁盘）                 │
│    - 文本注入到 Agent SystemMessage 上下文         │
│    - Agent 加载 contract-review Skill             │
│    - 开始多步推理审查                               │
└──────────────────────────────────────────────────┘
  ↓ SSE 流式推送
┌──────────────────────────────────────────────────┐
│ 前端审查结果展示                                   │
│                                                    │
│ 5. 审查报告页面                                    │
│    - 左侧：合同原文（只读，支持高亮风险条款）       │
│    - 右侧：审查报告（六段式）                       │
│    - SSE 进度："正在审查第3条/共15条..."           │
└──────────────────────────────────────────────────┘
```

#### 6.3 前端实现

**合同上传组件** (`frontend/src/views/ContractReview.vue`):

```vue
<template>
  <div class="contract-review">
    <!-- 上传区域 -->
    <el-upload
      ref="uploadRef"
      class="contract-upload"
      drag
      :action="uploadUrl"
      :headers="authHeaders"
      :before-upload="beforeUpload"
      :on-success="handleUploadSuccess"
      :on-error="handleUploadError"
      :limit="1"
      accept=".pdf,.doc,.docx,.txt"
    >
      <el-icon><UploadFilled /></el-icon>
      <div class="el-upload__text">
        将合同文件拖到此处，或<em>点击上传</em>
      </div>
      <template #tip>
        <div class="el-upload__tip">
          支持 PDF / Word / TXT 格式，文件大小不超过 10MB
        </div>
      </template>
    </el-upload>

    <!-- 审查中状态 -->
    <div v-if="reviewing" class="review-progress">
      <el-progress :percentage="reviewProgress" />
      <p>{{ progressMessage }}</p>
    </div>

    <!-- 审查结果 -->
    <div v-if="reviewResult" class="review-result">
      <ContractReviewReport :data="reviewResult" />
    </div>
  </div>
</template>

<script>
import { UploadFilled } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

export default {
  setup() {
    const userStore = useUserStore()
    const uploadUrl = '/api/contract-review/upload'
    const authHeaders = { Authorization: `Bearer ${userStore.token}` }

    const beforeUpload = (file) => {
      // 文件类型白名单校验
      const allowedTypes = [
        'application/pdf',
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'text/plain'
      ]
      if (!allowedTypes.includes(file.type)) {
        ElMessage.error('仅支持 PDF、Word、TXT 格式')
        return false
      }
      // 文件大小校验（10MB）
      if (file.size > 10 * 1024 * 1024) {
        ElMessage.error('文件大小不能超过 10MB')
        return false
      }
      return true
    }
    // ... handleUploadSuccess / handleUploadError
  }
}
</script>
```

**合同原文 + 审查报告双栏布局** (`frontend/src/components/ContractReviewReport.vue`):

```vue
<template>
  <el-row :gutter="20">
    <!-- 左栏：合同原文（只读） -->
    <el-col :span="12">
      <el-card header="合同原文">
        <div class="contract-text" v-html="highlightedText" />
      </el-card>
    </el-col>
    <!-- 右栏：审查报告 -->
    <el-col :span="12">
      <el-card header="审查报告">
        <!-- 总体评分卡片 -->
        <div class="score-cards">
          <el-statistic title="合法性" :value="data.legalityScore" suffix="/10" />
          <el-statistic title="公平性" :value="data.fairnessScore" suffix="/10" />
          <el-statistic title="完整性" :value="data.completenessScore" suffix="/10" />
        </div>
        <!-- 逐条审查 -->
        <el-table :data="data.clauseReviews" stripe>
          <el-table-column prop="clauseName" label="条款" width="120" />
          <el-table-column prop="excerpt" label="原文摘要" />
          <el-table-column label="风险" width="80">
            <template #default="{ row }">
              <el-tag :type="riskTagType(row.severity)">
                {{ row.severity }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="suggestion" label="修改建议" />
        </el-table>
        <!-- 签约建议 -->
        <el-alert :title="data.recommendation" :type="alertType" show-icon />
        <!-- 法条引用 -->
        <div class="law-refs">
          <h4>引用法条</h4>
          <ul>
            <li v-for="ref in data.lawRefs" :key="ref">{{ ref }}</li>
          </ul>
        </div>
      </el-card>
    </el-col>
  </el-row>
</template>
```

#### 6.4 后端实现

**合同审查专用上传接口** (`controller/ContractReviewController.java`):

```java
@RestController
@RequestMapping("/api/contract-review")
public class ContractReviewController {

    private final FileUtil fileUtil;
    private final IntentGate intentGate;
    private final AgentRunner agentRunner;

    public ContractReviewController(FileUtil fileUtil,
                                     IntentGate intentGate,
                                     AgentRunner agentRunner) {
        this.fileUtil = fileUtil;
        this.intentGate = intentGate;
        this.agentRunner = agentRunner;
    }

    /**
     * 上传合同文件并启动审查
     * 返回 SSE 流：上传确认 → 文本提取完成 → 逐条审查进度 → 审查报告
     */
    @PostMapping(value = "/upload", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> uploadAndReview(
            @RequestParam("file") MultipartFile file) {

        String userId = RequestContext.getUserId();

        // 步骤 1：文件校验
        if (file.isEmpty()) {
            return Flux.just(sseEvent("error", "文件为空"));
        }
        String originalFilename = file.getOriginalFilename();
        if (!isAllowedFileType(originalFilename)) {
            return Flux.just(sseEvent("error",
                "不支持的文件格式，仅支持 PDF / Word / TXT"));
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            return Flux.just(sseEvent("error", "文件大小不能超过 10MB"));
        }

        return Flux.create(sink -> {
            try {
                // 步骤 2：提取文本
                sink.next(sseEvent("progress",
                    "{\"step\":\"extracting\",\"message\":\"正在提取文件文本...\"}"));
                String extractedText = fileUtil.extractText(file);
                int wordCount = extractedText.length();

                sink.next(sseEvent("progress",
                    String.format("{\"step\":\"extracted\",\"fileName\":\"%s\",\"wordCount\":%d}",
                        originalFilename, wordCount)));

                // 步骤 3：门控判断（确认是合同审查意图）
                GateResult gateResult = intentGate.process(
                    "请审查以下合同：\n" + extractedText);
                if (!gateResult.accepted()) {
                    sink.next(sseEvent("error", gateResult.rejectResponse()));
                    sink.complete();
                    return;
                }

                // 步骤 4：Agent 多步推理审查
                sink.next(sseEvent("progress",
                    "{\"step\":\"reviewing\",\"message\":\"正在审查合同条款...\"}"));
                // Agent 内部通过 SSE 推送每步进展
                agentRunner.runStream(userId, extractedText, "CONTRACT_REVIEW")
                    .subscribe(
                        event -> sink.next(sseEvent("review_chunk", event)),
                        sink::error,
                        sink::complete
                    );

            } catch (IOException e) {
                sink.next(sseEvent("error", "文件解析失败：" + e.getMessage()));
                sink.complete();
            }
        });
    }

    private boolean isAllowedFileType(String filename) {
        if (filename == null) return false;
        String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return List.of("pdf", "doc", "docx", "txt").contains(ext);
    }

    private ServerSentEvent<String> sseEvent(String event, String data) {
        return ServerSentEvent.<String>builder()
            .event(event)
            .data(data)
            .build();
    }
}
```

#### 6.5 文本提取（复用现有 FileUtil）

```java
// FileUtil.extractText() 现有实现（无需修改）
// 内部使用 Apache Tika 自动检测文件类型并提取文本：
// - .pdf  → PDFBox 解析
// - .docx → POI XWPFDocument 解析
// - .doc  → POI HWPFDocument 解析
// - .txt  → 直接读取 UTF-8

public String extractText(MultipartFile file) throws IOException {
    try (InputStream is = file.getInputStream()) {
        // Tika 自动检测文件类型
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1); // 不限长度
        Metadata metadata = new Metadata();
        metadata.set(Metadata.RESOURCE_NAME_KEY, file.getOriginalFilename());
        parser.parse(is, handler, metadata);
        return handler.toString();
    }
}
```

#### 6.6 安全措施

| 措施 | 实现方式 |
|------|---------|
| 文件类型白名单 | 前端 `accept` + 后端扩展名校验（双重校验） |
| 文件大小限制 | 前端 `before-upload` + 后端 `file.getSize()` 检查（10MB） |
| 路径遍历防护 | 不将文件写入磁盘，仅内存中处理文本 |
| 原始合同不持久化 | 审查完成后仅保留审查报告，原始合同文本不存库 |
| 敏感信息脱敏提示 | 前端上传前提示用户去除身份证号、银行账号等敏感信息 |
| SSE 认证 | 复用现有 Spring Security 认证体系 |

#### 6.7 与现有上传的隔离

| 维度 | 现有 FileController (`/api/file/upload`) | 新增合同审查上传 (`/api/contract-review/upload`) |
|------|----------------------------------------|----------------------------------------------|
| 用途 | 知识库文档上传（存入 RAG 索引） | 合同审查（一次性提取文本，不入库） |
| 文本存储 | 写入 `LawFileUpload` 表 + 向量化索引 | 仅内存中处理，审查完成后丢弃 |
| 返回格式 | JSON（文件信息） | SSE（流式审查进度 + 报告） |
| 触发后续流程 | `DocumentIngestionService` 入库 | Agent 加载 contract-review Skill |

> **设计原则**：合同审查上传与知识库上传隔离。合同是用户的私人文件，不应混入公共知识库。专用接口确保合同文本"阅后即焚"。

### 七、与现有系统的集成

| 集成点 | 现有组件 | 集成方式 |
|--------|---------|---------|
| 意图识别 | `IntentGate` / `IntentClassifierEnhanced` | 扩充 `CONTRACT_REVIEW` 意图类型 + 关键词（"帮我审查合同""这份合同有没有坑"等） |
| 路由决策 | `IntentRouter` | 合同审查一律走 AGENT 通道（需要多步推理） |
| 法条检索 | `LawKnowledgeService.search()` | Agent 审查时自动调用，无需修改 |
| 案例参考 | `CaseSearchService`（如已有） | Agent 可检索类似合同纠纷判例作为参考 |
| 文件上传与解析 | 详见「六、文件上传实现」 | 复用 FileUtil + Tika 文本提取；专用接口 `POST /api/contract-review/upload`（"阅后即焚"不入库不写盘） |
| 审查记录 | `ai_chat` 表 | 审查报告存入聊天记录，附 `contract_review_id` 关联 |
| SSE 推送 | `AgentController` | 复用现有 SSE，Agent 多步推理每步推送进度 |

### 八、实施步骤

| 步骤 | 工作内容 | 工作量 | 前置依赖 |
|------|---------|--------|---------|
| 1 | 扩充意图分类：新增 `CONTRACT_REVIEW` 意图 + 关键词 | 0.25 天 | 无 |
| 2 | 编写 Skill 文件：`manifest.yaml` + `prompt/` + `checklists/` + `patterns/` | 0.5 天 | 步骤 1 |
| 3 | 编写审查报告模板 + 不公平条款模式库 | 0.5 天 | 步骤 2 |
| 4 | 前端：文件上传组件（支持 PDF/Word/图片）+ 审查结果展示页 | 1 天 | 步骤 2 |
| 5 | 后端：文件解析接口（PDF/Word 文本提取） | 0.5 天 | 无 |
| 6 | 集成到 Agent 路由：IntentRouter 新增 CONTRACT_REVIEW → AGENT | 0.25 天 | 步骤 1 |
| 7 | 端到端测试：3 类合同 × 5 份样本 = 15 个审查用例 | 0.5 天 | 全部 |
| **合计** | | **3.5 天** | |

### 九、风险与缓解

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| LLM 幻觉（编造法条） | 审查结论不准确 | System Prompt 明确要求"法条需可查证"；审查报告标注法条来源 |
| 复杂合同超出上下文窗口 | 审查不完整 | 长合同分段审查 + 汇总；超过 50 页的合同提示用户分段上传 |
| 非法律合同（技术合同、外贸合同） | 审查清单覆盖不足 | 明确 Skill 适用边界（民事/商事合同）；专项合同逐步扩展 |
| 用户误解 AI 审查结果 | 过度依赖导致决策失误 | 审查报告固定标注免责声明；提示用户重大合同需律师复核 |
| 合同文本含敏感个人信息 | 隐私泄露风险 | 提示用户上传前脱敏；后端不持久化原始合同文本 |

### 十、与程序编码方案的对比总结

| 维度 | Skill 方案（推荐） | 程序编码方案 |
|------|------------------|------------|
| 开发效率 | 高 —— 主要是 prompt 和 checklist 编写 | 低 —— 需设计类体系、数据库、API |
| 审查质量 | 依赖 LLM 法律推理能力（持续提升） | 依赖规则覆盖度（永远有遗漏） |
| 适应性 | 强 —— 不同合同类型只需新增专项 checklist | 弱 —— 新合同类型需要新代码分支 |
| 可维护性 | 强 —— 法规变化更新 YAML/提示词即可 | 弱 —— 法规变化需修改规则引擎逻辑 |
| 可解释性 | 中 —— LLM 推理过程可展示但不可精确审计 | 高 —— 每条规则命中可追溯 |
| 成本 | LLM 推理调用（约 3000-8000 tokens/次） | 开发人力成本（预估 5-7 天） |
| 适用场景 | MVP 验证、快速迭代、法规频繁变化 | 批量标准化合同（上万份同类型合同自动审查） |

> **结论**：当前阶段 Skill 方案明显优于代码方案。未来如果出现「每日审查 > 500 份同类型标准化合同」的场景，再考虑将高频审查维度固化为代码规则引擎。

---

## 九、记忆系统

> **设计依据**：详细的记忆系统设计方案参见 [记忆系统-设计方案.md](./记忆系统-设计方案.md)。本章仅概述架构和集成方式，不重复完整设计细节。

### 9.1 背景与动机

当前系统缺少**跨会话记忆能力**：

- 用户今天咨询了"劳动合同纠纷"，明天再问"上次那个合同怎么样了"，系统完全不知道在说什么
- 用户偏好（如"请援引法条原文"）每次都需要重新说明
- 合同审查结果不关联到用户记忆，下次上传同类合同时无法提醒历史发现的问题

已有的 `AiChat`、`Conversation` 仅是日志级别的存储，`KnowledgeState` 和 `List<ChatMessage>` 随请求结束销毁——**没有任何信息跨越会话边界**。

### 9.2 记忆 vs 上下文（概念辨析）

```
一个正在读案卷的律师

  上下文 (Context)        = 桌面上正摊开的案卷和法条
  上下文压缩 (Compression) = 把冗长的判例摘要精简为关键要点
  记忆 (Memory)           = 律师大脑中"这个客户我认识，他上次..."

  上下文是"现在在讨论什么"
  记忆是"这个用户是谁、历史背景是什么"
```

| 维度 | 上下文 | 记忆 |
|------|--------|------|
| 生命周期 | 单次请求内 | 跨会话、跨天、永久 |
| 存储位置 | JVM 堆内存 | MySQL + Redis 向量索引 |
| 获取方式 | 始终完整加载在推理循环中 | 按需检索，注入 System Prompt |
| 更新频率 | 每轮 Agent 推理都在变化 | 会话结束后异步提炼，周期性归并 |
| 可变性 | 不可修改历史 | 可更新、归并、衰减、删除 |

### 9.3 记忆类型体系（四类型模型，借鉴 Claude Code）

按使用语义区分四种类型，而非传统的"情节记忆/语义记忆"二分法：

| 类型 | 用途 | 示例 | 衰减周期 |
|------|------|------|----------|
| **USER**（用户画像） | 身份、角色、知识水平、长期偏好 | "HR经理，法律知识中等"、"偏好表格形式" | 180 天 |
| **FEEDBACK**（反馈记忆） | 用户纠正、确认、偏好声明 | "用户指出试用期按第47条计算" | 90 天 |
| **PROJECT**（项目记忆） | 案件、合同审查、咨询事项 | "2026-07-10 审查了张三劳动合同.pdf" | 30 天 |
| **REFERENCE**（参考记忆） | 外部资源指针 | "(2023)京01民终1234号"、"常用《劳动合同法》第47条" | 60 天 |

**为什么不用情节/语义二分法**：四类型按使用场景区分，决定了检索策略、衰减周期、注入方式和用户可见性——这些才是影响系统行为的关键因素。情节/语义的区别仅是存储实现细节。

### 9.4 存储架构

```
第 0 层：原始数据（已有，不可变）
  MySQL: ai_chat (Q&A 原文) + conversation (会话标题)

第 1 层：统一记忆存储（新增）
  MySQL: ai_memory（一张表，type ENUM 区分四类型）
  Redis: 向量索引（复用现有 EmbeddingUtil）

第 2 层：上下文（已有）
  JVM 堆: List<ChatMessage>（当前请求的推理窗口）
```

采用**统一表**设计而非 v1 的三表方案：

```sql
CREATE TABLE ai_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    type ENUM('USER', 'FEEDBACK', 'PROJECT', 'REFERENCE') NOT NULL,
    title VARCHAR(100),          -- 简短标题，用于一级索引注入
    body TEXT NOT NULL,           -- 记忆正文
    summary VARCHAR(300),         -- 从 body 提炼，注入时节省 token
    origin_session_id BIGINT,     -- 溯源
    source_session_ids JSON,      -- 多来源追溯
    confidence DOUBLE DEFAULT 0.5,
    importance DOUBLE DEFAULT 0.5,
    access_count INT DEFAULT 0,
    embedding JSON,               -- 1536 维向量
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_accessed_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_type (user_id, type),
    INDEX idx_user_importance (user_id, importance DESC),
    INDEX idx_user_decay (user_id, last_accessed_at, type)
);
```

### 9.5 两级检索机制

**核心问题**：500+ 条记忆时，全部注入 body 将占用 10000+ token，远超预算。

**解决方案**：两级检索——"知道有什么"和"知道详情"分开。

```
第一级（自动，每次请求，~200 token 预算）：
  SELECT id, type, title, importance FROM ai_memory
  WHERE user_id = ? ORDER BY importance DESC LIMIT 30
  → 注入 System Prompt 作为"记忆索引"

第二级（按需触发）：
  方式 1: 语义相似度匹配 → 自动注入 Top-3 记忆的 body
  方式 2: Agent 调用 retrieveMemory(id) 工具 → 获取详细内容
  方式 3: 用户显式引用（"上次那个合同..."）→ 语义匹配触发
```

### 9.6 记忆生命周期

```
请求前 [检索] → 两级检索注入 System Prompt
请求中 [推理] → Agent 正常执行（可调用 retrieveMemory 工具）
请求后 [提取] → MemoryExtractor（@Async LLM 提取 + 冲突检测 + 向量化）
定时   [归并] → MemoryConsolidator（合并相似 + 类型差异化衰减 + 清理）
```

**分类型衰减策略**：PROJECT(30d, importance×0.5) / REFERENCE(60d, ×0.7) / FEEDBACK(90d, ×0.8) / USER(180d, ×0.9)

**反馈行为闭环**：用户纠正 → 提取为 FEEDBACK 记忆 → 下次同类话题自动注入 → Agent 行为改变 → 用户无需再次纠正

### 9.7 不存储排除规则

以下内容**永远不进入记忆系统**：
- 合同原文（阅后即焚）、个人隐私信息（身份证号/手机号/银行账号）
- 法律知识库已有的法条原文（只需记录法条名称作为 REFERENCE）
- 单次问候/闲聊、Agent 推理过程的中间步骤
- 提取原则：**"这条信息在下一周还会有用吗？"如果答否，不提取。**

### 9.8 AgentRunner 集成

```java
// 集成点 1：推理前注入记忆
public AgentResult execute(String userQuestion, String effectiveSystemPrompt) {
    String memoryContext = memoryManager.retrieveAndFormat(userId, userQuestion);
    String enrichedSystemPrompt = effectiveSystemPrompt;
    if (!memoryContext.isEmpty()) {
        enrichedSystemPrompt = effectiveSystemPrompt + "\n\n" + memoryContext;
    }
    messages.add(SystemMessage.from(enrichedSystemPrompt));
    // ...
}

// 集成点 2：推理后异步提取
if (memoryManager != null && result.success()) {
    memoryManager.extractAsync(userId, conversationId, userQuestion,
                               result.answer(), messages);
}
```

Agent 工具注册：`retrieveMemory(memoryId)` — Agent 可从一级索引中获取 ID 后主动调用，获取记忆详情。

### 9.9 与上下文压缩的关系

记忆系统与已有的 `ContextCompressor` / `KnowledgeState` 是**互补**关系：

| 系统 | 方向 | 时机 | 存储 |
|------|------|------|------|
| ContextCompressor | 向下压缩 | 请求中（上下文过长时） | JVM 堆 |
| KnowledgeState | 向上积累 | 请求中（每轮工具调用后） | JVM 堆 |
| Memory System | 跨时间提取 | 请求后（异步）+ 请求前（检索注入） | MySQL + Redis |

`KnowledgeState` 是"这次请求学到了什么"，记忆系统是"所有历史请求积累了什么"。

---

## 十、测试策略

### 7.1 Tool 层单元测试

每个 Tool 方法独立测试，Mock 其依赖的 Service：

```java
@ExtendWith(MockitoExtension.class)
class LawSearchToolsTest {

    @Mock
    private HybridSearchService hybridSearchService;
    @Mock
    private LawKnowledgeService lawKnowledgeService;
    @Mock
    private EmbeddingUtil embeddingUtil;

    @InjectMocks
    private LawSearchTools lawSearchTools;

    @Test
    void searchLawKnowledge_shouldReturnFormattedResults_whenResultsFound() {
        // Arrange
        when(embeddingUtil.embed("劳动合同法 经济补偿"))
                .thenReturn(new float[1536]);
        when(hybridSearchService.searchHybrid(any(), any(), eq(10)))
                .thenReturn(List.of(/* mock LawKnowledge */));
        
        // Act
        String result = lawSearchTools.searchLawKnowledge(
                "劳动合同法 经济补偿", "劳动法");
        
        // Assert
        assertThat(result).contains("[劳动法]");
        assertThat(result).doesNotContain("[Tool 错误]");
    }

    @Test
    void searchLawKnowledge_shouldReturnError_whenEmbeddingFails() {
        when(embeddingUtil.embed(any())).thenThrow(new RuntimeException("API超时"));
        
        String result = lawSearchTools.searchLawKnowledge("test", "");
        
        assertThat(result).startsWith("[Tool 错误]");
        assertThat(result).contains("API超时");
    }
}
```

### 7.2 Agent 集成测试

使用 `ChatLanguageModel` 的真实施例（需 DashScope API Key 可用）：

```java
@SpringBootTest
class LawAgentIntegrationTest {

    @Autowired
    private LawAgent lawAgent;

    @Test
    void shouldAnswerSimpleLegalQuestion() {
        String answer = lawAgent.answer("test-user-1", 
                "《劳动合同法》第三十九条规定了什么？")
                .execute()  // 同步模式，用于测试
                .content();
        
        assertThat(answer).isNotEmpty();
        assertThat(answer).contains("劳动合同法");
        // 注意：由于 LLM 的非确定性，不做精确断言
    }
}
```

### 7.3 黄金数据集评估

复用现有的 `GoldenDatasetEvaluator` 和黄金数据集，在 Agent 模式下重新评估：

```
传统模式 RAGAS 得分（基准） → Agent 模式 RAGAS 得分 → 对比差异
```

如果 Agent 模式下幻觉率（faithfulness）明显上升，需要调整 SystemMessage 中的核实要求。

---

## 十一、推荐实施路径

综合考虑（已有扎实 RAG 基础 + 想做 Agent + 每步独立可验证），推荐按以下顺序实施：

```
第 0 步（★ 已完成 2026-06-10）：实现意图识别门控
    ↓ 输出：IntentGate + DomainGate + IntentClassifierEnhanced + IntentRouter
    ↓ 三层门控流水线：领域判断 → 意图分类 → 路由决策
    ↓ 四通道分流：快速（简单问题）/ Agent（复杂问题）/ 混合（文书）/ 拒绝（非法律）
    ↓ 时间：1.5 天 ✓
    ↓ 交付物：agent/gate/ 包（11 文件）+ intent-gate.yml + 65 单元测试
    ↓
第 1 步：封装 @Tool（了解 Agent 基础机制）
    ↓ 输出：LawSearchTools.java + LawIntentTools.java + LawVerificationTools.java
    ↓ 不影响现有功能，可独立测试
    ↓ 时间：1~2 天
    ↓ 交付物：3 个 Tool 类 + 单元测试
    ↓
第 2 步：创建 LawAgent AiService 接口
    ↓ 输出：AgentConfig.java + AgentController.java + AgentAskRequest.java
    ↓ 与现有 /api/ai-chat/ask-stream 接口并存
    ↓ 用 curl / Postman 验证 Agent 接口可用
    ↓ 时间：0.5~1 天
    ↓ 交付物：1 个 Agent 接口 + 3 个配置/控制器文件
    ↓
第 3 步：前端灰度开关 + A/B 对比测试
    ↓ 输出：前端 "Agent 模式" 切换按钮
    ↓ 收集两种模式的回答质量数据
    ↓ 时间：1 天
    ↓ 交付物：前端开关 + 用户反馈对比数据
    ↓
第 4 步：引入 ReAct 规划（SystemMessage 优化）
    ↓ 输出：增强版 SystemMessage，复杂问题的多步推理
    ↓ 用 5~10 个复杂法律问题做对比测试
    ↓ 时间：1~2 天
    ↓ 交付物：优化后的 SystemMessage + 对比测试报告
    ↓
第 5 步（可选）：探索多 Agent 协作
    ↓ 输出：检索 Agent + 校验 Agent + 协调 Agent
    ↓ 准备 3~5 个需要分工协作的典型场景
    ↓ 时间：3~5 天
    ↓ 交付物：多 Agent 系统 + 评估报告（适合毕业设计/论文）
```

**每步都能独立出成果**，不会出现"做了一大堆最后跑不起来"的情况。

---

## 十二、与现有功能的集成细节

### 9.1 前端灰度切换实现

在 `Consultation.vue` 中添加模式切换开关：

```vue
<!-- 在 chat-header 中添加 -->
<div class="mode-switch">
  <el-switch
    v-model="useAgentMode"
    active-text="Agent"
    inactive-text="传统"
    @change="handleModeSwitch"
  />
</div>
```

```javascript
// 在 <script setup> 中添加
const useAgentMode = ref(false);

function handleModeSwitch(val) {
  ElMessage.info(val ? '已切换到 Agent 智能模式' : '已切换到传统检索模式');
}

// 修改 sendMessage 中的 API 端点
async function sendMessage() {
  // ...
  const endpoint = useAgentMode.value 
    ? '/api/agent/ask'       // Agent 模式
    : '/api/ai-chat/ask-stream';  // 传统模式
  // ...
}
```

### 9.2 复用现有基础设施清单

| 现有功能 | 是否复用 | 说明 |
|---------|---------|------|
| Redis 向量存储 | ✅ 完全复用 | Tool 内部调用现有 HybridSearchService |
| MySQL 知识库 | ✅ 完全复用 | LawKnowledgeService CRUD 不变 |
| JWT 认证 | ✅ 完全复用 | AgentController 同样通过 RequestContext.getUserId() |
| 限流（Sentinel） | ✅ 完全复用 | Agent 接口注册同样的限流规则 |
| 日志审计（SecurityAuditAspect） | ✅ 完全复用 | 切面自动拦截 Agent 接口 |
| PII 脱敏（PiiUtil） | ✅ 完全复用 | Agent 接口同样后处理 |
| 敏感话题过滤（SensitiveTopicFilter） | ✅ 完全复用 | 在 Agent Controller 层调用 |
| 引用校验 | ✅ 复用 + 增强 | 封装为 verifyCitation Tool，Agent 可主动调用 |
| 用户反馈（like/dislike） | ✅ 完全复用 | 无论哪种模式，都收集用户反馈 |
| 质量评估（RAGAS） | ⚠️ 需适配 | Agent 模式下的评估指标阈值可能需要调整 |
| 会话管理（ConversationService） | ✅ 完全复用 | 但 ChatMemory 独立于 Conversation 表 |

### 9.3 降级策略

```
正常流程：
  用户请求 → Agent 模式 → Tool 调用 → LLM 回答 → 返回

降级触发条件：
  - Agent 模式下连续 3 次 Tool 调用失败
  - SseEmitter 超时（120 秒）
  - ChatLanguageModel Bean 不可用

降级流程：
  Agent 模式 ↓（失败）
  → 自动降级到固定管线（RagService.processQuestionStream）
  → 返回答案 + 标记来源为 "fallback"
  → AgentMetricsCollector.recordFallback()

降级是自动且用户无感知的。
```

---

## 十三、关键 Q&A

**Q: Agent 改造需要修改现有代码吗？**
A: 不需要。所有 Agent 相关代码都是新增文件（`agent/` 目录），现有 RAG 管线代码不变。

**Q: Agent 模式会替代固定管线吗？**
A: 短期内不会。两个接口并存（`/api/ai-chat/ask-stream` 和 `/api/agent/ask`），通过前端开关切换。等 Agent 模式稳定后可考虑设为默认。

**Q: 需要升级 LangChain4j 版本吗？**
A: 不需要。0.36.0 完全支持 @Tool、AiServices、TokenStream、ChatMemory。升级到 0.40.0+ 是可选优化。

**Q: Agent 模式会多慢？**
A: 简单问题几乎无差异（1~2 个 Tool）；复杂问题可能慢 2~3 秒（多 2~3 轮 Tool 调用）。但回答质量预期会更高。

**Q: 如果 Agent 调用了错误的 Tool 怎么办？**
A: Tool 返回描述性错误文本（不抛异常），LLM 会理解失败原因并调整策略。这是 Agent 架构的核心优势——自愈能力。

---

## 十四、总结

| 维度 | 当前状态 | Agent 改造后 |
|------|---------|-------------|
| 流程控制 | 代码硬编码（RagServiceImpl 800+ 行） | LLM 自主决策 |
| 工具调用 | 固定顺序（检索→Rerank→生成） | 动态选择 + 可跳过 |
| 复杂问题处理 | 单次检索+生成 | 多步推理+验证 |
| 扩展性 | 加功能需修改管线代码 | 新增 @Tool 方法即扩展 |
| 可观测性 | 日志散落各 Service | Tool 调用链天然可追踪 |
| 研究价值 | 中等（RAG 参数优化） | 高（Agent 架构 + 多 Agent 协作） |
| Token 成本 | 固定 ~2000 tokens/次 | 可变 2000~15000 tokens/次 |

**核心结论**：你的项目已有非常扎实的 RAG 基础（混合检索、Rerank、相似问题、热点缓存、质量评估），转型 Agent 的主要工作是把现有服务声明为 `@Tool`，然后用 `AiServices` 把它们串起来。**不需要推倒重来**。每一步改造都有明确产出，适合作为毕业设计或求职项目亮点。

---

## 附录：如何扩展本文档

### 新增章节模板

路线图采用「编号章节 + 子章节」结构，新增章节时按以下规则：

1. **新增整章**：在合适位置插入 `## 十三、XXX`（使用连续中文数字序号），调整后续章节编号
2. **在现有章节内新增子节**：使用 `###` 子标题，编号与父章节对应
3. **同步更新**：
   - 更新「文档导航」对照表
   - 更新实施计划中的「文档导航」对应行
   - 在 `## 十一、推荐实施路径` 中添加实施步骤
4. **章节结构建议**：
   ```markdown
   ## N、章节名称
   
   ### 背景与动机（为什么需要）
   ### 架构设计（核心方案）
   ### 关键代码示例（可选）
   ### 实施步骤（怎么做）
   ### 验证标准（如何确认成功）
   ```

### 文档版本规则

- 新增模块/章节：次版本号 +0.1（如 v2.3 → v2.4）
- 仅修正/更新内容：修订号 +0.0.1（如 v2.3 → v2.3.1）

---

*文档版本：v2.5*
*更新日期：2026-07-12*
*基于项目实际状态：Spring Boot 3.5.12 + LangChain4j 0.36.0 + DashScope qwen-plus + Redis Vector*
*本次更新：新增「九、记忆系统」—— 四类型记忆模型（USER/FEEDBACK/PROJECT/REFERENCE，借鉴 Claude Code）+ 统一存储表（ai_memory）+ 两级检索机制 + 分类型差异化衰减 + 反馈行为闭环 + 不存储排除规则；章节重新编号（九~十三 → 十~十四）*
- 新增模块/章节：次版本号 +0.1（如 v2.3 → v2.4）
