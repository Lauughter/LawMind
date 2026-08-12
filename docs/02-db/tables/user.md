# user（用户表）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：src/main/resources/sql/init_schema.sql | 关联模块：用户认证模块
> 关联文档：[data-dictionary.md](../data-dictionary.md)、[conventions.md](../conventions.md)

**用途**：存储系统用户账号、角色与登录信息，支持管理员/普通用户双角色，含软删除。

## 字段

| 字段 | 类型 | 空 | 默认 | 含义 |
|------|------|:--:|------|------|
| id | BIGINT | 否 | AUTO_INCREMENT | 主键，用户 ID |
| username | VARCHAR(64) | 否 | — | 用户名（唯一） |
| password | VARCHAR(256) | 否 | — | 加密密码（bcrypt 哈希，非明文） |
| nickname | VARCHAR(64) | 是 | NULL | 昵称 |
| phone | VARCHAR(20) | 是 | NULL | 手机号 |
| role | VARCHAR(32) | 是 | 'user' | 角色（见枚举） |
| create_time | DATETIME | 是 | CURRENT_TIMESTAMP | 创建时间 |
| update_time | DATETIME | 是 | CURRENT_TIMESTAMP ON UPDATE | 更新时间 |
| last_login_time | DATETIME | 是 | NULL | 最后登录时间 |
| is_deleted | INT | 是 | 0 | 软删除标记：0-正常 1-已删除 |

## 索引

| 名称 | 类型 | 字段 | 用途 |
|------|------|------|------|
| PRIMARY | 主键 | id | 主键 |
| idx_username | 唯一 | username | 用户名唯一约束，登录查询 |

## 枚举

`role`（VARCHAR(32)）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| user | ROLE_USER | 普通用户 | 默认角色 |
| admin | ROLE_ADMIN | 管理员 | 拥有系统管理、审核、配置权限 |

`is_deleted`（INT）：

| 值 | 常量 | 含义 | 触发条件 |
|----|------|------|----------|
| 0 | NOT_DELETED | 正常 | 默认 |
| 1 | DELETED | 已删除（软删） | 用户注销/被删除时置 1，数据保留 |

## 业务规则

- **密码**：`password` 存 bcrypt 哈希（`$2b$12$` 前缀，长度远超 VARCHAR(256) 需求），绝不明文存储；schema 底部 INSERT 了默认管理员 `admin/123456`，为硬编码默认凭据，上线前必须改密。
- **软删除**：`is_deleted=1` 时账号逻辑删除，不物理删除；登录与查询须过滤 `is_deleted=0`。
- **关系**：1 对多 → conversation.user_id、ai_chat.user_id、law_knowledge.user_id、law_file_upload.user_id、ai_memory.user_id、security_audit_log.user_id（均为逻辑关联，无物理外键）。
- **角色用途**：admin 可进入审核（ai_chat.feedback_status 审核、review_log）与系统配置（sys_config）。
