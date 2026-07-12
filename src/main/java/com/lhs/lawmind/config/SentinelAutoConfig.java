package com.lhs.lawmind.config;

import com.alibaba.csp.sentinel.annotation.aspectj.SentinelResourceAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel AOP 自动配置类
 * 
 * 重要说明:
 * Sentinel 的@SentinelResource 注解需要 AOP 支持才能生效
 * 这个配置类会创建 SentinelResourceAspect Bean，启用 AOP 拦截
 * 
 * 工作原理:
 * 1. Spring AOP 会自动扫描并拦截标记了@SentinelResource 的方法
 * 2. 在方法执行前检查限流规则
 * 3. 如果触发限流，调用 blockHandler 方法
 * 4. 如果发生异常，调用 fallback 方法
 * 
 * 注意: @EnableAspectJAutoProxy 已迁移至 AopConfig 统一管理
 */
@Configuration
public class SentinelAutoConfig {

    /**
     * 创建 SentinelResourceAspect Bean
     * 这是 Sentinel 能够拦截@SentinelResource 注解的关键
     */
    @Bean
    public SentinelResourceAspect sentinelResourceAspect() {
        return new SentinelResourceAspect();
    }
}
