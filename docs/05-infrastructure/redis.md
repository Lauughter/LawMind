# 05 · 基础设施层 —— Redis Stack（缓存 + 向量检索）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`config/RedisConfig.java`、`config/RedisIndexInitializer.java`、`config/RagConfig.java`、`application.yml`、`pom.xml`

Redis 承担两类职责：**缓存/锁/计数**（分布式锁、会话缓存）与 **向量存储**（RAG 知识库检索）。

---

## 1. 版本与选型

| 项 | 值 |
|----|-----|
| 服务端 | Redis Stack 7.4（含 **RediSearch** + **RedisJSON** 模块） |
| 客户端 | Jedis（`pom.xml` 排除 Lettuce，引入 `redis.clients:jedis`） |
| 客户端配置 | `spring-boot-starter-data-redis` |
| 向量存储库 | `dev.langchain4j:langchain4j-redis-spring-boot-starter` / `langchain4j-redis` **0.36.0** |
| 向量维度 | 1536（`langchain4j.redis.dimension: 1536`，与 embedding 模型一致） |

> 裸 Redis 无法执行 `FT.CREATE` 向量索引，必须使用带模块的 Redis Stack。

---

## 2. 连接配置（Jedis）

`application.yml`：

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password: ""
      database: 0
      jedis:
        pool:
          enabled: true
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms
```

`RedisConfig.java` 关键点：

| 项 | 值 |
|----|-----|
| 工厂 | `JedisConnectionFactory` + `JedisClientConfiguration` |
| 连接/读超时 | 各 5 秒（`Duration.ofSeconds(5)`） |
| 密码处理 | 空串转为 `null`（`config.setPassword(password.isEmpty() ? null : password)`） |
| 序列化 | Key/HashKey：`StringRedisSerializer`；Value/HashValue：`GenericJackson2JsonRedisSerializer` |
| 容错 | 连接池运行时管理连接，Redis 暂不可用也不返回 null，自动重试 |

---

## 3. Key 设计与命名空间

| 前缀 | 用途 | 配置来源 |
|------|------|----------|
| `law:vector:` | 法律知识向量（HASH，RediSearch 索引对象） | `rag.redis.key-prefix.law-vector` |
| `law:vectorize:scheduler:lock` | 向量化调度分布式锁 | `lawmind.vectorize.scheduler.scheduler-lock-key` |

索引名：

| 索引 | 对应键前缀 | 说明 |
|------|-----------|------|
| `idx:law_knowledge` | `law:vector:` | 法律知识库向量索引 |

> **历史遗留清理**：`idx:similar_question`、`similar:question:*`、`hot:question:*`、`visit:*` 属于已删除的「相似问题 / 热点问题」模块。旧版本部署升级后，如 Redis 中仍有残留，可手动清理：`FT.DROPINDEX idx:similar_question DD` 后 `DEL similar:question:* hot:question:* visit:*`（`idx:law_knowledge`、`langchain4j-index`、`idx:law_chunk` 为保留索引，勿删）。

---

## 4. 向量索引结构（RedisIndexInitializer）

`RedisIndexInitializer` 实现 `CommandLineRunner`，应用启动时自动创建缺失索引并缓存状态（`IndexStatusCache`）。

### 4.1 法律知识库索引 `idx:law_knowledge`

```bash
FT.CREATE idx:law_knowledge ON HASH PREFIX 1 law:vector: SCHEMA \
  title      TEXT   SORTABLE \
  law_type   TAG    SORTABLE \
  content    TEXT \
  vector     VECTOR FLAT 6 TYPE FLOAT32 DIM 1536 DISTANCE_METRIC COSINE
```

| 字段 | 类型 | 属性 |
|------|------|------|
| `title` | TEXT | SORTABLE |
| `law_type` | TAG | SORTABLE |
| `content` | TEXT | — |
| `vector` | VECTOR(FLAT, FLOAT32, DIM=1536, COSINE) | 相似度检索 |

### 4.2 生命周期

```
启动 → 检查索引是否存在（RedisIndexUtil.indexExists）
   ├─ 存在 → 跳过创建
   └─ 不存在 → 执行 FT.CREATE → 再次校验
→ IndexStatusCache.put(indexName, exists)  缓存状态
→ 失败 → IndexStatusCache.put(indexName, false, 60_000)  60s TTL 后重试
```

---

## 5. 检索相关配置

### 5.1 相似度阈值（`rag.threshold.*`）

| 项 | application.yml | 代码默认（RagConfig） | 说明 |
|----|----------------|----------------------|------|
| `law-knowledge` | 0.55 | 0.75 | 知识库命中阈值 |
| `filter` | 0.40 | 0.70 | 过滤阈值 |

> 配置值会覆盖代码默认值；不同阈值影响召回质量，调参以 `application.yml` 为准。

### 5.2 检索数量与策略（`rag.search.*`）

```yaml
rag:
  search:
    top-k: 15            # 初检返回条数
    hybrid:
      enabled: true      # 混合检索（向量 + 关键词）
    mmr:
      enabled: true      # MMR 重排
      lambda: 0.85       # 相关性与多样性权衡
    hyde:
      enabled: false
    rerank:
      enabled: true
      api-key: sk-5efba3d1a796499797ecc5d474d45ec6   # ⚠️ 硬编码
      model: qwen3-rerank
      top-n: 10
      candidate-top-k: 30
```

## 6. 去重与引用验证

```yaml
rag:
  dedup:
    enabled: true        # 入库前去重
    threshold: 0.92      # 高于该相似度视为重复
  citation:
    verification:
      enabled: true      # 回答中法条引用来源验证
```

---

## 7. 运维命令参考

```bash
# 查看索引
FT._LIST

# 查看索引结构
FT.INFO idx:law_knowledge

# 全文/向量搜索
FT.SEARCH idx:law_knowledge "合同" LIMIT 0 10
FT.SEARCH idx:law_knowledge "*=>[KNN 5 @vector $B]" \
  PARAMS 2 B <binary_vector> DIALECT 2

# 内存统计
MEMORY USAGE law:vector:1
INFO modules
```
