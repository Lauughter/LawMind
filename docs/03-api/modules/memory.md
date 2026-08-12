# 接口文档 — memory（记忆系统）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：controller/MemoryController.java | 关联：01-architecture/memory-system.md、01-architecture 记忆设计、02-db/tables ai_memory 表

---

## 一、接口清单

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/api/memory/list` | 需登录 | 当前用户记忆列表 |
| DELETE | `/api/api/memory/{id}` | 需登录 | 删除单条记忆 |
| DELETE | `/api/api/memory/clear` | 需登录 | 清空当前用户全部记忆 |

> 实际路径前缀为 `/api/api/memory`（`@RequestMapping("/api/memory")` 叠加 context-path `/api`，见 common.md 第八节）。

---

## 二、记忆列表

### GET /api/api/memory/list

- 鉴权：需登录
- 成功响应：`R<List<AiMemory>>`（仅当前用户记忆）

`AiMemory` 字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 记忆 ID |
| userId | Long | 所属用户 |
| type | MemoryType | 记忆类型（USER/FEEDBACK/PROJECT/REFERENCE） |
| title | String | 标题 |
| body | String | 记忆正文 |
| summary | String | 摘要 |
| originSessionId | Long | 来源会话 |
| sourceSessionIds | String | 多来源会话 ID |
| confidence | Double | 置信度 |
| importance | Double | 重要度 |
| accessCount | Integer | 访问次数 |
| embedding | String | 向量（通常不返回给前端） |
| createdAt / updatedAt / lastAccessedAt | LocalDateTime | 创建 / 更新 / 最近访问时间 |

- 错误：`401, "用户未登录，请先登录"`

---

## 三、删除单条记忆

### DELETE /api/api/memory/{id}

- 鉴权：需登录
- 参数：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 记忆 ID |

- 成功响应：`R<Map<String,Object>>`：`{"deleted":true,"id":N}`
- 错误：`401` / `404, "记忆不存在或无权删除"`（服务端校验记忆属主）

---

## 四、清空记忆

### DELETE /api/api/memory/clear

- 鉴权：需登录
- 成功响应：`R<Map<String,Object>>`：`{"deleted":true,"count":N}`（N 为删除条数）
- 错误：`401, "用户未登录，请先登录"`

---

> 通用约定（R 响应体 / 错误码 / 鉴权）见 [../common.md](../common.md)。
