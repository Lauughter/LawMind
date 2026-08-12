# 接口文档 — chat（对话 / AI 问答 / Agent）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：controller/AiChatController.java、ConversationController.java、AgentController.java | 关联：01-architecture 对话与 Agent 设计、02-db/tables ai_chat / conversation 表

---

## 一、接口清单

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/ai-chat/list` | 管理员 | 全部对话记录（分页） |
| GET | `/api/ai-chat/list-by-user` | 需登录 | 当前用户对话（分页） |
| GET | `/api/ai-chat/get/{id}` | 需登录+属主 | 对话详情 |
| POST | `/api/ai-chat/add` | 需登录 | 新增对话记录 |
| POST | `/api/ai-chat/update` | 需登录+属主 | 更新对话记录 |
| DELETE | `/api/ai-chat/delete/{id}` | 需登录+属主 | 删除对话记录 |
| POST | `/api/ai-chat/ask` | 需登录 | AI 问答（同步） |
| POST | `/api/ai-chat/ask-stream` | 需登录 | AI 问答（SSE 流式） |
| POST | `/api/ai-chat/feedback` | 需登录+属主 | 消息反馈（赞/踩） |
| GET | `/api/ai-chat/admin/review-queue` | 管理员 | 待审核反馈队列（分页） |
| POST | `/api/ai-chat/admin/review/{id}` | 管理员 | 提交审核结果 |
| POST | `/api/conversation/create` | 需登录 | 创建会话 |
| GET | `/api/conversation/list` | 需登录 | 会话列表（分页） |
| GET | `/api/conversation/{id}/messages` | 需登录 | 会话内消息（分页） |
| PUT | `/api/conversation/{id}/rename` | 需登录 | 重命名会话 |
| DELETE | `/api/conversation/{id}` | 需登录 | 删除会话（软删） |
| POST | `/api/agent/ask` | 需登录 | Agent 推理（SSE 流式） |
| GET | `/api/agent/metrics` | 管理员 | Agent 运行指标 |
| GET | `/api/agent/health` | 需登录 | Agent 健康检查 |

---

# 二、AiChatController（对话记录）

## 2.1 对话记录列表（管理员）

### GET /api/ai-chat/list

- 鉴权：管理员（`RequestContext.getUserId()` == `lawmind.admin-user-id`）
- 参数：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<AiChatVO>>`
- 错误：`403, "无权访问"`

## 2.2 当前用户对话记录（分页）

### GET /api/ai-chat/list-by-user

- 鉴权：需登录
- 参数：`page`（默认 1）、`pageSize`（默认 10）
- 成功响应：`R<PageResult<AiChatVO>>`（仅当前用户）
- 错误：`401, "用户未登录"`

## 2.3 对话详情

### GET /api/ai-chat/get/{id}

- 鉴权：需登录
- 参数：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 对话记录 ID |

- 成功响应：`R<AiChatVO>`
- 错误：`404, "记录不存在"` / `403, "无权访问该记录"`

## 2.4 新增对话记录

### POST /api/ai-chat/add

- 鉴权：需登录
- 请求体（`AiChat`，源码未见校验注解，`userId` 由后端覆盖为当前用户）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| conversationId | Long | 否 | （源码未见校验注解） | 所属会话 |
| userQuestion | String | 否 | （源码未见校验注解） | 用户问题 |
| aiAnswer | String | 否 | （源码未见校验注解） | AI 回答 |
| knowledgeMatch | String | 否 | （源码未见校验注解） | 命中知识（JSON 字符串） |

- 成功响应：`R<?>`

## 2.5 更新 / 删除对话记录

### POST /api/ai-chat/update

- 鉴权：需登录 + 属主（`aiChat.userId == 当前用户`）
- 请求体：`AiChat`（需含 `id`）
- 错误：`404, "记录不存在"` / `403, "无权修改该记录"`

### DELETE /api/ai-chat/delete/{id}

- 鉴权：需登录 + 属主
- 参数：`id`（Path）
- 错误：`404, "记录不存在"` / `403, "无权删除该记录"`
- 成功响应：`R<?>`

## 2.6 AI 问答（同步）

### POST /api/ai-chat/ask

- 鉴权：需登录；注解：`@Log("AI问答请求")`
- 请求体（`Map<String,Object>`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| question | String | 是 | （源码未见校验注解） | 用户问题 |
| conversationId | Long | 否 | （源码未见校验注解） | 会话 ID，为空自动创建 |

- 成功响应：`R<AIChatResponse>`（`answer`、`relatedKnowledge`、`chatId`、`conversationId`）
- 错误：

| code | 含义 |
|:---:|------|
| 401 | 用户未登录 |
| 500 | 请求被中断 / 执行失败 / 超时（超时上限 90s） |

**业务规则**：问题长度 >20 字符时自动截取前 20 字符 + "..." 作为新会话标题；异步线程池执行，`Future.get(90s)` 超时兜底。

## 2.7 AI 问答（SSE 流式）

### POST /api/ai-chat/ask-stream

- 鉴权：需登录
- 返回类型：`text/event-stream`（`SseEmitter`，超时 120s）
- 请求体（`StreamChatRequest`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| question | String | 是 | （源码未见校验注解） | 用户问题，不能为空白 |
| conversationId | Long | 否 | （源码未见校验注解） | 会话 ID，为空自动创建 |
| userId | Long | 否 | （源码未见校验注解） | **忽略**，实际用户取 `RequestContext` |

- SSE 事件：

| 事件 | 数据 | 说明 |
|------|------|------|
| `token` | `{"content":"..."}` | 增量输出片段（逐字） |
| `knowledge` | `{"relatedKnowledge":[...]}` | 命中知识检索结果 |
| `done` | `{"conversationId":..,"chatId":..}` | 正常结束 |
| `error` | `{"message":"..."}` | 未登录 / 参数不完整 / 系统错误 |
| `message` | 拒绝文案 | 敏感话题被安全守卫拦截时 |

**业务规则**：question 为空、未登录等失败场景也会先建连接再推 `error` 事件；后端先走敏感话题过滤与法律相关性判断，非法律问题直接拒绝。

## 2.8 消息反馈

### POST /api/ai-chat/feedback

- 鉴权：需登录 + 属主
- 请求体（`Map<String,Object>`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| chatId | Long | 是 | （源码未见校验注解） | 对话记录 ID |
| feedback | Integer | 否 | 仅允许 `1`(赞)/`-1`(踩) | 反馈值 |
| feedbackContent | String | 否 | （源码未见校验注解） | 反馈文字说明 |

- 成功响应：`R<?>`
- 错误：`401` / `404, "聊天记录不存在"` / `403, "无权操作该记录"` / `400, "反馈值非法"`

**业务规则**：`feedback=-1`（点踩）时自动将 `feedbackStatus` 置为 `PENDING_REVIEW` 进入审核队列。

## 2.9 反馈审核（管理员）

### GET /api/ai-chat/admin/review-queue

- 鉴权：管理员（adminUserId）
- 参数：`page`（默认 1）、`pageSize`（默认 10）
- 成功响应：`R<PageResult<AiChatVO>>`（仅 `PENDING_REVIEW` 记录）
- 错误：`403, "无权访问，仅限管理员"`

### POST /api/ai-chat/admin/review/{id}

- 鉴权：管理员（adminUserId）
- 请求体（`Map<String,Object>`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| feedbackStatus | String | 是 | 仅允许 `REVIEWED` / `DISMISSED` | 审核结果 |
| reviewNotes | String | 否 | （源码未见校验注解） | 审核备注 |

- 成功响应：`R<?>`；错误：`400, "审核状态非法"` / `404, "聊天记录不存在"`

---

# 三、ConversationController（会话管理）

## 3.1 创建会话

### POST /api/conversation/create

- 鉴权：需登录
- 请求体（`Map<String,String>`，可选）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| title | String | 否 | （源码未见校验注解） | 会话标题，缺省用 `"新对话"` |

- 成功响应：`R<Conversation>`
- 错误：`401, "用户未登录，请先登录"`

## 3.2 会话列表

### GET /api/conversation/list

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<Conversation>>`（仅当前用户）

## 3.3 会话消息

### GET /api/conversation/{id}/messages

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 会话 ID |
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 50 | 每页条数 |

- 成功响应：`R<PageResult<AiChat>>`

## 3.4 重命名会话

### PUT /api/conversation/{id}/rename

- 鉴权：需登录（**未校验会话属主**，可重命名他人会话，IDOR 风险）
- 请求体（`Map<String,String>`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| title | String | 是 | （源码未见校验注解） | 新标题，不能为空 |

- 成功响应：`R<?>`；错误：`code=500, "标题不能为空"`

## 3.5 删除会话

### DELETE /api/conversation/{id}

- 鉴权：需登录（**未校验会话属主**，可删除他人会话，IDOR 风险）
- 参数：`id`（Path）
- 成功响应：`R<?>`

**业务规则**：软删除（`isDeleted=1`），服务端无返回错误。

---

# 四、AgentController（Agent 推理）

## 4.1 Agent 推理（SSE 流式）

### POST /api/agent/ask

- 鉴权：需登录
- 返回类型：`text/event-stream`（`SseEmitter`，超时 120s）
- 请求体（`AgentAskRequest`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| question | String | 是 | （源码未见校验注解） | 用户问题，不能为空白 |
| conversationId | Long | 否 | （源码未见校验注解） | 会话 ID，缺省/不属于当前用户时自动新建 |
| mode | String | 否 | 取 `"agent"` 强制 Agent 模式，否则自动门控路由 | 模式开关 |

- SSE 事件：

| 事件 | 数据 | 说明 |
|------|------|------|
| `message` | 单字符 | 逐字流式输出（含拒绝文案） |
| `done` | `{"status":"completed","channel":"fast\|agent","conversationId":..,"chatId":..}` | 成功结束 |
| `done` | `{"status":"rejected"}` | 被意图门控拒绝 |
| `error` | `{"message":"..."}` | 未登录 / 问题为空 / 处理失败 |

**业务规则**：
- 通过意图门控（IntentGate）做领域判断 → 意图分类 → 路由决策，分派到 **Fast / Hybrid / Agent / Reject** 四通道；
- `mode="agent"` 时跳过门控，强制走 Agent 通道（多步推理 + 工具调用）；
- Hybrid 通道当前版本复用 Fast 通道实现；
- 每次推理完成后持久化聊天记录并 `touch` 会话（保存失败返回 `chatId=0`，不阻断流）。

## 4.2 Agent 指标（管理员）

### GET /api/agent/metrics

- 鉴权：管理员（adminUserId）
- 成功响应：`R<Map<String,Object>>`：`totalAgentCalls`、`totalToolCalls`、`totalFallbackCalls`、`totalCompressions`、`estimatedTokensSaved`、`knowledgeStateAtomCounts`、`toolCallCounts`、`gateStats{channelCounts,totalRejects}`、`startTime`
- 错误：`403, "无权访问，仅限管理员"`

## 4.3 健康检查

### GET /api/agent/health

- 鉴权：需登录（**未被拦截器排除**，健康检查也需 token）
- 成功响应：`R<String>`：`"Agent service is running"`

---

> 通用约定（R 响应体 / 错误码 / 鉴权 / SSE / 分页）见 [../common.md](../common.md)。
