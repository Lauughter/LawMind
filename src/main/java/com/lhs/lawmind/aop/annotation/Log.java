package com.lhs.lawmind.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 自定义日志注解
 * 标记在方法或类上，用于AOP日志切面的细粒度控制
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Log {

    /** 操作描述 */
    String value() default "";

    /** 是否记录方法参数 */
    boolean logParams() default true;

    /** 是否记录返回值 */
    boolean logResult() default false;
}
