package com.lhs.lawmind.aop.aspect;

import com.lhs.lawmind.aop.annotation.RateLimit;
import com.lhs.lawmind.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.lhs.lawmind.common.BusinessException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 接口限流切面
 * 使用 Redis + Lua 滑动窗口算法，保证原子性
 */
@Aspect
@Component
@Slf4j
public class RateLimitAspect {

    private final RedisTemplate<String, Object> redisTemplate;

    // Redis 键前缀，格式：law:ratelimit:{methodKey}:{userIdentifier}
    private static final String KEY_PREFIX = "law:ratelimit:";

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local window_ms = tonumber(ARGV[2]) * 1000
            local now = tonumber(ARGV[3])
            local member = ARGV[4]

            redis.call('ZREMRANGEBYSCORE', key, 0, now - window_ms)
            local count = redis.call('ZCARD', key)

            if count < limit then
                redis.call('ZADD', key, now, member)
                redis.call('PEXPIRE', key, window_ms)
                return 1
            else
                return 0
            end
            """;

    public RateLimitAspect(Optional<RedisTemplate<String, Object>> redisTemplate) {
        this.redisTemplate = redisTemplate.orElse(null);
    }

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        if (redisTemplate == null) {
            return joinPoint.proceed();
        }

        String key = buildKey(joinPoint);
        long now = System.currentTimeMillis();
        String member = now + ":" + UUID.randomUUID().toString().substring(0, 8);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(LUA_SCRIPT, Long.class);
        Long allowed = redisTemplate.execute(script, List.of(key),
                rateLimit.limit(), rateLimit.windowSeconds(), now, member);

        if (allowed != null && allowed == 1L) {
            return joinPoint.proceed();
        }

        log.warn("接口限流触发: key={}, limit={}/{}{}s",
                key, rateLimit.limit(), rateLimit.windowSeconds(), rateLimit.message());
        throw BusinessException.tooManyRequests(rateLimit.message());
    }

    /**
     * 构建 Redis 键，格式：law:ratelimit:{methodKey}:{userIdentifier}
     * - methodKey: 方法签名简短字符串，区分不同接口
     * - userIdentifier: 优先使用用户 ID，格式 "u{userId}"；如果没有用户 ID，则使用 IP 地址（考虑 X-Forwarded-For 和 X-Real-IP）；如果都没有，则使用 "anonymous"
     * @param joinPoint 切点信息
     * @return Redis 键
     */
    private String buildKey(ProceedingJoinPoint joinPoint) {
        String methodKey = joinPoint.getSignature().toShortString();
        String userIdentifier = getUserIdOrIp();
        return KEY_PREFIX + methodKey + ":" + userIdentifier;
    }

    private String getUserIdOrIp() {
        try {
            Long userId = RequestContext.getUserId();
            if (userId != null) return "u" + userId;
        } catch (Exception ignored) {
        }

        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isEmpty()) {
                    return forwarded.split(",")[0].trim();
                }
                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isEmpty()) return realIp;
                return request.getRemoteAddr();
            }
        } catch (Exception ignored) {
        }

        return "anonymous";
    }
}
