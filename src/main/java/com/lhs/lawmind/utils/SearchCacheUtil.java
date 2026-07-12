package com.lhs.lawmind.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lhs.lawmind.entity.LawKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 搜索缓存工具类
 * 使用 StringRedisTemplate 直接存取 JSON 字符串，避免 GenericJackson2JsonRedisSerializer 的类型擦除和双编码问题
 * - 有结果缓存 5 分钟
 * - 计数缓存 5 分钟（翻页不重复 count）
 */
@Slf4j
@Component
public class SearchCacheUtil {

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    // 缓存 key 设计： law:search:list:{keywordHash}:{type}:{page}:{pageSize}
    private static final String RESULT_PREFIX = "law:search:list:";

    // law:search:count:{keywordHash}:{type}
    private static final String COUNT_PREFIX = "law:search:count:";

    // 结果 TTL 设计为 5 分钟
    private static final long RESULT_TTL = 300;

    // 计数 TTL 设计为 5 分钟
    private static final long COUNT_TTL = 300;

    public SearchCacheUtil(Optional<StringRedisTemplate> stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate.orElse(null);
        this.objectMapper = new ObjectMapper();
    }

    private String md5(String input) {
        if (input == null || input.isEmpty()) return "_";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }

    private String resultKey(String keyword, String type, int page, int pageSize) {
        return RESULT_PREFIX + md5(keyword) + ":" + (type != null ? type : "_") + ":" + page + ":" + pageSize;
    }

    private String countKey(String keyword, String type) {
        return COUNT_PREFIX + md5(keyword) + ":" + (type != null ? type : "_");
    }

    public List<LawKnowledge> getSearchResult(String keyword, String type, int page, int pageSize) {
        if (stringRedisTemplate == null) return null;
        try {
            String key = resultKey(keyword, type, page, pageSize);
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json == null || json.isEmpty()) return null;
            return objectMapper.readValue(json, new TypeReference<List<LawKnowledge>>() {});
        } catch (Exception e) {
            log.debug("读取搜索结果缓存失败: keyword={}, error={}", keyword, e.getMessage());
            return null;
        }
    }

    public void setSearchResult(String keyword, String type, int page, int pageSize, List<LawKnowledge> result) {
        if (stringRedisTemplate == null) return;
        if (result.isEmpty()) return;
        try {
            String key = resultKey(keyword, type, page, pageSize);
            String json = objectMapper.writeValueAsString(result);
            stringRedisTemplate.opsForValue().set(key, json, RESULT_TTL, TimeUnit.SECONDS);
            log.debug("搜索结果缓存已写入: key={}, size={}, ttl={}s", key, result.size(), RESULT_TTL);
        } catch (Exception e) {
            log.debug("写入搜索结果缓存失败: keyword={}, error={}", keyword, e.getMessage());
        }
    }

    public Long getSearchCount(String keyword, String type) {
        if (stringRedisTemplate == null) return null;
        try {
            String key = countKey(keyword, type);
            String value = stringRedisTemplate.opsForValue().get(key);
            if (value == null) return null;
            return Long.parseLong(value);
        } catch (Exception e) {
            log.debug("读取计数缓存失败: keyword={}, error={}", keyword, e.getMessage());
            return null;
        }
    }

    public void setSearchCount(String keyword, String type, long count) {
        if (stringRedisTemplate == null) return;
        try {
            String key = countKey(keyword, type);
            stringRedisTemplate.opsForValue().set(key, String.valueOf(count), COUNT_TTL, TimeUnit.SECONDS);
            log.debug("计数缓存已写入: key={}, count={}", key, count);
        } catch (Exception e) {
            log.debug("写入计数缓存失败: keyword={}, error={}", keyword, e.getMessage());
        }
    }
}
