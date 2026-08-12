# 05 · 基础设施层 —— 阿里百炼 DashScope（LLM 接入）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`application.yml`、`application-dev.example.yml`、`application-prod.yml`、`pom.xml`、`config/LangChain4jConfig.java`

通过 **LangChain4j DashScope Starter** 接入阿里云百炼（DashScope/Bailian），提供对话、流式对话、向量嵌入与 Rerank 精排四类能力。

---

## 1. 依赖与自动装配

```xml
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope-spring-boot-starter</artifactId>
    <version>0.36.0</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope</artifactId>
    <version>0.36.0</version>
</dependency>
```

`config/LangChain4jConfig.java`：纯空配置类，**依赖 Spring Boot 自动配置**——根据 `application.yml` 自动创建 `ChatLanguageModel`、`StreamingChatLanguageModel`、`EmbeddingModel` 等 Bean。

---

## 2. 模型清单

| 能力 | 配置键 | 模型 | 说明 |
|------|--------|------|------|
| 对话 | `langchain4j.dashscope.chat-model` | `qwen-plus` | 普通问答 |
| 流式对话 | `langchain4j.dashscope.streaming-chat-model` | `qwen-plus` | SSE 流式输出 |
| 嵌入 | `langchain4j.dashscope.embedding-model` | `text-embedding-v2` | **1536 维**，batch 上限 25/次（配置 10） |
| Rerank 精排 | `rag.search.rerank` | `qwen3-rerank` | RAG 精排，候选 30 → 精排 10 |

---

## 3. 配置结构（application.yml）

```yaml
langchain4j:
  dashscope:
    chat-model:
      api-key: sk-5efba3d1a796499797ecc5d474d45ec6   # ⚠️ 硬编码
      model-name: qwen-plus
    streaming-chat-model:
      api-key: sk-5efba3d1a796499797ecc5d474d45ec6   # ⚠️ 硬编码
      model-name: qwen-plus
    embedding-model:
      api-key: sk-5efba3d1a796499797ecc5d474d45ec6   # ⚠️ 硬编码
      model-name: text-embedding-v2
  redis:
    dimension: 1536                                    # 与 embedding 维度一致

rag:
  search:
    rerank:
      enabled: true
      api-key: sk-5efba3d1a796499797ecc5d474d45ec6   # ⚠️ 硬编码（prod 未覆盖）
      model: qwen3-rerank
      top-n: 10
      candidate-top-k: 30
```

---

## 4. ⚠️ API Key 安全标注（必须整改）

**当前硬编码，生产应改环境变量。** `application.yml` 中 4 处 API Key 为明文：

| 位置 | 当前值 | 生产覆盖情况 |
|------|--------|--------------|
| `langchain4j.dashscope.chat-model.api-key` | `sk-5efba3d1...` | ✅ 被 `application-prod.yml` `${DASHSCOPE_API_KEY}` 覆盖 |
| `langchain4j.dashscope.streaming-chat-model.api-key` | `sk-5efba3d1...` | ✅ 同上 |
| `langchain4j.dashscope.embedding-model.api-key` | `sk-5efba3d1...` | ✅ 同上 |
| `rag.search.rerank.api-key` | `sk-5efba3d1...` | ❌ **prod 无覆盖**，生产沿用明文 |

建议：将 `rag.search.rerank.api-key` 改为 `${DASHSCOPE_API_KEY}`（或独立 `RERANK_API_KEY`），并**轮换已暴露的 Key**。

---

## 5. 环境差异

| 维度 | dev（application-dev.example.yml） | prod（application-prod.yml） |
|------|-----------------------------------|------------------------------|
| API Key | `${DASHSCOPE_API_KEY:}`（空默认） | `${DASHSCOPE_API_KEY}`（必填） |
| 请求/响应日志 | `log-requests: true`、`log-responses: true` | `false` |
| 嵌入 batch | `max-segments-per-batch: 10` | `10` |
| langchain4j 日志 | `DEBUG` | `WARN` |
| 模型名 | 固定 `qwen-plus` / `text-embedding-v2` | `${DASHSCOPE_CHAT_MODEL:qwen-plus}` / `${DASHSCOPE_EMBEDDING_MODEL:text-embedding-v2}` |

> 生产环境变量：`DASHSCOPE_API_KEY`、`DASHSCOPE_CHAT_MODEL`（默认 qwen-plus）、`DASHSCOPE_EMBEDDING_MODEL`（默认 text-embedding-v2）。

---

## 6. 使用示例

```java
// 注入自动配置的 Bean
@Autowired
private ChatLanguageModel chatModel;

@Autowired
private StreamingChatLanguageModel streamingChatModel;

@Autowired
private EmbeddingModel embeddingModel;

// 对话
String answer = chatModel.generate("劳动合同到期不续签有赔偿吗？");

// 嵌入（1536 维）
Embedding embedding = embeddingModel.embed("劳动法").content();
List<Float> vector = embedding.vector(); // 长度 1536

// 流式
streamingChatModel.generate("什么是工伤认定？", new StreamingResponseHandler<>() {
    @Override public void onNext(String token) { /* 逐块输出 */ }
    @Override public void onComplete(Response<AiMessage> r) { /* 完成 */ }
    @Override public void onError(Throwable e) { /* 错误 */ }
});
```

---

## 7. 相关文档

- 向量维度配套 Redis：`docs/05-infrastructure/redis.md`（`langchain4j.redis.dimension: 1536`）
- 检索精排链路：`docs/06-reference/` 检索策略文档（MMR / RRF / Rerank）
- 生产环境变量清单：`docs/04-environments/production.md`
