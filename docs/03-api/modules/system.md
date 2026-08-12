# 接口文档 — system（系统配置 / 通用文件 / 自动学习）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：controller/SysConfigController.java、FileController.java、AutoLearningController.java | 关联：01-architecture 系统管理设计、02-db/tables sys_config / law_file_upload 表

---

## 一、接口清单

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/sys-config/list` | 管理员 | 配置列表（分页） |
| GET | `/api/sys-config/get-by-key/{configKey}` | 管理员 | 按 key 查配置 |
| GET | `/api/sys-config/get/{id}` | 管理员 | 按 ID 查配置 |
| POST | `/api/sys-config/add` | 管理员 | 新增配置 |
| POST | `/api/sys-config/update` | 管理员 | 更新配置 |
| DELETE | `/api/sys-config/delete/{id}` | 管理员 | 删除配置 |
| POST | `/api/file/upload` | 需登录 | 通用文件上传（multipart） |
| GET | `/api/file/list` | 需登录 | 当前用户文件列表 |
| GET | `/api/file/content/{id}` | 需登录 | 文件全文内容 |
| GET | `/api/file/info/{id}` | 需登录 | 文件元信息 |
| DELETE | `/api/file/{id}` | 需登录 | 删除文件 |
| DELETE | `/api/file/delete/{id}` | 需登录 | 删除文件 |
| POST | `/api/auto-learning/trigger` | 需登录 | 手动触发自动学习入库 |

---

# 二、SysConfigController（系统配置）

> 所有接口通过 `lawmind.admin-user-id` 判定管理员（`RequestContext.getUserId() == adminUserId`），非管理员统一返回 `403, "无权访问，仅限管理员"` / `"无权操作，仅限管理员"`。

## 2.1 配置列表

### GET /api/sys-config/list

- 鉴权：管理员

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 20 | 每页条数 |

- 成功响应：`R<PageResult<SysConfig>>`

## 2.2 按 key 查询

### GET /api/sys-config/get-by-key/{configKey}

- 鉴权：管理员
- 参数：`configKey`（Path）
- 成功响应：`R<SysConfig>`

## 2.3 按 ID 查询

### GET /api/sys-config/get/{id}

- 鉴权：管理员
- 参数：`id`（Path，Integer）
- 成功响应：`R<SysConfig>`

## 2.4 新增 / 更新 / 删除配置

### POST /api/sys-config/add

- 鉴权：管理员
- 请求体（`SysConfig`，源码未见校验注解）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| configKey | String | 否 | （源码未见校验注解） | 配置键 |
| configValue | String | 否 | （源码未见校验注解） | 配置值 |
| description | String | 否 | （源码未见校验注解） | 描述 |

- 成功响应：`R<?>`

### POST /api/sys-config/update

- 鉴权：管理员
- 请求体：`SysConfig`（需含 `id`）
- 成功响应：`R<?>`

### DELETE /api/sys-config/delete/{id}

- 鉴权：管理员
- 参数：`id`（Path，Integer）
- 成功响应：`R<?>`

**业务规则**：新增/更新/删除均输出 `log.warn("系统配置被修改: key=...")` 告警日志。

---

# 三、FileController（通用文件）

## 3.1 文件上传

### POST /api/file/upload

- 鉴权：需登录（`userId` 由 `RequestContext` 取得，不可伪造）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| file | MultipartFile | 是 | 无 | 上传文件（PDF/Word/TXT） |

- 成功响应：`R<LawFileUpload>`（已清空 `content`/`aiReviewResult`/`aiRevisedContent` 大字段，含 `processingStatus`）
- 错误：`401, "用户未登录，请先登录"` / `code=500, "文件处理失败: ..."`

**业务规则**：提取文本 → 入库（PROCESSING）→ 文档接入（COMPLETED/FAILED）→ 成功时自动触发 `asyncVectorizeService.batchVectorizeAsync(0, 200)` 异步向量化。

## 3.2 文件列表

### GET /api/file/list

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<LawFileUpload>>`（仅当前用户，已清空大字段）

## 3.3 文件内容 / 元信息

### GET /api/file/content/{id}

- 鉴权：需登录（**未校验文件属主**，可读取任意文件全文，越权/泄露风险）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 文件 ID |

- 成功响应：`R<String>`（`data` 为文件全文 `content`）
- 错误：`code=500, "文件不存在"`

### GET /api/file/info/{id}

- 鉴权：需登录（未校验属主）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 文件 ID |

- 成功响应：`R<LawFileUpload>`（不含大字段）

## 3.4 删除文件

### DELETE /api/file/{id} 与 DELETE /api/file/delete/{id}

- 鉴权：需登录（未校验属主）
- 参数：`id`（Path）
- 成功响应：`R<?>`（两个路径行为一致，均为直接删除）

---

# 四、AutoLearningController（自动学习）

## 4.1 触发自动学习

### POST /api/auto-learning/trigger

- 鉴权：需登录（**未校验管理员**，任意登录用户可触发）
- 成功响应：`R<String>`：`"自动学习入库已触发，请查看日志"`
- 错误：`code=500, "自动学习服务未初始化"`（`AutoLearningScheduler` Bean 未装配时）

**业务规则**：开发阶段手动触发 `AutoLearningScheduler.autoLearning()` 入库；生产默认 `rag.auto-learning.enabled=false`，由定时任务（cron `0 0 2 * * ?`）驱动。

---

> 通用约定（R 响应体 / 错误码 / 鉴权 / 分页）见 [../common.md](../common.md)。
