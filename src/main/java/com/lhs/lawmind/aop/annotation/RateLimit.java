package com.lhs.lawmind.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口限流注解
 * 基于 Redis + Lua 滑动窗口算法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    /** 时间窗口内允许的最大请求数，默认 60 */
    int limit() default 60;

    /** 时间窗口（秒），默认 60 */
    int windowSeconds() default 60;

    /** 限流提示信息 */
    String message() default "请求过于频繁，请稍后再试";
}
