package com.lhs.lawmind.aop.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhs.lawmind.aop.annotation.SecurityAudit;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.entity.SecurityAuditLog;
import com.lhs.lawmind.mapper.SecurityAuditLogMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 安全审计切面
 * 用于记录重要操作的安全审计日志
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class SecurityAuditAspect {

    private final SecurityAuditLogMapper securityAuditLogMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Around("@annotation(securityAudit)")
    public Object audit(ProceedingJoinPoint joinPoint, SecurityAudit securityAudit) throws Throwable {
        long startTime = System.currentTimeMillis();
        SecurityAuditLog auditLog = new SecurityAuditLog();
        
        try {
            // 设置审计日志基本信息
            auditLog.setUserId(RequestContext.getUserId());
            auditLog.setOperationType(securityAudit.operationType());
            auditLog.setDescription(securityAudit.description());
            auditLog.setResourceType(securityAudit.resourceType());
            auditLog.setRequestId(RequestContext.getRequestId());
            auditLog.setCreateTime(new Date());
            
            // 获取HTTP请求信息
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                auditLog.setRequestMethod(request.getMethod());
                auditLog.setRequestUri(request.getRequestURI());
                auditLog.setClientIp(getClientIp(request));
            }
            
            // 记录请求参数
            if (securityAudit.logParams()) {
                Map<String, Object> params = new HashMap<>();
                MethodSignature signature = (MethodSignature) joinPoint.getSignature();
                String[] paramNames = signature.getParameterNames();
                Object[] args = joinPoint.getArgs();
                
                for (int i = 0; i < paramNames.length; i++) {
                    params.put(paramNames[i], args[i]);
                }
                
                auditLog.setRequestParams(objectMapper.writeValueAsString(params));
            }
            
            // 尝试从参数中提取资源ID
            extractResourceId(joinPoint, auditLog);
            
            // 执行目标方法
            Object result = joinPoint.proceed();
            
            // 记录成功结果
            auditLog.setResult("SUCCESS");
            return result;
            
        } catch (Throwable e) {
            // 记录失败结果
            auditLog.setResult("FAIL");
            auditLog.setErrorMessage(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            throw e;
            
        } finally {
            try {
                // 保存审计日志
                securityAuditLogMapper.insert(auditLog);
                log.info("安全审计日志已记录: operation={}, resourceType={}, result={}", 
                        auditLog.getOperationType(), auditLog.getResourceType(), auditLog.getResult());
            } catch (Exception e) {
                log.error("保存安全审计日志失败", e);
            }
        }
    }

    /**
     * 从方法参数中提取资源ID
     */
    private void extractResourceId(ProceedingJoinPoint joinPoint, SecurityAuditLog auditLog) {
        Object[] args = joinPoint.getArgs();
        for (Object arg : args) {
            if (arg instanceof Long) {
                auditLog.setResourceId((Long) arg);
                break;
            }
            try {
                // 尝试从对象中获取id字段
                Object id = objectMapper.convertValue(arg, Map.class).get("id");
                if (id instanceof Number) {
                    auditLog.setResourceId(((Number) id).longValue());
                    break;
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * 获取客户端真实IP
     */
    private String getClientIp(HttpServletRequest request) {
        String[] headers = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED"
        };
        
        for (String header : headers) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // 多个代理时，第一个IP为客户端真实IP
                int index = ip.indexOf(',');
                return index != -1 ? ip.substring(0, index).trim() : ip.trim();
            }
        }
        
        return request.getRemoteAddr();
    }
}
