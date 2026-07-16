<!-- 法律智能助手 Logo 区域 -->
<br />
<p align="center">
  <h1 align="center">⚖️ LawMind</h1>
  <p align="center">面向法律领域的智能问答平台 —— 从文档检索到多步 Agent 推理的完整链路</p>
</p>

<br />

---

## 项目简介

LawMind 是一款面向中国法律垂直场景的智能问答平台，基于 **Spring Boot 3.5 + LangChain4j** 构建。系统支持法律知识检索、法条查询、金额计算、合同审查、文书起草等常见法律服务场景，核心能力包括：

- **多级 RAG 混合检索管道**：BM25 全文 + 向量语义 → RRF 融合 → Rerank 精排 → MMR 去重 → 双阈值过滤
- **ReAct 多工具 Agent**：7 个法律工具自主调用，最多 5 轮迭代推理
- **四级意图门控**：领域判断 → 意图分类 → 复杂度评估 → 快慢分流路由
- **渐进式上下文压缩**：解决多轮工具调用导致的 token 膨胀问题
- **跨会话记忆系统**：四类型记忆模型（用户 / 反馈 / 事项 / 引用），支持 LLM 自动提取和语义检索

前端提供「传统模式」（RAG 快速检索）和「Agent 模式」（多步推理 + 工具调用）两种交互方式，用户可根据问题复杂度自由切换。

---

## 技术栈

| 层级 | 技术选型 |
|------|----------|
| **框架** | Spring Boot 3.5、Java 17、Maven |
| **LLM 集成** | LangChain4j 0.36 + 阿里 DashScope（qwen-plus / qwen3-rerank / text-embedding-v2） |
| **向量存储** | Redis Stack 7.4（RediSearch + FLOAT32 向量索引） |
| **数据库** | MySQL 8.0 + MyBatis（纯 XML 映射） |
| **前端** | Vue 3 + Vite + Element Plus + Pinia + SSE 流式响应 |
| **安全与运维** | Spring AOP（限流 / 审计）、JWT 认证、Sentinel 熔断降级、Nginx |

---

## 架构概览

```
用户请求
  │
  ▼
┌──────────────────────────────────────────────┐
│  意图门控（Intent Gate）                        │
│  DomainGate → IntentClassifier → Router       │
│  判断：法律/非法律 → 7类意图 → 快/慢/混合通道     │
└──────────────────────────────────────────────┘
  │                    │                    │
  ▼                    ▼                    ▼
┌────────┐      ┌──────────┐        ┌──────────┐
│ 快速通道 │      │ 混合通道   │        │ Agent通道 │
│ (Fast)  │      │ (Hybrid)  │        │ (Agent)   │
│         │      │           │        │           │
│ 关键词   │      │ 文书起草   │        │ ReAct循环  │
│ +RAG    │      │ +模板填充  │        │ 7工具调用   │
│ <2秒    │      │           │        │ 上下文压缩  │
└────────┘      └──────────┘        └──────────┘
  │                    │                    │
  ▼                    ▼                    ▼
┌──────────────────────────────────────────────┐
│  RAG 检索管道（五阶段）                          │
│  混合召回 → RRF融合 → Rerank精排 → MMR去重 → 阈值过滤 │
└──────────────────────────────────────────────┘
  │
  ▼
 LLM 生成 → 引用验证 → SSE 流式返回前端
```

---

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Redis Stack 7.4+
- Maven 3.8+
- Node.js 18+

### 1. 克隆项目

```bash
git clone https://github.com/Lauughter/LawMind.git
cd LawMind
```

### 2. 初始化数据库

```bash
mysql -u root -p < src/main/resources/sql/init_schema.sql
```

> 脚本包含全部 14 张表的建表语句，并自动创建一个管理员账号：
> - 用户名：`admin`
> - 密码：`123456`
> - 登录后可在前端页面修改密码。

### 3. 配置文件

```bash
# 复制并编辑配置文件
cp src/main/resources/application-dev.example.yml src/main/resources/application-dev.yml
```

编辑 `application-dev.yml`，填入你的数据库连接信息、DashScope API Key 和 Redis 连接地址。

### 4. 启动后端

```bash
# Windows
mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Linux / macOS
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

后端默认运行在 `http://localhost:8080`。

### 5. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端默认运行在 `http://localhost:5173`。

---

## 核心特性

### 🔍 多级 RAG 检索

- **混合召回**：Redis 向量 KNN + MySQL Ngram 全文搜索并行执行，RRF 融合（K=60）
- **三级降级链**：BOOLEAN MODE → NATURAL LANGUAGE MODE → LIKE 搜索，任一分支故障不影响整体
- **多级精排**：DashScope qwen3-rerank 重评分 → MMR 多样化去重（λ=0.7）
- **双阈值过滤**：高质量 ≥0.55，边缘结果 0.40–0.55 作为后备
- **查询预处理**：法律实体提取 + 口语→术语扩展（153 条规则）+ LLM 查询改写

### 🤖 ReAct Agent

- **7 个法律工具**：知识检索、法条原文查询、意图分类、术语扩展、相似问题、引用验证、记忆检索
- **四级门控流水线**：领域判断（规则+LLM 兜底）→ 7 类意图分类 → 四因子复杂度评估 → 路由决策
- **快慢分流**：预计 60% 请求走快速通道（<2 秒），复杂咨询走 Agent 通道

### 📦 上下文压缩

四层渐进压缩策略：

| 层级 | 策略 | 成本 |
|------|------|------|
| Layer 0 | 去装饰 / 空白 | 零 |
| Layer 1 | RuleExtractor 正则提取法条/金额 | 零 LLM |
| Layer 2 | SummarizingCompressor LLM 语义压缩至 40% | 低 |
| 全局折叠 | KnowledgeState 结构化摘要替换全部对话 | 中 |

### 🧠 跨会话记忆

- **四类型模型**：USER（用户画像）/ FEEDBACK（用户纠正）/ PROJECT（近期事项）/ REFERENCE（常用法条）
- **两级检索**：索引层（Top 30 注入提示，~200 token）+ 语义层（向量检索详情，~600 token）
- **类型特定衰减**：PROJECT 30d / REFERENCE 60d / FEEDBACK 90d / USER 180d

### 🛡️ 法律安全适配

- **五层安全守卫**：敏感话题过滤 → 法律相关性判断 → 提示注入防御 → PII 脱敏 → 合规声明
- **引用验证闭环**：自动提取回答中的法条引用，逐条与知识库原文核对，标记 UNVERIFIED 警告
- **法律文档专属分块**：LegalArticleChunker 解析编/章/节/条/款层级，条文独立成块并附加出处前缀

---

## 项目结构

```
LawMind/
├── src/main/java/com/lhs/lawmind/
│   ├── agent/              # Agent 核心
│   │   ├── compress/       #   上下文压缩（4层渐进策略）
│   │   ├── gate/           #   意图门控（领域→意图→复杂度→路由）
│   │   ├── memory/         #   跨会话记忆系统
│   │   ├── monitor/        #   Agent 监控指标
│   │   └── tool/           #   7个法律工具
│   ├── config/             # Spring 配置
│   ├── controller/         # REST 控制器
│   ├── service/            # 业务服务层
│   │   └── impl/           #   含 RAG 管道、混合搜索、合同审查等
│   ├── entity/             # 数据库实体
│   └── common/             # 通用工具
├── skills/                 # 法律技能定义（合同审查）
│   └── contract-review/    #   检查清单 + 不公平条款模式
├── frontend/               # Vue 3 前端
│   └── src/
│       ├── components/     #   通用组件（Markdown渲染、侧边栏等）
│       ├── views/          #   页面（咨询、知识库、合同审查等）
│       ├── stores/         #   Pinia 状态管理
│       └── utils/          #   SSE 流式解析、Markdown 渲染
├── docs/                   # 技术文档（架构设计/算法说明/评估体系）
└── scripts/                # RAG 评估脚本
```

---

## 许可证

本项目仅供学习使用。
