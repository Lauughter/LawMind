# 接口文档 — knowledge（知识库 / 文件上传 / 向量化 / Redis 索引）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：controller/LawKnowledgeController.java、LawFileUploadController.java、LawVectorTaskController.java、RedisIndexManagementController.java | 关联：01-architecture 知识库与检索设计、02-db/tables law_knowledge / law_file_upload 表

---

## 一、接口清单

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| GET | `/api/law-knowledge/list` | 需登录 | 知识列表（分页+搜索+类型过滤） |
| GET | `/api/law-knowledge/list-by-law-type/{lawType}` | 需登录 | 按法律类型分页列表 |
| GET | `/api/law-knowledge/search` | 需登录 | 关键词搜索（分页） |
| GET | `/api/law-knowledge/get/{id}` | 需登录 | 知识详情 |
| POST | `/api/law-knowledge/add` | 需登录 | 新增知识 |
| POST | `/api/law-knowledge/update` | 需登录 | 更新知识 |
| DELETE | `/api/law-knowledge/delete/{id}` | 需登录 | 删除知识 |
| POST | `/api/law-knowledge/vectorize` | 需登录 | 触发向量化 |
| POST | `/api/law-knowledge/api/vectorize/all` | 需登录 | 全量向量化（返回 String，非 Result） |
| POST | `/api/law-knowledge/api/vectorize/batch` | 需登录 | 批量向量化（返回 String） |
| POST | `/api/law-knowledge/api/vectorize/batch/{offset}/{limit}` | 需登录 | 批量向量化带偏移（返回 String） |
| GET | `/api/law-file-upload/list` | 需登录 | 上传文件列表（分页） |
| GET | `/api/law-file-upload/list-by-user/{userId}` | 需登录 | 按用户查文件列表 |
| GET | `/api/law-file-upload/get/{id}` | 需登录 | 文件详情（含全文 content） |
| POST | `/api/law-file-upload/add` | 需登录 | 新增上传记录 |
| POST | `/api/law-file-upload/update` | 需登录 | 更新上传记录 |
| DELETE | `/api/law-file-upload/delete/{id}` | 需登录 | 删除上传记录 |
| DELETE | `/api/law-file-upload/{id}` | 需登录 | 删除上传记录（个人中心） |
| POST | `/api/law-file-upload/upload` | 需登录 | 文件上传（multipart） |
| POST | `/api/api/vectorize/single/{id}` | 需登录 | 单条向量化（异步） |
| POST | `/api/api/vectorize/batch` | 需登录 | 批量向量化（异步） |
| POST | `/api/api/vectorize/all` | 需登录 | 全量向量化（异步，分批） |
| GET | `/api/api/vectorize/progress` | 需登录 | 向量化进度（预留） |
| GET | `/api/redis/index/stats` | 需登录 | 索引缓存统计 |
| DELETE | `/api/redis/index/cache` | 需登录 | 清空索引缓存 |
| POST | `/api/redis/index/refresh/{indexName}` | 需登录 | 刷新索引状态 |
| GET | `/api/redis/index/status` | 需登录 | 所有索引状态 |

> 注：`/api/law-knowledge/api/vectorize/*` 与 `/api/api/vectorize/*` 的双 `/api` 前缀为实际映射结果（见 common.md 第八节）。

---

# 二、LawKnowledgeController（知识库）

## 2.1 知识列表（分页 + 搜索 + 类型过滤）

### GET /api/law-knowledge/list

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |
| keyword | Query | 否 | 无 | 搜索关键词（标题/内容） |
| type | Query | 否 | 无 | 法律类型过滤（如"民法"/"刑法"） |

- 成功响应：`R<PageResult<LawKnowledge>>`


**业务规则**：keyword 与 type 组合查询（keyword+type → 同时过滤；仅 keyword → 搜索；仅 type → 按类型；均无 → 全量分页）。

## 2.2 按法律类型列表

### GET /api/law-knowledge/list-by-law-type/{lawType}

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| lawType | Path | 是 | 无 | 法律类型 |
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<LawKnowledge>>`

## 2.3 关键词搜索

### GET /api/law-knowledge/search

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| keyword | Query | 是 | 无 | 搜索关键词 |
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<LawKnowledge>>`

## 2.4 知识详情

### GET /api/law-knowledge/get/{id}

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 知识 ID |

- 成功响应：`R<LawKnowledge>`

## 2.5 新增 / 更新 / 删除知识

### POST /api/law-knowledge/add

- 鉴权：需登录（**未校验管理员**，`userId` 后端覆盖为当前用户）
- 请求体（`LawKnowledge`，源码未见校验注解）：

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|:---:|------|------|
| lawType | String | 否 | （源码未见校验注解） | 法律类型 |
| title | String | 否 | （源码未见校验注解） | 标题 |
| chapter / section | String | 否 | （源码未见校验注解） | 章 / 节 |
| articleNumber | Integer | 否 | （源码未见校验注解） | 条文编号 |
| content | String | 否 | （源码未见校验注解） | 条文内容 |
| effectiveDate / expiryDate | Date | 否 | （源码未见校验注解） | 生效 / 失效日期 |
| status | String | 否 | （源码未见校验注解） | 法律状态（EFFECTIVE 等） |
| source | String | 否 | （源码未见校验注解） | 来源（BATCH_IMPORT/MANUAL/AUTO_LEARN） |
| publisher / publishDate | String / Date | 否 | （源码未见校验注解） | 发布方 / 发布日期 |

- 成功响应：`R<?>`

### POST /api/law-knowledge/update

- 鉴权：需登录（**未校验属主/管理员**，可更新任意知识）
- 请求体：`LawKnowledge`（需含 `id`）
- 成功响应：`R<?>`

### DELETE /api/law-knowledge/delete/{id}

- 鉴权：需登录（**未校验属主/管理员**，可删除任意知识）
- 参数：`id`（Path）
- 成功响应：`R<?>`

## 2.6 触发向量化

### POST /api/law-knowledge/vectorize

- 鉴权：需登录（**任何登录用户可触发全库向量化**，属 DoS 风险）
- 成功响应：`R<?>`（`data=null`，同步执行后返回）

## 2.7 向量化变体（返回 String，非 Result 包装）

### POST /api/law-knowledge/api/vectorize/all

- 鉴权：需登录
- 成功响应：纯文本 `String` `"向量化任务已启动，请查看日志"`

### POST /api/law-knowledge/api/vectorize/batch

- 鉴权：需登录
- 成功响应：纯文本 `String` `"批量向量化任务已完成，成功处理 N 条数据"`

### POST /api/law-knowledge/api/vectorize/batch/{offset}/{limit}

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| offset | Path | 是 | 无 | 起始偏移量 |
| limit | Path | 是 | 无 | 处理记录数上限 |

- 成功响应：纯文本 `String`

---

# 三、LawFileUploadController（文件上传）

## 3.1 文件列表

### GET /api/law-file-upload/list

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<LawFileUpload>>`（返回完整实体，含 `content` 大字段）

## 3.2 按用户文件列表

### GET /api/law-file-upload/list-by-user/{userId}

- 鉴权：需登录（**userId 来自 Path，可查看任意用户文件**，越权风险）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| userId | Path | 是 | 无 | 目标用户 ID |
| page | Query | 否 | 1 | 页码 |
| pageSize | Query | 否 | 10 | 每页条数 |

- 成功响应：`R<PageResult<LawFileUpload>>`

## 3.3 文件详情

### GET /api/law-file-upload/get/{id}

- 鉴权：需登录（**未校验属主**，可读取任意文件含全文 content，数据泄露风险）
- 参数：`id`（Path）
- 成功响应：`R<LawFileUpload>`（含 `content` 全文）

## 3.4 新增 / 更新 / 删除记录

### POST /api/law-file-upload/add

- 鉴权：需登录
- 请求体（`LawFileUpload`，源码未见校验注解）：`userId`、`fileName`、`fileType`、`fileSize`、`processingStatus` 等
- 成功响应：`R<?>`

### POST /api/law-file-upload/update

- 鉴权：需登录（未校验属主）
- 请求体：`LawFileUpload`（需含 `id`）
- 成功响应：`R<?>`

### DELETE /api/law-file-upload/delete/{id}

- 鉴权：需登录（未校验属主）
- 参数：`id`（Path）
- 成功响应：`R<?>`

### DELETE /api/law-file-upload/{id}

- 鉴权：需登录（未校验属主）
- 参数：`id`（Path）
- 成功响应：`R<?>`

## 3.5 文件上传

### POST /api/law-file-upload/upload

- 鉴权：需登录
- 参数（`multipart/form-data`）：

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| file | MultipartFile | 是 | 无 | 上传文件（PDF/Word/TXT） |
| userId | Form | 是 | 无 | **客户端传入，可伪造为他人 userId**（越权风险） |

- 成功响应：`R<LawFileUpload>`（含 `id`、`processingStatus`，状态 PROCESSING→COMPLETED/FAILED）
- 错误：`code=500, "文件处理失败: ..."` / `"上传失败: ..."`

**业务规则**：提取文本 → 入库（PROCESSING）→ 触发文档接入流水线（解析/分块/建知识）→ 更新 COMPLETED 或 FAILED。

---

# 四、LawVectorTaskController（向量化任务，异步）

> 实际路径前缀为 `/api/api/vectorize`；响应为 `ResponseEntity`（非 `Result` 包装）。

## 4.1 单条向量化

### POST /api/api/vectorize/single/{id}

- 鉴权：需登录（未校验管理员）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| id | Path | 是 | 无 | 知识库 ID |

- 成功响应：`ResponseEntity<String>` `"向量化任务已启动，请查看日志"`

## 4.2 批量向量化

### POST /api/api/vectorize/batch

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| offset | Query | 是 | 无 | 偏移量 |
| limit | Query | 是 | 无 | 处理数量 |

- 成功响应：`ResponseEntity<String>`

## 4.3 全量向量化

### POST /api/api/vectorize/all

- 鉴权：需登录（任何登录用户可触发全量向量化，DoS 风险）

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| batchSize | Query | 否 | 100 | 每批处理数量 |

- 成功响应：`ResponseEntity<Map>`：`{"message":"全量向量化任务已启动，请查看日志","batchSize":100}`

**业务规则**：启动独立线程分批处理，每批间隔休眠 1s 防过载。

## 4.4 进度查询（预留）

### GET /api/api/vectorize/progress

- 鉴权：需登录
- 成功响应：`ResponseEntity<Map>`：`{"status":"running","message":"..."}`（TODO，未实现真实进度）

---

# 五、RedisIndexManagementController（Redis 索引管理）

## 5.1 索引缓存统计

### GET /api/redis/index/stats

- 鉴权：需登录
- 成功响应：`R<Map<String,Object>>`：`cacheSize`、`hits`、`misses`、`hitRate`、`totalRequests`

## 5.2 清空索引缓存

### DELETE /api/redis/index/cache

- 鉴权：需登录（**未校验管理员**，任意用户可清空缓存）
- 成功响应：`R<String>` `"缓存已清空"`

## 5.3 刷新索引状态

### POST /api/redis/index/refresh/{indexName}

- 鉴权：需登录

| 参数 | 类型 | 必填 | 默认 | 说明 |
|------|------|:---:|------|------|
| indexName | Path | 是 | 无 | 索引名 |

- 成功响应：`R<String>` `"索引状态已刷新: {indexName}"`（当前为占位实现，未实际刷新）

## 5.4 所有索引状态

### GET /api/redis/index/status

- 鉴权：需登录
- 成功响应：`R<Map<String,Boolean>>`（当前返回空 Map，占位实现）

---

> 通用约定（R 响应体 / 错误码 / 鉴权 / 分页）见 [../common.md](../common.md)。
