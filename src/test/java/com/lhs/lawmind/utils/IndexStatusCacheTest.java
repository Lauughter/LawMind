package com.lhs.lawmind.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IndexStatusCache 工具类单元测试
 */
@SpringBootTest
public class IndexStatusCacheTest {

    @BeforeEach
    public void setUp() {
        // 每个测试前清空缓存
        IndexStatusCache.clear();
    }

    @Test
    public void testPutAndGet() {
        // 测试基本的put和get功能
        String indexName = "test:index:1";
        IndexStatusCache.put(indexName, true);
        
        assertTrue(IndexStatusCache.get(indexName));
        assertFalse(IndexStatusCache.get("nonexistent:index"));
    }

    @Test
    public void testCacheExpiration() throws InterruptedException {
        // 测试缓存过期功能
        String indexName = "test:index:2";
        long shortTtl = 50; // 50毫秒
        
        IndexStatusCache.put(indexName, true, shortTtl);
        assertTrue(IndexStatusCache.get(indexName));
        
        // 等待缓存过期
        Thread.sleep(shortTtl + 10);
        assertFalse(IndexStatusCache.get(indexName));
    }

    @Test
    public void testRefreshFunction() {
        // 测试强制刷新功能
        String indexName = "test:index:3";
        
        IndexStatusCache.put(indexName, false);
        assertFalse(IndexStatusCache.get(indexName));
        
        IndexStatusCache.refresh(indexName, true);
        assertTrue(IndexStatusCache.get(indexName));
    }

    @Test
    public void testRemoveFunction() {
        // 测试移除功能
        String indexName = "test:index:4";
        
        IndexStatusCache.put(indexName, true);
        assertTrue(IndexStatusCache.get(indexName));
        
        IndexStatusCache.remove(indexName);
        assertFalse(IndexStatusCache.get(indexName));
    }

    @Test
    public void testClearFunction() {
        // 测试清空功能
        IndexStatusCache.put("test:index:5", true);
        IndexStatusCache.put("test:index:6", false);
        IndexStatusCache.put("test:index:7", true);

        assertTrue(IndexStatusCache.getStats().getCacheSize() >= 3);

        IndexStatusCache.clear();
        assertEquals(0, IndexStatusCache.getStats().getCacheSize());
    }

    @Test
    public void testCacheStats() {
        // 测试统计功能
        String index1 = "test:index:8";
        String index2 = "test:index:9";

        // 记录初始状态（可能包含其他测试的残留数据）
        IndexStatusCache.CacheStats initialStats = IndexStatusCache.getStats();
        long initialHits = initialStats.getHits();
        long initialMisses = initialStats.getMisses();

        // 缓存未命中测试
        assertFalse(IndexStatusCache.get(index1));
        IndexStatusCache.CacheStats missStats = IndexStatusCache.getStats();
        assertEquals(initialMisses + 1, missStats.getMisses());
        assertEquals(initialHits, missStats.getHits());

        // 缓存命中测试
        IndexStatusCache.put(index1, true);
        assertTrue(IndexStatusCache.get(index1));
        IndexStatusCache.CacheStats hitStats = IndexStatusCache.getStats();
        assertEquals(initialHits + 1, hitStats.getHits());
    }

    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // 测试并发访问安全性
        final int threadCount = 10;
        final int operationsPerThread = 100;
        Thread[] threads = new Thread[threadCount];

        // 记录并发测试前的请求总数（可能包含其他测试的残留数据）
        long beforeTotalRequests = IndexStatusCache.getStats().getTotalRequests();

        // 启动多个线程同时操作缓存
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String indexName = "concurrent:test:" + threadId + ":" + j;
                    IndexStatusCache.put(indexName, j % 2 == 0);
                    IndexStatusCache.get(indexName);
                }
            });
            threads[i].start();
        }

        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }

        // 验证并发操作已执行
        IndexStatusCache.CacheStats finalStats = IndexStatusCache.getStats();
        assertTrue(finalStats.getTotalRequests() >= beforeTotalRequests + threadCount * operationsPerThread);
        assertTrue(finalStats.getCacheSize() > 0);
    }

    @Test
    public void testCachedIndexStatusObject() {
        // 测试缓存状态对象的功能
        long ttl = 1000;
        IndexStatusCache.CachedIndexStatus status = new IndexStatusCache.CachedIndexStatus(true, ttl);
        
        assertTrue(status.isExists());
        assertTrue(status.isValid());
        
        // 等待过期
        try {
            Thread.sleep(ttl + 100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        assertFalse(status.isValid());
    }

    @Test
    public void testCacheStatsToString() {
        // 测试统计信息toString方法
        IndexStatusCache.CacheStats stats = new IndexStatusCache.CacheStats(5, 100, 50, 0.6667, 150);
        String statsString = stats.toString();
        
        assertNotNull(statsString);
        assertTrue(statsString.contains("size=5"));
        assertTrue(statsString.contains("hits=100"));
        assertTrue(statsString.contains("misses=50"));
        assertTrue(statsString.contains("hitRate=66.67%"));
    }
}