package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Token Redis 管理工具类
 */
@Slf4j
@Component
public class TokenRedisUtil {

    private static final String TOKEN_KEY_PREFIX = "auth:token:";
    private static final String USER_TOKENS_KEY_PREFIX = "auth:user:tokens:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;

    public TokenRedisUtil(
            Optional<RedisTemplate<String, Object>> redisTemplate,
            @Value("${jwt.expire:7200}") long accessTokenExpire,
            @Value("${jwt.refresh-expire:604800}") long refreshTokenExpire) {
        this.redisTemplate = redisTemplate.orElse(null);
        this.accessTokenExpire = accessTokenExpire;
        this.refreshTokenExpire = refreshTokenExpire;
    }

    /**
     * 存储 accessToken 到 Redis
     */
    public void storeAccessToken(Long userId, String accessToken) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法存储 accessToken");
            return;
        }

        try {
            String tokenKey = TOKEN_KEY_PREFIX + accessToken;
            String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;

            redisTemplate.opsForValue().set(tokenKey, userId, accessTokenExpire, TimeUnit.SECONDS);
            redisTemplate.opsForSet().add(userTokensKey, accessToken);
            redisTemplate.expire(userTokensKey, refreshTokenExpire, TimeUnit.SECONDS);

            log.info("存储 accessToken 成功: userId={}", userId);
        } catch (Exception e) {
            log.error("存储 accessToken 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 存储 refreshToken 到 Redis
     */
    public void storeRefreshToken(Long userId, String refreshToken) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法存储 refreshToken");
            return;
        }

        try {
            String tokenKey = TOKEN_KEY_PREFIX + refreshToken;
            String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;

            redisTemplate.opsForValue().set(tokenKey, userId, refreshTokenExpire, TimeUnit.SECONDS);
            redisTemplate.opsForSet().add(userTokensKey, refreshToken);
            redisTemplate.expire(userTokensKey, refreshTokenExpire, TimeUnit.SECONDS);

            log.info("存储 refreshToken 成功: userId={}", userId);
        } catch (Exception e) {
            log.error("存储 refreshToken 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 同时存储 accessToken 和 refreshToken
     */
    public void storeTokens(Long userId, String accessToken, String refreshToken) {
        storeAccessToken(userId, accessToken);
        storeRefreshToken(userId, refreshToken);
    }

    /**
     * 验证 token 是否存在于 Redis 中
     */
    public boolean isTokenExists(String token) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法验证 token");
            return false;
        }

        try {
            String tokenKey = TOKEN_KEY_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(tokenKey));
        } catch (Exception e) {
            log.error("验证 token 失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 从 token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法从 token 获取用户ID");
            return null;
        }

        try {
            String tokenKey = TOKEN_KEY_PREFIX + token;
            Object userIdObj = redisTemplate.opsForValue().get(tokenKey);
            if (userIdObj != null) {
                return Long.parseLong(userIdObj.toString());
            }
            return null;
        } catch (Exception e) {
            log.error("从 token 获取用户ID 失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 删除单个 token
     */
    public void deleteToken(String token) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法删除 token");
            return;
        }

        try {
            String tokenKey = TOKEN_KEY_PREFIX + token;
            Long userId = getUserIdFromToken(token);
            redisTemplate.delete(tokenKey);
            if (userId != null) {
                String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;
                redisTemplate.opsForSet().remove(userTokensKey, token);
            }
            log.info("删除 token 成功");
        } catch (Exception e) {
            log.error("删除 token 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 清除用户的所有 token（登出、修改密码时调用）
     */
    public void clearUserTokens(Long userId) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法清除用户 token");
            return;
        }

        try {
            String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;
            Set<Object> tokens = redisTemplate.opsForSet().members(userTokensKey);

            if (tokens != null && !tokens.isEmpty()) {
                List<String> tokenKeys = tokens.stream()
                        .map(t -> TOKEN_KEY_PREFIX + t.toString())
                        .collect(Collectors.toList());
                tokenKeys.add(userTokensKey);
                redisTemplate.delete(tokenKeys);

                log.info("清除用户所有 token 成功: userId={}, token数量={}", userId, tokens.size());
            }
        } catch (Exception e) {
            log.error("清除用户 token 失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取用户的 token 数量
     */
    public long getUserTokenCount(Long userId) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法获取用户 token 数量");
            return 0;
        }

        try {
            String userTokensKey = USER_TOKENS_KEY_PREFIX + userId;
            Long count = redisTemplate.opsForSet().size(userTokensKey);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取用户 token 数量失败: {}", e.getMessage(), e);
            return 0;
        }
    }
}
