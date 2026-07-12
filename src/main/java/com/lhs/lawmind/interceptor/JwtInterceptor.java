package com.lhs.lawmind.interceptor;

import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.utils.JwtUtil;
import com.lhs.lawmind.utils.TokenRedisUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.PrintWriter;

/**
 * JWT拦截器
 * 用于验证token有效性，并将用户信息存储到RequestContext中
 * 
 * 安全加固说明：
 * - 验证token是否在Redis中存在
 * - 支持主动撤销token
 * - 配合RequestContext使用，统一管理上下文
 * 
 * */
@RequiredArgsConstructor
@Slf4j
@Component
public class JwtInterceptor implements HandlerInterceptor {

    // MDC 的 key 常量
    private static final String MDC_KEY_USER_ID = "userId";

    private final JwtUtil jwtUtil;

    private final TokenRedisUtil tokenRedisUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 注意：LogTraceInterceptor 已经先执行了，RequestContext 已经初始化
        
        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            log.warn("请求未携带token或token格式错误: {}", request.getRequestURI());
            response.setContentType("application/json;charset=utf-8");
            PrintWriter out = response.getWriter();
            out.write("{\"code\": 401, \"message\": \"未授权，请先登录\"}");
            out.flush();
            out.close();
            return false;
        }
        
        token = token.substring(7);
        
        // 1. 首先验证token是否在Redis中存在（支持主动撤销）
        if (!tokenRedisUtil.isTokenExists(token)) {
            log.warn("token不存在或已撤销: {}", request.getRequestURI());
            response.setContentType("application/json;charset=utf-8");
            PrintWriter out = response.getWriter();
            out.write("{\"code\": 401, \"message\": \"token已失效，请重新登录\"}");
            out.flush();
            out.close();
            return false;
        }
        
        // 2. 验证token有效性（签名和过期时间）
        if (!jwtUtil.validateToken(token)) {
            log.warn("token无效或已过期: {}", request.getRequestURI());
            // 从Redis中删除无效的token
            tokenRedisUtil.deleteToken(token);
            response.setContentType("application/json;charset=utf-8");
            PrintWriter out = response.getWriter();
            out.write("{\"code\": 401, \"message\": \"token无效或已过期，请重新登录\"}");
            out.flush();
            out.close();
            return false;
        }
        
        // 3. 从token中提取用户ID和角色并存储到RequestContext
        try {
            String userIdStr = jwtUtil.getUserIdFromToken(token);
            Long userId = Long.parseLong(userIdStr);
            String role = jwtUtil.getRoleFromToken(token);

            // 设置到 RequestContext
            RequestContext.setUserId(userId);
            RequestContext.setRole(role);

            // 设置到 MDC（用于日志输出）
            MDC.put(MDC_KEY_USER_ID, userIdStr);

            log.debug("用户认证成功: userId={}, role={}, uri={}", userId, role, request.getRequestURI());
        } catch (Exception e) {
            log.error("从token中提取用户ID失败", e);
            response.setContentType("application/json;charset=utf-8");
            PrintWriter out = response.getWriter();
            out.write("{\"code\": 401, \"message\": \"token解析失败，请重新登录\"}");
            out.flush();
            out.close();
            return false;
        }
        
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 注意：LogTraceInterceptor 会在最后清除 RequestContext 和 MDC
        // 这里不需要再清除了
        log.debug("JWT拦截器完成: uri={}", request.getRequestURI());
    }
}
