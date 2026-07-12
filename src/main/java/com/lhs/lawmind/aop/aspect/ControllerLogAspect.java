package com.lhs.lawmind.aop.aspect;

import com.lhs.lawmind.aop.annotation.Log;
import com.lhs.lawmind.aop.annotation.NoLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Controller层日志切面
 * 统一记录Controller方法的请求参数、执行时间、异常信息
 */
@Slf4j
@Aspect
@Order(10)
@Component
public class ControllerLogAspect {

    @Pointcut("execution(* com.lhs.lawmind.controller..*(..))")
    public void controllerPointcut() {}

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 跳过 @NoLog 标注的方法
        if (method.isAnnotationPresent(NoLog.class)) {
            return joinPoint.proceed();
        }

        // 跳过 Sentinel blockHandler/fallback 方法
        if (isSentinelHandler(method)) {
            return joinPoint.proceed();
        }

        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        // 获取 @Log 注解信息
        Log logAnnotation = method.getAnnotation(Log.class);
        String description = (logAnnotation != null && !logAnnotation.value().isEmpty())
                ? logAnnotation.value()
                : methodName;
        boolean logParams = logAnnotation == null || logAnnotation.logParams();
        boolean logResult = logAnnotation != null && logAnnotation.logResult();

        // 记录请求开始
        if (logParams) {
            String params = buildParamString(signature, joinPoint.getArgs());
            log.info("[Controller] {}.{} | {} | 开始 | params={}", className, methodName, description, params);
        } else {
            log.info("[Controller] {}.{} | {} | 开始", className, methodName, description);
        }

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long costTime = System.currentTimeMillis() - startTime;

            if (logResult && result != null) {
                log.info("[Controller] {}.{} | {} | 完成 | 耗时={}ms | result={}", className, methodName, description, costTime, result);
            } else {
                log.info("[Controller] {}.{} | {} | 完成 | 耗时={}ms", className, methodName, description, costTime);
            }
            return result;
        } catch (Throwable e) {
            long costTime = System.currentTimeMillis() - startTime;
            log.error("[Controller] {}.{} | {} | 异常 | 耗时={}ms | error={}", className, methodName, description, costTime, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 构建参数字符串，跳过不适合日志输出的参数类型
     */
    private String buildParamString(MethodSignature signature, Object[] args) {
        Parameter[] parameters = signature.getMethod().getParameters();
        Map<String, Object> paramMap = new LinkedHashMap<>();

        for (int i = 0; i < parameters.length; i++) {
            Object arg = args[i];
            String name = parameters[i].getName();

            if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse) {
                continue;
            }
            if (arg instanceof MultipartFile file) {
                paramMap.put(name, "MultipartFile(" + file.getOriginalFilename() + ", " + file.getSize() + "bytes)");
                continue;
            }
            paramMap.put(name, arg);
        }
        return paramMap.toString();
    }

    /**
     * 判断是否为Sentinel的blockHandler或fallback方法
     */
    private boolean isSentinelHandler(Method method) {
        for (Class<?> paramType : method.getParameterTypes()) {
            if ("BlockException".equals(paramType.getSimpleName())) {
                return true;
            }
        }
        String name = method.getName();
        return name.startsWith("handleBlock") || name.startsWith("handleFallback");
    }
}
