# 04 · 环境层 —— 本地开发环境（Development）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`src/main/resources/application.yml`、`src/main/resources/application-dev.example.yml`、`src/main/resources/sql/init_schema.sql`、`frontend/vite.config.js`

本文档描述 LawMind 本地开发环境的搭建、初始化与启动流程，面向新加入的开发者。

---

## 1. 技术栈与前置依赖

| 组件 | 版本要求 | 用途 | 校验命令 |
|------|---------|------|----------|
| JDK | 17 | 后端运行（Spring Boot 3.5.12） | `java -version` |
| Maven | 3.x（或使用仓库自带 `mvnw`/`mvnw.cmd` wrapper） | 后端构建 | `mvn -version` |
| MySQL | 8.0+ | 业务主库（库名 `lawmind`） | `mysql --version` |
| Redis Stack | 7.4 | 缓存 + 向量检索（需 RediSearch/RedisJSON 模块） | `redis-cli --version`、`redis-cli MODULE LIST` |
| Node.js | 18+ | 前端（Vue 3 + Vite 5） | `node -v` |

> 注意：向量检索依赖 RediSearch（`FT.CREATE`）与 RedisJSON 模块，**必须安装 Redis Stack**（自带模块），不能使用裸 Redis。

---

## 2. 数据库初始化

初始化脚本位于 `src/main/resources/sql/init_schema.sql`，一条命令完成建库 + 建表 + 默认账号：

```bash
mysql -u root -p < src/main/resources/sql/init_schema.sql
```

| 项目 | 值 |
|------|-----|
| 数据库名 | `lawmind`（`CREATE DATABASE IF NOT EXISTS`，字符集 `utf8mb4`） |
| 核心表 | user、conversation、ai_chat、law_knowledge、knowledge_chunk、law_vector_task、law_file_upload、security_audit_log、ai_memory 等 13 张 |
| 默认管理员 | 用户名 `admin`，密码 `123456`（bcrypt `$2b$12$aknPp...` 加密存储） |

后端配置的默认管理员用户 ID：`lawmind.admin-user-id: 4`（见 `application.yml`）。

---

## 3. 配置文件复制

开发环境使用 profile `dev`（`application.yml` 中 `spring.profiles.active: dev` 已设置）。

```bash
# 复制 dev 示例为实际配置（已被 .gitignore 忽略，不入库）
cp src/main/resources/application-dev.example.yml src/main/resources/application-dev.yml
```

复制后需设置两个环境变量（见第 5 节），否则启动会因空 Key / 空密码连接失败。

---

## 4. 启动命令

```bash
# 后端（仓库根目录）
./mvnw spring-boot:run            # Linux/macOS
mvnw.cmd spring-boot:run          # Windows

# 前端（frontend/ 目录）
cd frontend
npm install                       # 首次
npm run dev                       # 开发服务器，端口 5173
```

> `application.yml` 通过 `spring.config.import: classpath:intent-gate.yml` 加载意图门控规则配置（`lawmind.agent.gate.*`），无需额外步骤。

---

## 5. 端口与访问入口

| 服务 | 地址 | 说明 |
|------|------|------|
| 后端 | `http://localhost:8080/api` | `server.port: 8080`，`context-path: /api`，UTF-8 强制编码 |
| 前端 | `http://localhost:5173` | Vite 开发服务器，`/api` 代理到 `http://localhost:8080` |
| CORS | 允许来源 | `http://localhost:5173`（见 `SecurityConfig`） |
| 健康检查 | `http://localhost:8080/api/actuator/health` | Actuator 仅暴露 `health`、`info` |

---

## 6. 环境变量

本地开发需注入两个环境变量：

| 变量 | 说明 | 取值示例 |
|------|------|----------|
| `MYSQL_PASSWORD` | MySQL root 密码 | 你的本地数据库密码 |
| `DASHSCOPE_API_KEY` | 阿里百炼 DashScope API Key（格式 `sk-...`） | 从百炼控制台获取 |

```bash
# 示例：Linux/macOS
export MYSQL_PASSWORD=your_password
export DASHSCOPE_API_KEY=sk-xxxx

# Windows (PowerShell)
$env:MYSQL_PASSWORD="your_password"
$env:DASHSCOPE_API_KEY="sk-xxxx"
```

`application-dev.example.yml` 中的占位方式：

```yaml
spring:
  datasource:
    password: ${MYSQL_PASSWORD:}
langchain4j:
  dashscope:
    chat-model:
      api-key: ${DASHSCOPE_API_KEY:}
      log-requests: true
      log-responses: true
```

> ⚠️ 注意：`application.yml` 中 DB 密码存在默认值 `${MYSQL_PASSWORD:197058Li}`、DashScope Key 为明文硬编码（见 `docs/05-infrastructure/dashscope.md` 安全标注）。本地个人环境可接受，**禁止用于生产**。

---

## 7. 启动自检清单

| 检查项 | 期望结果 |
|--------|----------|
| MySQL `lawmind` 库存在 | `SHOW DATABASES;` 含 `lawmind` |
| Redis Stack 模块加载 | `redis-cli MODULE LIST` 含 RediSearch |
| 后端启动日志 | Redis 向量索引创建/命中（`RedisIndexInitializer`） |
| 前端页面 | `http://localhost:5173` 可访问，登录页正常 |
| 管理员登录 | `admin` / `123456` |
