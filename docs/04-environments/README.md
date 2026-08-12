# 04 · 环境层（Environments）

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 已实现
> 事实源：`src/main/resources/application*.yml`

本层描述不同运行环境的配置差异与部署方式。配置以 `application*.yml` 为准。

## 文档清单

| 文档 | 说明 | 状态 |
|------|------|:---:|
| [development.md](development.md) | 本地开发环境：依赖 / 初始化 / 启动 / 端口 / 环境变量 | ✅ |
| [production.md](production.md) | 生产配置：差异项 / 安全清单 / 部署要点 | ✅ |

## 环境差异概览

| 项 | dev | prod |
|----|-----|------|
| 配置文件 | application-dev.example.yml（复制为 dev） | application-prod.yml |
| 敏感值（DB 密码 / API Key / JWT secret） | 有默认占位，dev 可用 | 必填环境变量 |
| Redis 连接池 | max-active 8 | max-active 20、max-wait 5000ms |
| 日志级别 | DEBUG/INFO | WARN/INFO |
| MyBatis log-impl | stdout | 关闭 |

## 使用指引

- **搭环境** → 读 development.md。
- **上生产前** → 按 production.md 安全清单逐项核对，尤其是密钥外置。

> 本层文档的引用关系登记在 [00-conventions/reference-map.md](../00-conventions/reference-map.md)。
