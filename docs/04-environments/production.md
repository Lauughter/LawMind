# 04 · 环境层 —— 生产环境（Production）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`src/main/resources/application-prod.yml`（部署环境事实源）、`src/main/resources/application.yml`

生产 profile 以 `application-prod.yml` 覆盖默认配置，**所有敏感信息通过环境变量注入**，不落盘任何明文密钥。

---

## 1. 与默认配置的差异总览

| 配置项 | 默认（application.yml） | 生产（application-prod.yml） |
|--------|------------------------|------------------------------|
| 数据源 | `jdbc:mysql://localhost:3306/lawmind`，密码 `${MYSQL_PASSWORD:197058Li}` | `${DB_URL}` / `${DB_USERNAME}` / `${DB_PASSWORD}` |
| Redis 地址 | `localhost:6379`，无密码，db 0 | `${REDIS_HOST}` / `${REDIS_PORT:6379}` / `${REDIS_PASSWORD:}` / `${REDIS_DATABASE:0}` |
| Redis 连接池 | max-active 8 / max-idle 8 / min-idle 0 / max-wait -1ms | max-active **20** / max-idle **10** / min-idle **2** / max-wait **5000ms** |
| DashScope Key | 明文硬编码 `sk-5efba3d1...`（⚠️ 见第 5 节） | `${DASHSCOPE_API_KEY}`（必填，无默认值） |
| DashScope 模型 | `qwen-plus` / `text-embedding-v2` | `${DASHSCOPE_CHAT_MODEL:qwen-plus}` / `${DASHSCOPE_EMBEDDING_MODEL:text-embedding-v2}` |
| 请求/响应日志 | dev 示例开启 `log-requests/log-responses: true` | 全部 `false`（不落 LLM 明文） |
| 日志级别 | `dev.langchain4j: DEBUG`、`com.lhs.lawmind: DEBUG`（dev） | `dev.langchain4j: WARN`、`com.lhs.lawmind: INFO` |

---

## 2. 数据源配置（DB）

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: com.mysql.cj.jdbc.Driver
```

| 环境变量 | 说明 | 示例 |
|----------|------|------|
| `DB_URL` | JDBC 连接串 | `jdbc:mysql://db.internal:3306/lawmind?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai` |
| `DB_USERNAME` | 数据库账号 | `lawmind_app`（生产建议非 root） |
| `DB_PASSWORD` | 数据库密码 | 由密钥管理注入 |

---

## 3. Redis 配置

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DATABASE:0}
      jedis:
        pool:
          enabled: true
          max-active: 20
          max-idle: 10
          min-idle: 2
          max-wait: 5000ms
```

生产连接池调优要点（相对 dev）：

- `max-active: 20`（dev 为 8）——支撑更高并发；
- `max-wait: 5000ms`（dev 为 `-1ms` 无限等待）——**有限等待**，避免请求无限阻塞在取连接上。

> Redis 需为 Redis Stack 7.4（含 RediSearch/RedisJSON），否则向量索引 `FT.CREATE` 失败。

---

## 4. DashScope（阿里百炼）配置

```yaml
langchain4j:
  dashscope:
    chat-model:
      api-key: ${DASHSCOPE_API_KEY}
      model-name: ${DASHSCOPE_CHAT_MODEL:qwen-plus}
      log-requests: false
      log-responses: false
    streaming-chat-model:
      api-key: ${DASHSCOPE_API_KEY}
      model-name: ${DASHSCOPE_CHAT_MODEL:qwen-plus}
      log-requests: false
      log-responses: false
    embedding-model:
      api-key: ${DASHSCOPE_API_KEY}
      model-name: ${DASHSCOPE_EMBEDDING_MODEL:text-embedding-v2}
      max-segments-per-batch: 10
```

| 环境变量 | 默认 | 说明 |
|----------|------|------|
| `DASHSCOPE_API_KEY` | 无（**必填**） | 百炼 API Key |
| `DASHSCOPE_CHAT_MODEL` | `qwen-plus` | 对话模型 |
| `DASHSCOPE_EMBEDDING_MODEL` | `text-embedding-v2` | 嵌入模型（1536 维） |

生产关闭 `log-requests/log-responses`，避免用户问题/回答明文进入日志。

---

## 5. 安全配置与部署清单

### ⚠️ 未随 prod 覆盖的硬编码项（重点）

以下配置仅在 `application.yml` 中存在明文，`application-prod.yml` **未覆盖**，生产部署前必须改造：

| 配置项 | 位置 | 明文值 | 风险 |
|--------|------|--------|------|
| DashScope API Key（chat/streaming/embedding） | `langchain4j.dashscope.*.api-key` | `sk-5efba3d1a796499797ecc5d474d45ec6` | 已由 prod 覆盖，但 Key 已暴露在代码库，**应轮换** |
| Rerank API Key | `rag.search.rerank.api-key` | `sk-5efba3d1...` | **prod 无覆盖**，生产会沿用明文 Key |
| JWT 密钥 | `jwt.secret` | `lawmind_secret_key` | 生产 Token 可被伪造，**必须外置** |
| MySQL 密码默认值 | `spring.datasource.password` | `${MYSQL_PASSWORD:197058Li}` | prod 已覆盖，但明文曾入库 |

> ⚠️ 当前硬编码，生产应改环境变量。建议通过 `SPRING_APPLICATION_JSON`、环境变量或外部配置中心注入，并轮换已暴露的 Key。

### 生产安全清单

- [ ] 全部敏感项改为环境变量注入（上表 4 项）；
- [ ] `DASHSCOPE_API_KEY`、`DB_PASSWORD` 等由密钥管理服务（Vault/KMS）下发；
- [ ] DB 使用最小权限账号，非 root；
- [ ] Redis 开启 `requirepass`（`REDIS_PASSWORD`）；
- [ ] 日志级别保持 `WARN`/`INFO`，LLM 明文日志关闭；
- [ ] Actuator 仅暴露 `health`、`info`（默认配置已满足）。

---

## 6. 部署方式（参考）

```bash
# 构建
./mvnw clean package -DskipTests

# 运行（注入全部环境变量后）
java -jar target/LawMind-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=prod
```

启动后自检：

- `/api/actuator/health` 返回 `UP`；
- Redis 向量索引初始化成功（`RedisIndexInitializer`）；
- 管理员账号 `admin` 可登录（密码由生产初始化脚本设置，禁止沿用默认 `123456`）。
