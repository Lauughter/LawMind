# security_audit_log（安全审计日志表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：安全审计模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：记录关键操作的审计轨迹（操作人、动作、资源、请求、结果），支撑安全审计与问题追踪。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，审计日志 ID |
| user_id | BIGINT | 是 | NULL | 用户 ID（未登录操作可空） |
| operation_type | VARCHAR(64) | 否 | — | 操作类型 |
| description | VARCHAR(500) | 是 | NULL | 操作描述 |
| resource_type | VARCHAR(64) | 是 | NULL | 资源类型 |
| resource_id | BIGINT | 是 | NULL | 资源 ID |
| request_method | VARCHAR(10) | 是 | NULL | 请求方法（见枚举） |
| request_uri | VARCHAR(500) | 是 | NULL | 请求 URI |
| request_params | TEXT | 是 | NULL | 请求参数（JSON 文本） |
| client_ip | VARCHAR(64) | 是 | NULL | 客户端 IP |
| request_id | VARCHAR(64) | 是 | NULL | 请求追踪 ID |
| result | VARCHAR(20) | 是 | NULL | 操作结果（见枚举） |
| error_message | TEXT | 是 | NULL | 错误信息 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_user_id | 普通 | user_id | 按用户查操作轨迹 |
| idx_operation_type | 普通 | operation_type | 按操作类型审计筛选 |
| idx_create_time | 普通 | create_time DESC | 审计日志时间倒序检索 |
| idx_request_id | 普通 | request_id | 请求追踪 ID 关联排查 |

## 枚举

`request_method`（VARCHAR(10)）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| GET | GET | 查询 | 读请求 |
| POST | POST | 新增 | 写请求 |
| PUT | PUT | 更新 | 修改请求 |
| DELETE | DELETE | 删除 | 删除请求 |

`result`（VARCHAR(20)）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| SUCCESS | SUCCESS | 成功 | 操作无异常 |
| FAIL | FAIL | 失败 | 操作抛错，error_message 记录原因 |

## 业务规则

- **触发范围**：安全敏感操作（登录、改密、配置变更、删除、权限操作）强制写审计；`request_id` 用于跨服务链路追踪。
- **关系**：`user_id` → user.id（可空）；`resource_id` 引用对应业务表主键（多态，无外键）。
- **数据保留**：无软删除、无更新字段，日志只增不改，历史全量保留供合规审计。
- **敏感脱敏**：`request_params` 存请求参数 JSON 文本（TEXT），涉及密码/令牌时应脱敏后写入。
- **用途**：安全事件追溯与操作留痕。
