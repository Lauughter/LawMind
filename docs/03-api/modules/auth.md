# 接口文档 — auth（认证与用户）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：controller/UserController.java | 关联：01-architecture 用户与安全设计、02-db/tables/user.md

---

## 一、接口清单

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/user/login` | 公开 | 登录，返回双 Token |
| POST | `/api/user/register` | 公开 | 注册 |
| POST | `/api/user/refresh-token` | 公开 | 用 refreshToken 换取新双 Token |
| POST | `/api/user/logout` | 公开 | 登出，清除该用户全部 Token |
| GET | `/api/user/list` | 需登录 | 用户列表（分页） |
| GET | `/api/user/get/{id}` | 需登录 | 用户详情 |
| POST | `/api/user/add` | 需登录 | 新增用户 |
| POST | `/api/user/update` | 需登录 | 更新用户 |
| DELETE | `/api/user/delete/{id}` | 需登录 | 删除用户 |
| GET | `/api/user/info` | 需登录 | 当前登录用户信息 |
| POST | `/api/user/update-info` | 需登录 | 更新当前用户昵称/手机号 |
| POST | `/api/user/reset-password/{id}` | 需登录 | 重置指定用户密码（不校验旧密码） |
| POST | `/api/user/change-password` | 需登录 | 修改当前用户密码 |
| GET | `/api/user/chat-history` | 需登录 | 当前用户聊天历史（分页） |
| GET | `/api/user/upload-history` | 需登录 | 当前用户文件上传历史（分页） |
| GET | `/api/user/stats` | 需登录 | 用户统计（咨询/上传数） |

> 鉴权列中"需登录"= JWT 认证通过即可，**未校验管理员身份**（见安全提示）。

---

## 二、登录

### POST /api/user/login

- 鉴权：公开（拦截器排除路径）
- 请求体（`User`，源码未见校验注解，仅取 `username` / `password`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| username | String | 是 | （源码未见校验注解） | 用户名 |
| password | String | 是 | （源码未见校验注解） | 密码（明文传输，校验时与库内加密比对） |

- 成功响应：`R<Map<String,Object>>`

```json
{ "code": 200, "message": "success", "data": { "accessToken": "...", "refreshToken": "...", "expiresIn": 7200 } }
```

- 错误：`code=500, "用户名或密码错误"`（注意：校验失败走 `Result.error(String)`，code 为 500 而非 401）
- 注解：`@Log(value="用户登录", logResult=false)`

**业务规则**：
- 登录成功后生成 accessToken + refreshToken 并存入 Redis；更新 `lastLoginTime`；
- 用户 `role` 为空时按 `"user"` 处理；refreshToken 有效期为 `jwt.refresh-expire`（604800s）。

---

## 三、注册

### POST /api/user/register

- 鉴权：公开（拦截器排除路径）
- 请求体（`User`，源码未见校验注解）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| username | String | 是 | （源码未见校验注解） | 用户名，需唯一 |
| password | String | 是 | （源码未见校验注解） | 密码 |
| nickname | String | 否 | （源码未见校验注解） | 昵称 |
| phone | String | 否 | （源码未见校验注解） | 手机号 |
| role | String | 否 | （源码未见校验注解） | 角色，不传默认 `"user"` |

- 成功响应：`R<?>`（`data=null`）
- 错误：`code=500, "用户名已存在"`

**业务规则**：用户名已存在则注册失败；注册接口可自行传入 `role="admin"` 提升为管理员（源码未做限制，属安全风险项）。

---

## 四、刷新 Token

### POST /api/user/refresh-token

- 鉴权：公开（拦截器排除路径）
- 请求体（`Map<String,String>`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| refreshToken | String | 是 | （源码未见校验注解） | 有效 refreshToken |

- 成功响应：`R<Map<String,Object>>`（同登录：新 accessToken / refreshToken / expiresIn）
- 错误：

| code | 含义 |
|:---:|------|
| 400 | refreshToken 为空 |
| 401 | refreshToken 不存在/已过期/非法类型 |

**业务规则**：先校验 Redis 存在性与签名有效性，再校验 `isRefreshToken` 类型；成功后删除旧 refreshToken 并签发新双 Token（保留原角色）。

---

## 五、登出

### POST /api/user/logout

- 鉴权：公开（拦截器排除路径，但需传 Authorization 头）
- 参数：`Authorization: Bearer <token>`

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| Authorization | Header | 是 | 无 | 需带 `Bearer ` 前缀，方法内取 `substring(7)` |

- 成功响应：`R<?>`（`data=null`）
- 注解：`@Log("用户登出")`

**业务规则**：优先按 userId 清除该用户全部 token（access + refresh）；若 token 已过期无法取 userId，则仅删除当前 token。

---

## 六、用户列表 / 详情 / 新增 / 更新 / 删除

### GET /api/user/list

- 鉴权：需登录（**未做管理员校验**，任何登录用户可查看全部用户列表）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<User>>`

### GET /api/user/get/{id}

- 鉴权：需登录（未做管理员校验）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 用户 ID |

- 成功响应：`R<User>`（**含 password 字段**，未脱敏，属安全风险项）

### POST /api/user/add

- 鉴权：需登录（未做管理员校验）
- 请求体（`User`，字段同上注册表）
- 成功响应：`R<?>`（`data=null`）

### POST /api/user/update

- 鉴权：需登录（未做管理员校验，可任意更新其他用户，含密码字段）
- 请求体（`User` 完整对象，含 `id`）
- 成功响应：`R<?>`

### DELETE /api/user/delete/{id}

- 鉴权：需登录（未做管理员校验，可删除任意用户）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 用户 ID |

- 成功响应：`R<?>`

---

## 七、当前用户信息

### GET /api/user/info

- 鉴权：需登录
- 成功响应：`R<User>`

**业务规则**：从 `RequestContext.getUserId()` 取当前用户（不可伪造），`password` 字段置 `null` 后返回；未登录返回 `code=401, "用户未登录，请先登录"`。

### POST /api/user/update-info

- 鉴权：需登录
- 请求体（`User`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| nickname | String | 否 | （源码未见校验注解） | 新昵称 |
| phone | String | 否 | （源码未见校验注解） | 新手机号 |

- 成功响应：`R<?>`

**业务规则**：只允许更新 `nickname` / `phone`，`username` / `password` 不生效；未登录返回 401，用户不存在返回 `code=500, "用户不存在"`。

---

## 八、重置密码（管理员）

### POST /api/user/reset-password/{id}

- 鉴权：需登录（**未做管理员校验**，任何登录用户可重置任意用户密码，属高危安全风险项）
- 请求体（`Map<String,String>`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| password | String | 是 | （源码未见校验注解） | 新密码，直接覆盖库内密码 |

- 成功响应：`R<?>`；注解：`@Log("重置密码")`

**业务规则**：用于修复因历史版本双重加密无法登录的账号，无需旧密码；用户不存在返回 `code=500, "用户不存在"`。

---

## 九、修改密码

### POST /api/user/change-password

- 鉴权：需登录
- 请求体（`Map<String,String>`）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| oldPassword | String | 是 | （源码未见校验注解） | 原密码 |
| newPassword | String | 是 | （源码未见校验注解） | 新密码 |

- 成功响应：`R<?>`；注解：`@Log("修改密码")`
- 错误：`code=500, "参数错误"` / `"用户不存在"` / `"原密码错误"`

**业务规则**：校验原密码通过后更新，并清除该用户全部 token（需重新登录）。

---

## 十、聊天历史 / 上传历史 / 统计

### GET /api/user/chat-history

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<AiChat>>`（仅当前用户数据）

### GET /api/user/upload-history

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<LawFileUpload>>`（仅当前用户数据）

### GET /api/user/stats

- 鉴权：需登录
- 成功响应：`R<Map<String,Object>>`：`chatCount`（咨询次数）、`fileCount`（上传文件数）

---

> 通用约定（R 响应体 / 错误码 / 鉴权 / 分页）见 [../common.md](../common.md)。
