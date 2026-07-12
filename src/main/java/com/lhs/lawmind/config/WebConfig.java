package com.lhs.lawmind.config;

import com.lhs.lawmind.interceptor.LogTraceInterceptor;
import com.lhs.lawmind.interceptor.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

/**
 * Web配置类
 * 
 * 安全加固说明：
 * - 减少了拦截器的排除路径
 * - 核心业务接口（ai-chat、conversation、hot-question、law-file等）现在都需要JWT认证
 * - 只有登录注册、健康检查等必要接口被排除
 * 
 * 拦截器顺序（重要！）：
 * 1. LogTraceInterceptor - 最先执行，初始化日志追踪
 * 2. JwtInterceptor - 验证 JWT，设置用户上下文
 * 
 * */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LogTraceInterceptor logTraceInterceptor;

    private final JwtInterceptor jwtInterceptor;

    public WebConfig(Optional<LogTraceInterceptor> logTraceInterceptor,
                     Optional<JwtInterceptor> jwtInterceptor) {
        this.logTraceInterceptor = logTraceInterceptor.orElse(null);
        this.jwtInterceptor = jwtInterceptor.orElse(null);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 1. 日志追踪拦截器（最先执行，所有请求都拦截）
        if (logTraceInterceptor != null) {
            registry.addInterceptor(logTraceInterceptor)
                    .addPathPatterns("/**")  // 拦截所有请求
                    .order(1);  // 优先级最高
        }

        // 2. JWT 认证拦截器（需要认证的接口）
        if (jwtInterceptor != null) {
            registry.addInterceptor(jwtInterceptor)
                    .addPathPatterns("/**")  // 拦截所有请求
                    .excludePathPatterns(
                            // 用户登录注册相关（必需公开）
                            "/user/login",
                            "/user/register",
                            "/user/refresh-token",
                            "/user/logout",
                            // Sentinel 注解测试接口（开发调试用）
                            "/sentinel-test/**",
                            // Actuator 健康检查（监控用）
                            "/actuator/**"
                    )
                    .order(2);  // 优先级次之
        }
    }
}
