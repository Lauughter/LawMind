package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis索引状态缓存工具类
 * 用于缓存Redis索引的存在状态，避免重复查询
 * 纯静态工具类，不依赖Spring容器
 */
@Slf4j
public class IndexStatusCache {

    // 索引状态缓存
    private static final ConcurrentHashMap<String, CachedIndexStatus> CACHE = new ConcurrentHashMap<>();

    // 默认缓存TTL（毫秒）- 20分钟
    private static final long DEFAULT_TTL = 1200_000;

    // 最大缓存条目数
    private static final int MAX_CACHE_SIZE = 100;

    // 缓存命中次数
    private static final AtomicInteger CACHE_HITS = new AtomicInteger(0);

    // 缓存未命中次数
    private static final AtomicInteger CACHE_MISSES = new AtomicInteger(0);
    
    /**
     * 缓存的索引状态对象
     * 包含索引是否存在、缓存时间戳和TTL
     * @Param exists 索引是否存在
     * @Param ttl 缓存有效时间（毫秒）
     * @Param timestamp 缓存创建时间戳
     * @return 索引状态缓存对象
     */
    public static class CachedIndexStatus {
        private final boolean exists;
        private final long timestamp;
        private final long ttl;
        
        public CachedIndexStatus(boolean exists, long ttl) {
            this.exists = exists;
            this.timestamp = System.currentTimeMillis();
            this.ttl = ttl;
        }
        
        /**
         * 检查缓存是否仍然有效
         */
        public boolean isValid() {
            return System.currentTimeMillis() - timestamp < ttl;
        }
        
        public boolean isExists() {
            return exists;
        }
    }
    
    /**
     * 获取索引状态（带缓存）
     * 如果缓存存在且有效，返回缓存值；否则返回false（表示未知状态，需要查询Redis）
     * 同时更新缓存命中/未命中统计信息
     */
    public static boolean get(String indexName) {
        CachedIndexStatus status = CACHE.get(indexName);
        if (status != null && status.isValid()) {
            CACHE_HITS.incrementAndGet();
            log.debug("索引状态缓存命中: {} = {}", indexName, status.isExists());
            return status.isExists();
        }
        CACHE_MISSES.incrementAndGet();
        return false;
    }
    
    /**
     * 设置索引状态缓存
     */
    public static void put(String indexName, boolean exists) {
        put(indexName, exists, DEFAULT_TTL);
    }
    
    /**
     * 设置索引状态缓存（指定TTL）
     */
    public static void put(String indexName, boolean exists, long ttl) {
        CACHE.put(indexName, new CachedIndexStatus(exists, ttl));
        log.debug("更新索引状态缓存: {} = {}, TTL = {}ms", indexName, exists, ttl);
        evictIfNeeded();
    }

    /**
     * 超过最大容量时驱逐最旧的过期条目
     * 先清理已过期的条目，如果仍然超限，则按时间戳移除最旧的一半条目，确保缓存不会无限增长
     * 日志记录驱逐操作，便于监控缓存行为
     */
    private static void evictIfNeeded() {
        if (CACHE.size() <= MAX_CACHE_SIZE) return;

        // 先清理已过期的条目
        CACHE.entrySet().removeIf(e -> !e.getValue().isValid());

        // 如果仍然超限，按时间戳移除最旧的一半
        if (CACHE.size() > MAX_CACHE_SIZE) {
            var sorted = CACHE.entrySet().stream()
                    .sorted((a, b) -> Long.compare(a.getValue().timestamp, b.getValue().timestamp))
                    .toList();
            int removeCount = CACHE.size() - MAX_CACHE_SIZE + 10;
            for (int i = 0; i < removeCount && i < sorted.size(); i++) {
                CACHE.remove(sorted.get(i).getKey());
            }
            log.info("缓存驱逐: 移除了 {} 个条目", removeCount);
        }
    }
    
    /**
     * 移除索引状态缓存
     */
    public static void remove(String indexName) {
        CACHE.remove(indexName);
        log.debug("移除索引状态缓存: {}", indexName);
    }
    
    /**
     * 强制刷新索引状态
     * 先移除旧缓存，再设置新状态，确保下一次查询会获取最新状态
     * 日志记录刷新操作，便于监控索引状态变化
     */
    public static void refresh(String indexName, boolean exists) {
        remove(indexName);
        put(indexName, exists);
        log.info("强制刷新索引状态: {} = {}", indexName, exists);
    }
    
    /**
     * 清空所有缓存
     * 日志记录清空操作，便于监控缓存行为和调试索引状态问题
     * 在系统启动、索引重建或调试过程中可以调用此方法，确保缓存状态干净，避免过期或错误的缓存影响系统行为
     */
    public static void clear() {
        int size = CACHE.size();
        CACHE.clear();
        log.info("清空索引状态缓存，共清除 {} 个条目", size);
    }
    
    /**
     * 获取缓存统计信息
     * 返回当前缓存大小、命中次数、未命中次数、命中率和总请求数等统计数据，便于监控缓存性能和效果
     */
    public static CacheStats getStats() {
        // 总请求数 = 命中次数 + 未命中次数
        int totalRequests = CACHE_HITS.get() + CACHE_MISSES.get();
        double hitRate = totalRequests > 0 ? (double) CACHE_HITS.get() / totalRequests : 0.0;
        
        return new CacheStats(
            CACHE.size(),
            CACHE_HITS.get(),
            CACHE_MISSES.get(),
            hitRate,
            totalRequests
        );
    }
    
    /**
     * 缓存统计信息
     * @Param cacheSize 当前缓存大小
     * @Param hits 缓存命中次数
     * @Param misses 缓存未命中次数
     * @Param hitRate 缓存命中率
     * @Param totalRequests 总请求次数
     * @return 缓存统计信息对象
     */
    public static class CacheStats {
        private final int cacheSize;
        private final int hits;
        private final int misses;
        private final double hitRate;
        private final int totalRequests;
        
        public CacheStats(int cacheSize, int hits, int misses, double hitRate, int totalRequests) {
            this.cacheSize = cacheSize;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
            this.totalRequests = totalRequests;
        }
        
        // getters...
        public int getCacheSize() { return cacheSize; }
        public int getHits() { return hits; }
        public int getMisses() { return misses; }
        public double getHitRate() { return hitRate; }
        public int getTotalRequests() { return totalRequests; }
        
        @Override
        public String toString() {
            return String.format("CacheStats{size=%d, hits=%d, misses=%d, hitRate=%.2f%%, total=%d}",
                cacheSize, hits, misses, hitRate * 100, totalRequests);
        }
    }
}