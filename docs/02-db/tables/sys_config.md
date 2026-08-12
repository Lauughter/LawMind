# sys_config（系统配置表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：系统配置模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：存储键值型系统配置（K/V），供运行期动态读取，支持带说明的配置项管理。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，配置记录 ID |
| config_key | VARCHAR(200) | 否 | — | 配置键（唯一） |
| config_value | TEXT | 是 | NULL | 配置值 |
| description | VARCHAR(500) | 是 | NULL | 配置说明 |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_config_key | 唯一 | config_key | 配置键唯一约束，按 key 读取 |

## 枚举

无枚举字段。

## 业务规则

- **K/V 模型**：`config_key` 唯一，`config_value` 用 TEXT 存储任意类型（JSON/字符串/数字，由应用解析）。
- **用途**：存放运行时可变配置（如 RAG 阈值、模型参数、功能开关），读取优先走缓存，写穿 DB 失效缓存。
- **关系**：独立表，无外键依赖；不参与业务数据关联。
- **管理权限**：仅 admin 角色可写，其余角色只读（与 user.role 联动）。
