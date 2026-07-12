package com.lhs.lawmind.aop.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Scheduler层日志切面
 * 统一记录定时任务的开始、完成、耗时和异常
 * 注意：异常会被捕获并记录，不会重新抛出，防止杀死调度线程
 */
@Slf4j
@Aspect
@Order(5)
@Component
public class SchedulerLogAspect {

    @Pointcut("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public void scheduledPointcut() {}

    @Around("scheduledPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();

        log.info("[Scheduler] {}.{} | 任务开始", className, methodName);

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long costTime = System.currentTimeMillis() - startTime;
            log.info("[Scheduler] {}.{} | 任务完成 | 耗时={}ms", className, methodName, costTime);
            return result;
        } catch (Throwable e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[Scheduler] {}.{} | 任务失败 | 耗时={}ms | error={}", className, methodName, costTime, e.getMessage(), e);
            // 捕获异常不重新抛出，防止调度线程被杀死
            return null;
        }
    }
}
