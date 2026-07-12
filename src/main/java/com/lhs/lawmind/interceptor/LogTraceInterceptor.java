package com.lhs.lawmind.interceptor;

import com.lhs.lawmind.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 日志追踪拦截器
 * 
 * 功能说明：
 * 1. 为每个请求生成唯一的 requestId
 * 2. 将 requestId、userId 等放入 MDC（Mapped Diagnostic Context）
 * 3. 日志中自动输出追踪信息，方便问题排查
 * 4. 请求结束后清理上下文，防止内存泄漏
 * 
 * 使用方法：
 * 在日志配置中添加 %X{reqId}、%X{userId} 等占位符
 * 
 * @author LawMind
 */
@Slf4j
@Component
public class LogTraceInterceptor implements HandlerInterceptor {

    // MDC 的 key 常量
    private static final String MDC_KEY_REQUEST_ID = "reqId";
    private static final String MDC_KEY_USER_ID = "userId";
    private static final String MDC_KEY_CONV_ID = "convId";

    /**
     * 请求处理前执行
     * 1. 生成并设置 requestId
     * 2. 设置 MDC
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 清空旧的上下文
        RequestContext.clear();
        MDC.clear();

        // 2. 生成并设置请求 ID
        String requestId = RequestContext.generateRequestId();
        
        // 3. 放入 MDC（用于日志输出）
        MDC.put(MDC_KEY_REQUEST_ID, requestId);
        
        // 4. 记录请求开始
        String requestUri = request.getRequestURI();
        String requestMethod = request.getMethod();
        log.info("┌─────────────────────────────────────────────────────────────");
        log.info("│ 请求开始: {} {} | reqId={}", requestMethod, requestUri, requestId);
        log.info("└─────────────────────────────────────────────────────────────");

        return true;
    }

    /**
     * 请求处理后执行（视图渲染前）
     * 可选：记录响应状态
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 1. 获取请求信息
        String requestId = RequestContext.getRequestId();
        Long userId = RequestContext.getUserId();
        String conversationId = RequestContext.getConversationId();
        String requestUri = request.getRequestURI();
        int status = response.getStatus();

        // 2. 记录请求结束
        if (ex != null) {
            log.info("┌─────────────────────────────────────────────────────────────");
            log.info("│ 请求异常: {} | reqId={} | userId={} | convId={}", 
                    requestUri, requestId, userId, conversationId);
            log.error("│ 异常信息: {}", ex.getMessage(), ex);
            log.info("└─────────────────────────────────────────────────────────────");
        } else {
            log.info("┌─────────────────────────────────────────────────────────────");
            log.info("│ 请求完成: {} | 状态: {} | reqId={} | userId={} | convId={}", 
                    requestUri, status, requestId, userId, conversationId);
            log.info("└─────────────────────────────────────────────────────────────");
        }

        // 3. 重要：清空上下文，防止内存泄漏！
        RequestContext.clear();
        MDC.clear();
    }
}
