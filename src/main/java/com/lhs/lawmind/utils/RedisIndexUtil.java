package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Redis索引工具类（优化版）
 * 封装Redis Search索引相关的操作，增加缓存机制
 */
@Slf4j
@Component
public class RedisIndexUtil {

    /**
     * 检查索引是否已存在（带缓存优化）
     * 
     * @param redisTemplate RedisTemplate实例
     * @param indexName 索引名称
     * @return 索引是否存在
     */
    public boolean indexExists(RedisTemplate<String, Object> redisTemplate, String indexName) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, cannot check index existence");
            return false;
        }

        // 1. 首先检查缓存
        if (IndexStatusCache.get(indexName)) {
            return true;
        }

        // 2. 缓存未命中，执行实际检查
        boolean exists = checkIndexActual(redisTemplate, indexName);
        
        // 3. 更新缓存（无论存在与否都缓存，避免重复查询不存在的索引）
        IndexStatusCache.put(indexName, exists);
        
        return exists;
    }
    
    /**
     * 实际执行索引存在性检查（原始逻辑）
     */
    private boolean checkIndexActual(RedisTemplate<String, Object> redisTemplate, String indexName) {
        try {
            log.debug("[检查] 开始检查索引是否存在: {}", indexName);
            
            // 尝试执行 FT.INFO 命令
            Boolean result = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                try {
                    log.debug("[检查] 执行 ft.info 命令检查索引：{}", indexName);
                    
                    byte[][] args = new byte[][]{ indexName.getBytes() };

                    Object response = connection.execute("ft.info", args);
                    
                    log.debug("[检查] 响应对象：{}", response);
                    log.debug("[检查] 响应类型：{}", response != null ? response.getClass().getName() : "null");
                    
                    if (response != null) {
                        log.info("[检查] ft.info 命令执行成功，索引存在：{}", indexName);
                        return true;
                    } else {
                        log.warn("[检查] ft.info 返回 null，索引可能不存在：{}", indexName);
                        return false;
                    }
                } catch (Exception e) {
                    // 索引不存在时会抛出异常，这是正常的
                    log.debug("[检查] ft.info 执行异常，索引不存在：{} - {}", indexName, e.getMessage());
                    return false;
                }
            });
            
            // 根据执行结果判断索引是否存在
            if (Boolean.TRUE.equals(result)) {
                log.info("[检查] 索引已存在: {}", indexName);
                return true;
            } else {
                log.info("[检查] 索引不存在: {}", indexName);
                return false;
            }
            
        } catch (Exception e) {
            // 捕获异常，说明索引不存在
            log.error("[检查] 索引检查过程发生异常：{} - {}", indexName, e.getMessage());
            return false;
        }
    }
    
    /**
     * 强制刷新索引状态缓存
     */
    public void refreshIndexStatus(RedisTemplate<String, Object> redisTemplate, String indexName) {
        boolean exists = checkIndexActual(redisTemplate, indexName);
        IndexStatusCache.refresh(indexName, exists);
        log.info("手动刷新索引状态: {} = {}", indexName, exists);
    }
    
    /**
     * 获取缓存统计信息
     */
    public IndexStatusCache.CacheStats getCacheStats() {
        return IndexStatusCache.getStats();
    }
    
    /**
     * 清空索引状态缓存
     */
    public void clearCache() {
        IndexStatusCache.clear();
    }
}