package com.lhs.lawmind.aop.aspect;

import com.lhs.lawmind.aop.annotation.Log;
import com.lhs.lawmind.aop.annotation.NoLog;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Service层日志切面
 * 统一记录Service方法的执行信息、耗时、异常
 */
@Slf4j
@Aspect
@Order(20)
@Component
public class ServiceLogAspect {

    @Pointcut("execution(* com.lhs.lawmind.service..*(..))")
    public void servicePointcut() {}

    @Around("servicePointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 跳过 @NoLog 标注的方法
        if (method.isAnnotationPresent(NoLog.class)) {
            return joinPoint.proceed();
        }

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        // 判断是否为异步方法
        boolean isAsync = method.isAnnotationPresent(Async.class);
        String prefix = isAsync ? "[Service-Async]" : "[Service]";

        // 获取 @Log 注解信息，决定日志级别
        Log logAnnotation = method.getAnnotation(Log.class);
        boolean useInfoLevel = logAnnotation != null;
        String description = (logAnnotation != null && !logAnnotation.value().isEmpty())
                ? " | " + logAnnotation.value()
                : "";

        // 记录方法开始
        String params = Arrays.toString(joinPoint.getArgs());
        if (useInfoLevel) {
            log.info("{} {}.{}{} | 开始 | params={}", prefix, className, methodName, description, params);
        } else {
            log.debug("{} {}.{} | 开始 | params={}", prefix, className, methodName, params);
        }

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long costTime = System.currentTimeMillis() - startTime;

            if (useInfoLevel) {
                log.info("{} {}.{}{} | 完成 | 耗时={}ms", prefix, className, methodName, description, costTime);
            } else {
                log.debug("{} {}.{} | 完成 | 耗时={}ms", prefix, className, methodName, costTime);
            }
            return result;
        } catch (Throwable e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("{} {}.{}{} | 异常 | 耗时={}ms | error={}", prefix, className, methodName, description, costTime, e.getMessage(), e);
            throw e;
        }
    }
}
