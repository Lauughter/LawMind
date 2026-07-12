package com.lhs.lawmind.utils;

import com.lhs.lawmind.config.RagConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 热点缓存工具类
 * 使用 StringRedisTemplate 直接存取纯文本，避免 GenericJackson2JsonRedisSerializer 的 JSON 包装
 */
@Slf4j
@Component
public class HotCacheUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final RagConfig ragConfig;

    public HotCacheUtil(Optional<StringRedisTemplate> stringRedisTemplate, RagConfig ragConfig) {
        this.stringRedisTemplate = stringRedisTemplate.orElse(null);
        this.ragConfig = ragConfig;
    }

    public String getHotQuestionCache(String md5) {
        if (stringRedisTemplate == null) {
            log.warn("StringRedisTemplate is null, 无法查询热点缓存");
            return null;
        }
        try {
            String key = ragConfig.getHotQuestionKeyPrefix() + md5;
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value != null) {
                stringRedisTemplate.expire(key, ragConfig.getHotCacheInitialTtlDays(), TimeUnit.DAYS);
                log.info("热点缓存命中: {}, 续期{}天", key, ragConfig.getHotCacheInitialTtlDays());
                return value;
            }
            log.debug("热点缓存未命中: {}", key);
            return null;
        } catch (Exception e) {
            log.error("查询热点缓存失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public void setHotQuestionCache(String md5, String answer) {
        if (stringRedisTemplate == null) {
            log.warn("StringRedisTemplate is null, 无法存入热点缓存");
            return;
        }
        try {
            String key = ragConfig.getHotQuestionKeyPrefix() + md5;
            stringRedisTemplate.opsForValue().set(key, answer, ragConfig.getHotCacheInitialTtlDays(), TimeUnit.DAYS);
            log.info("存入热点缓存: {}, TTL={}天", key, ragConfig.getHotCacheInitialTtlDays());
        } catch (Exception e) {
            log.error("存入热点缓存失败: {}", e.getMessage(), e);
        }
    }

    public void deleteHotQuestionCache(String md5) {
        if (stringRedisTemplate == null) {
            log.warn("StringRedisTemplate is null, 无法删除热点缓存");
            return;
        }
        try {
            String key = ragConfig.getHotQuestionKeyPrefix() + md5;
            stringRedisTemplate.delete(key);
            log.info("删除热点缓存: {}", key);
        } catch (Exception e) {
            log.error("删除热点缓存失败: {}", e.getMessage(), e);
        }
    }
}
