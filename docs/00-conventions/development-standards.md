# 开发规范与评审标准

> 版本：V1.0 | 日期：2026-08-12 | 状态：✅ 生效
> 事实源：`src/main/java/com/lhs/lawmind` 源码约定、`src/main/resources/application*.yml`

---

## 一、总体原则

1. **分层架构**：`controller → service/impl → mapper → entity`，禁止跨层调用（controller 不直接操作 mapper/entity 逻辑）。
2. **统一响应**：所有 REST 接口返回 `Result<T>` / `PageResult<T>` 包装，禁止直接返回裸对象。
3. **错误处理**：业务异常抛 `BusinessException`（带错误码），由 `GlobalExceptionHandler` 统一转为 `Result`；不吞异常。
4. **不可变与清晰**：DTO/实体用 Lombok（`@Data`），方法职责单一（<50 行），命名见下述约定。
5. **AOP 切面**：审计/日志通过注解 + AOP 实现，不侵入业务代码。
6. **配置外置**：环境相关配置（DB/Redis/API Key）放 `application-*.yml`，不硬编码在代码中；敏感值用环境变量占位。

## 二、代码结构约定

| 层 | 包 | 说明 |
|----|----|------|
| 控制器 | `com.lhs.lawmind.controller` | `@RestController`，路径前缀 `/api`（全局 context-path） |
| 业务接口 | `com.lhs.lawmind.service` | Service 接口 |
| 业务实现 | `com.lhs.lawmind.service.impl` | `@Service` 实现 |
| 数据访问 | `com.lhs.lawmind.mapper` | MyBatis Mapper 接口，XML 在 `src/main/resources/mapper/*.xml` |
| 实体 | `com.lhs.lawmind.entity` | 与表结构一一对应，`mybatis.type-aliases-package` 已配置 |
| 传输对象 | `com.lhs.lawmind.dto` | 请求/响应 DTO，请求体用 `@Valid` 校验 |
| 通用 | `com.lhs.lawmind.common` | `Result`/`PageResult`/`BusinessException`/`GlobalExceptionHandler` |
| 安全 | `com.lhs.lawmind.security` | PII 脱敏、敏感话题过滤等法律安全组件 |
| AOP | `com.lhs.lawmind.aop` | 注解 + 切面（审计/日志） |

> **分层一致性**：全部 13 张表对应的实体统一在 `entity/`、Mapper 统一在 `mapper/`（含 `ai_memory`，XML 在 `resources/mapper/`）；业务按功能域内聚（如 `agent/` 下分 `compress`/`gate`/`memory`/`monitor`/`tool` 子包），`utils/` 为通用工具。

## 三、统一响应规范（Result）

- 成功：`{ "code": 200, "message": "success", "data": ... }`
- 分页：`PageResult<T>` 含 `total / page / limit / records`。
- 失败：`code` 非 200，`message` 为可读错误信息；`GlobalExceptionHandler` 兜底。
- 业务异常必须带明确错误码与消息，禁止返回 500 裸堆栈。

## 四、安全与鉴权

- 认证：JWT，请求头携带 Token，`JwtInterceptor` 拦截校验。
- 管理/运维接口：以 `lawmind.admin-user-id`（默认 4）校验管理员身份。
- **审计**：关键写操作加 `@SecurityAudit` 注解，记录到 `security_audit_log`。
- **日志**：controller/service 可加 `@Log` 注解；用 `@NoLog` 排除敏感/流式接口。
- 法律安全：对外输出前经过 `PII 脱敏` 与 `SensitiveTopicFilter` 敏感话题过滤；引用需 `verifyCitation` 校验。

## 五、AI 使用指南

1. **开发前先读**本文件「总体原则」与 [doc-writing.md](doc-writing.md)。
2. **改代码必同步文档**：同一 PR 内，按 [doc-writing.md](doc-writing.md) 4.5 全流程清单同步设计/接口/表文档。
3. **事实源优先**：接口以 Controller 源码为准、表以 `init_schema.sql` 为准、配置以 yml 为准；文档与源码冲突时改文档。
4. **评审清单**：提交前自审——无硬编码密钥、参数校验完整、异常处理明确、表字段与文档一致、接口与 common.md 约定一致。

## 六、评审标准（代码质量）

| 级别 | 标准 |
|------|------|
| 必须 | 函数 <50 行、文件 <800 行、嵌套 ≤4 层、命名清晰 |
| 必须 | 错误显式处理、无吞异常、无控制台调试残留 |
| 必须 | 新增接口/表/规则有对应测试或评估用例 |
| 必须 | 无硬编码密钥（用环境变量/配置占位符） |
| 必须 | 安全敏感代码（鉴权/用户输入/DB 查询/外部 API）过 security-review |
| 建议 | 覆盖 >=80% 核心逻辑路径 |

## 七、变更记录

| 版本 | 日期 | 变更 |
|------|------|------|
| V1.0 | 2026-08-12 | 初版：由 ai-docs skill 生成 |
