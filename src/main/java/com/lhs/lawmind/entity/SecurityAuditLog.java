package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

/**
 * 安全审计日志实体
 */
@Data
public class SecurityAuditLog {
    
    private Long id;
    
    /**
     * 用户ID
     */
    private Long userId;
    
    /**
     * 操作类型
     */
    private String operationType;
    
    /**
     * 操作描述
     */
    private String description;
    
    /**
     * 资源类型
     */
    private String resourceType;
    
    /**
     * 资源ID
     */
    private Long resourceId;
    
    /**
     * 请求方法
     */
    private String requestMethod;
    
    /**
     * 请求URI
     */
    private String requestUri;
    
    /**
     * 请求参数（JSON格式）
     */
    private String requestParams;
    
    /**
     * 客户端IP
     */
    private String clientIp;
    
    /**
     * 请求ID
     */
    private String requestId;
    
    /**
     * 操作结果（SUCCESS/FAIL）
     */
    private String result;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 创建时间
     */
    private Date createTime;
}
