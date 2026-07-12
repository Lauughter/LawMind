# 合同功能安全加固说明

## 概述

本次安全加固为LawMind的合同生成功能添加了以下安全机制：

## 1. 权限控制

### 功能说明
- 用户只能访问、修改和删除自己的合同
- 通过RequestContext获取当前登录用户ID
- 在Service层验证合同所有权

### 关键实现
- `ContractServiceImpl.validateContractOwnership()` 验证用户是否有权限
- 所有查询、更新、删除操作都会先验证用户ID
- 禁用了`selectAll()`方法，强制使用按用户查询

## 2. 安全审计日志

### 功能说明
- 记录所有合同相关的重要操作
- 包含操作类型、用户、资源、请求信息等
- 支持成功/失败状态记录

### 关键实现
- `@SecurityAudit` 注解标记需要审计的方法
- `SecurityAuditAspect` AOP切面自动记录日志
- `SecurityAuditLog` 实体存储审计信息
- `security_audit_log` 表持久化审计数据

### 审计的操作
- QUERY: 查询合同列表和详情
- CREATE: 创建合同
- UPDATE: 更新合同
- DELETE: 删除合同
- GENERATE: 生成合同
- EXPORT: 导出合同

## 3. XSS防护

### 功能说明
- 过滤用户输入中的危险字符
- 防止跨站脚本攻击

### 关键实现
- `SecurityUtil` 工具类提供XSS过滤功能
- 在Service层对所有用户输入进行清理
- 过滤`<script>`、`eval()`、`javascript:`等危险内容

## 4. SQL注入防护

### 功能说明
- 使用MyBatis参数化查询
- 所有SQL操作都使用预编译语句
- 更新和删除操作增加用户ID条件

### 关键实现
- `ContractMapper.xml` 使用`#{param}`而非字符串拼接
- `update`和`delete`操作都包含`user_id`条件
- 新增`selectByIdAndUserId`和`deleteByIdAndUserId`方法

## 5. 数据验证

### 功能说明
- 验证合同标题长度（不超过200字符）
- 验证合同状态（DRAFT/FINISHED/SIGNED）
- 验证合同编号格式

## 数据库变更

需要执行以下SQL脚本：

```sql
-- V5_security_audit.sql
CREATE TABLE IF NOT EXISTS `security_audit_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `operation_type` VARCHAR(50) NOT NULL COMMENT '操作类型',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '操作描述',
  `resource_type` VARCHAR(50) NOT NULL COMMENT '资源类型',
  `resource_id` BIGINT DEFAULT NULL COMMENT '资源ID',
  `request_method` VARCHAR(10) DEFAULT NULL COMMENT '请求方法',
  `request_uri` VARCHAR(255) DEFAULT NULL COMMENT '请求URI',
  `request_params` TEXT DEFAULT NULL COMMENT '请求参数(JSON格式)',
  `client_ip` VARCHAR(50) DEFAULT NULL COMMENT '客户端IP',
  `request_id` VARCHAR(50) DEFAULT NULL COMMENT '请求ID',
  `result` VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果(SUCCESS/FAIL)',
  `error_message` VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_operation_type` (`operation_type`),
  INDEX `idx_resource_type` (`resource_type`),
  INDEX `idx_resource_id` (`resource_id`),
  INDEX `idx_create_time` (`create_time`),
  INDEX `idx_request_id` (`request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='安全审计日志表';
```

## 文件清单

### 新增文件
- `src/main/java/com/lhs/lawmind/aop/annotation/SecurityAudit.java` - 安全审计注解
- `src/main/java/com/lhs/lawmind/aop/aspect/SecurityAuditAspect.java` - 安全审计切面
- `src/main/java/com/lhs/lawmind/entity/SecurityAuditLog.java` - 审计日志实体
- `src/main/java/com/lhs/lawmind/mapper/SecurityAuditLogMapper.java` - 审计日志Mapper接口
- `src/main/resources/mapper/SecurityAuditLogMapper.xml` - 审计日志Mapper XML
- `src/main/java/com/lhs/lawmind/utils/SecurityUtil.java` - 安全工具类
- `src/main/resources/sql/V5_security_audit.sql` - 审计日志表建表SQL
- `SECURITY_README.md` - 本说明文档

### 修改文件
- `src/main/java/com/lhs/lawmind/controller/ContractController.java` - 添加安全审计注解
- `src/main/java/com/lhs/lawmind/service/impl/ContractServiceImpl.java` - 添加权限控制、XSS防护、日志
- `src/main/java/com/lhs/lawmind/mapper/ContractMapper.java` - 新增安全查询方法
- `src/main/resources/mapper/ContractMapper.xml` - 优化SQL查询，增加user_id条件

## 使用建议

1. **执行数据库脚本**：运行`V5_security_audit.sql`创建审计日志表
2. **测试权限控制**：验证用户无法访问其他用户的合同
3. **查看审计日志**：检查`security_audit_log`表是否正确记录操作
4. **验证XSS防护**：尝试输入包含`<script>`的内容，确认被过滤
