package com.lhs.lawmind.aop.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 安全审计注解
 * 用于标记需要进行安全审计的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SecurityAudit {
    
    /**
     * 操作类型
     */
    String operationType();
    
    /**
     * 操作描述
     */
    String description() default "";
    
    /**
     * 资源类型（如 CONTRACT, USER 等）
     */
    String resourceType();
    
    /**
     * 是否记录请求参数
     */
    boolean logParams() default true;
}
