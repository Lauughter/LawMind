# 通用约定（common）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：controller/common/Result.java、PageResult.java、BusinessException.java、GlobalExceptionHandler.java、interceptor/JwtInterceptor.java | 关联：01-architecture 通用架构设计、04-environments 运行环境

---

## 一、响应体结构 `Result<T>`

所有接口（除 SSE 流式接口与个别返回 `String`/`ResponseEntity` 的向量化接口外）统一返回 `Result<T>` 包装。

| 字段 | 类型 | 说明 |
|------|------|------|
| code | int | 状态码，`200`=成功，其余为错误码（见第三节） |
| message | String | 提示信息，成功时 `"success"`，失败时为业务提示 |
| data | T | 数据载荷，失败时为 `null` |

```json
{ "code": 200, "message": "success", "data": { } }
```

**注意**：`Result.error(String)`（无 code 参数）会将 code 置为 **500**，部分接口用此方式表达"用户名或密码错误"等校验失败，与语义应有的 4xx 不一致，调用方需按 `code` 而非 HTTP 状态判断。

---

## 二、分页响应 `PageResult<T>`

分页接口的 `data` 为 `PageResult<T>`：

| 字段 | 类型 | 说明 |
|------|------|------|
| total | long | 总记录数 |
| list | List\<T\> | 当前页数据 |
| page | int | 当前页码（**从 1 开始**） |
| pageSize | int | 每页条数 |

**分页参数约定**：`page`（默认 1，1-based）、`pageSize`（默认 10，部分接口默认 20/50）。未做上限校验（`pageSize` 传超大值可能导致大查询）。

---

## 三、全局错误处理（GlobalExceptionHandler）

统一 `@RestControllerAdvice`，所有异常转为 `Result<T>` 返回，**不抛 HTTP 错误状态码**（HTTP 恒为 200，除非 SseEmitter 自行设置状态码）。

| code | 触发场景 | 消息 |
|:---:|------|------|
| 400 | `BusinessException.badRequest` / `IllegalArgumentException` / 缺参 / 请求体解析失败 | "请求参数不合法" / "缺少必要参数: X" / "请求体格式错误" |
| 401 | `BusinessException.unauthorized` | 业务自定义 |
| 403 | `BusinessException.forbidden` | 业务自定义 |
| 404 | `BusinessException.notFound` / 资源不存在 | "{资源}不存在: {id}" |
| 405 | 请求方法不支持 | "不支持的请求方法: {method}" |
| 429 | `BusinessException.tooManyRequests` | "请求过于频繁，请稍后再试" |
| 500 | `BusinessException.serviceError` / 运行时异常 / 未处理异常 | "系统内部错误，请稍后再试"（不泄露内部细节） |

> `BusinessException` 为业务异常基类，工厂方法：`notFound / unauthorized / forbidden / badRequest / tooManyRequests / serviceError / asyncExecutionFailed`。

---

## 四、JWT 鉴权

### 4.1 请求头

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| Authorization | Header | 是 | 无 | 固定格式 `Bearer <accessToken>`，缺省/格式错误直接返回 401 |

### 4.2 校验流程（JwtInterceptor）

1. 校验请求头是否以 `Bearer ` 开头，否则 401"未授权，请先登录"；
2. 校验 token 是否存在于 Redis（支持主动撤销），否则 401"token已失效，请重新登录"；
3. 校验签名与过期时间，失效则删除 Redis 中 token 并 401"token无效或已过期"；
4. 解析 userId/role 写入 `RequestContext`（`getUserId()` / `getRole()` / `isAdmin()`）。

### 4.3 公开接口（拦截器排除列表）

仅以下路径无需 JWT（均位于 context-path 之后）：

| 路径 | 说明 |
|------|------|
| `/user/login` | 登录 |
| `/user/register` | 注册 |
| `/user/refresh-token` | 刷新 token |
| `/user/logout` | 登出 |
| `/actuator/**` | 健康检查 |

> 其余全部接口默认需 JWT。健康检查类接口（如 `/agent/health`）**并未被排除**，同样需要 token。

### 4.4 Token 机制

- 双 Token：`accessToken`（`jwt.expire=7200s`）+ `refreshToken`（`jwt.refresh-expire=604800s`）；
- 登录成功返回 `{accessToken, refreshToken, expiresIn}`；
- token 存 Redis，`/user/logout`、`/user/change-password` 会清除该用户全部 token；
- `JwtUtil` 的密钥 `jwt.secret` 在配置文件中明文（`lawmind_secret_key`），属安全风险项。

---

## 五、管理员校验

系统存在**两种**管理员判定机制，实现方式不同，均应在接口文档中区分：

| 机制 | 判定依据 | 配置 | 使用位置 |
|------|----------|------|----------|
| 配置式 | `RequestContext.getUserId()` 等于 `lawmind.admin-user-id` | `lawmind.admin-user-id`（application.yml 为 `4`，代码默认 `1`） | AiChatController、AgentController、SysConfigController |
| 角色式 | `RequestContext.getRole()` 等于 `"admin"` | 登录用户 role 字段 | RagMetricsController（`RequestContext.isAdmin()`） |

> 两种机制口径不同：配置式只认 adminUserId，与用户 role 无关；角色式只认 role=="admin"。注意两者可能产生不一致的授权结果。

---

## 六、AOP 注解

| 注解 | 作用 | 参数 | 说明 |
|------|------|------|------|
| `@SecurityAudit(operationType, description, resourceType, logParams)` | 安全审计标记 | 操作类型 / 描述 / 资源类型 / 是否记录参数 | 由 `SecurityAuditAspect` 处理，当前 Controller 层暂无使用实例 |
| `@Log(value, logParams, logResult)` | 操作日志 | 描述 / 是否记录参数（默认 true）/ 是否记录返回值（默认 false） | 由 `ControllerLogAspect` 处理，已用于登录、登出、AI 问答等 |

---

## 七、SSE 流式接口约定

- **Content-Type**：`text/event-stream`；请求方式为 `POST`（SSE 由"一次性发起、持续推送"实现）。
- **不走 `Result` 包装**，所有数据通过事件推送。
- 通用事件名：

| 事件名 | 数据格式 | 出现位置 |
|--------|----------|----------|
| `message` | 单字符（逐字流式输出）或拒绝文案 | `/agent/ask`、敏感话题拦截时的 `/ai-chat/ask-stream` |
| `token` | `{"content":"..."}` 增量片段 | `/ai-chat/ask-stream` |
| `knowledge` | `{"relatedKnowledge":[...]}` | `/ai-chat/ask-stream`（检索结果命中时） |
| `done` | `{"status":"completed","channel":"...","conversationId":..,"chatId":..}` 或 `{"status":"rejected"}` 或 `{"conversationId":..,"chatId":..}` | 各流式接口收尾 |
| `error` | `{"message":"..."}` | 各流式接口失败/未登录 |

- **超时设置**：`/ai-chat/ask-stream`、`/agent/ask` 为 120s（`new SseEmitter(120_000L)`）。
- 客户端收到 `done` 或 `error` 后应停止监听并关闭连接。

---

## 八、路径前缀约定

- 全局 `server.servlet.context-path=/api`，所有 Controller 映射的实际完整路径均以 `/api` 开头。
- **双 `/api` 前缀**：部分 Controller 的 `@RequestMapping` 自身已带 `/api` 前缀，叠加 context-path 后实际路径为双 `/api`，例如 `LawVectorTaskController(@RequestMapping("/api/vectorize"))` → 实际 `POST /api/api/vectorize/single/{id}`；`MemoryController` → `/api/api/memory/list`；`RedisInfoController` → `/api/api/redis/info`；`LawKnowledgeController` 的 `@PostMapping("/api/vectorize/all")` → 实际 `/api/law-knowledge/api/vectorize/all`。以下模块文档中路径均按**实际完整路径**书写并保留此特征。

---

## 九、文件上传约定

- `POST .../upload` 使用 `multipart/form-data`，表单字段 `file`（`MultipartFile`）。
- 文件文本提取由 `FileUtil.extractText` 完成，支持 PDF/Word/TXT；提取后存库并触发文档接入流水线（解析 → 分块 → 创建知识 → 异步向量化）。
- 上传响应会清空 `content` / `aiReviewResult` / `aiRevisedContent` 大字段（`/file/upload`），但 `/law-file-upload/get/{id}` 返回完整实体（含 `content`）。
