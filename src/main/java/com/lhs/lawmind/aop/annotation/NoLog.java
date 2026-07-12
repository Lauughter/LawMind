package com.lhs.lawmind.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 排除日志注解
 * 标记在方法上，表示该方法不需要AOP日志切面处理
 * 适用于已有丰富业务日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface NoLog {
}
