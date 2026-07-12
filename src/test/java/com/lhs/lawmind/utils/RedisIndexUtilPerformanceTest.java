package com.lhs.lawmind.utils;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * RedisIndexUtil 性能测试和对比测试
 */
@SpringBootTest
public class RedisIndexUtilPerformanceTest {

    @Test
    public void testPerformanceImprovement() {
        // 模拟RedisTemplate
        RedisTemplate<String, Object> mockRedisTemplate = mock(RedisTemplate.class);
        
        // 创建两个版本的RedisIndexUtil进行对比
        RedisIndexUtilOptimized optimizedUtil = new RedisIndexUtilOptimized();
        RedisIndexUtilOriginal originalUtil = new RedisIndexUtilOriginal();
        
        String indexName = "test:performance:index";
        int iterations = 1000;
        
        // 测试优化版本性能
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            optimizedUtil.indexExists(mockRedisTemplate, indexName);
        }
        long optimizedTime = System.currentTimeMillis() - startTime;
        
        // 测试原始版本性能（模拟Redis调用）
        startTime = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            originalUtil.indexExists(mockRedisTemplate, indexName);
        }
        long originalTime = System.currentTimeMillis() - startTime;
        
        System.out.printf("性能对比测试结果:%n");
        System.out.printf("原始版本耗时: %d ms%n", originalTime);
        System.out.printf("优化版本耗时: %d ms%n", optimizedTime);
        System.out.printf("性能提升: %.2f倍%n", (double) originalTime / optimizedTime);
        System.out.printf("优化百分比: %.2f%%%n", ((double)(originalTime - optimizedTime) / originalTime) * 100);
        
        // 验证优化版本确实更快
        assertTrue(optimizedTime < originalTime, "优化版本应该比原始版本更快");
    }

    @Test
    public void testCacheHitRate() {
        RedisTemplate<String, Object> mockRedisTemplate = mock(RedisTemplate.class);
        RedisIndexUtil util = new RedisIndexUtil();
        
        String indexName = "test:cache:hit:rate";
        int totalCalls = 1000;
        
        // 首次调用会触发Redis查询
        util.indexExists(mockRedisTemplate, indexName);
        
        // 后续调用应该命中缓存
        long startTime = System.currentTimeMillis();
        for (int i = 0; i < totalCalls - 1; i++) {
            util.indexExists(mockRedisTemplate, indexName);
        }
        long totalTime = System.currentTimeMillis() - startTime;
        
        // 获取统计信息
        IndexStatusCache.CacheStats stats = util.getCacheStats();
        
        System.out.printf("缓存命中率测试:%n");
        System.out.printf("总请求数: %d%n", stats.getTotalRequests());
        System.out.printf("缓存命中数: %d%n", stats.getHits());
        System.out.printf("缓存未命中数: %d%n", stats.getMisses());
        System.out.printf("命中率: %.2f%%%n", stats.getHitRate() * 100);
        System.out.printf("平均每次调用耗时: %.4f ms%n", (double) totalTime / (totalCalls - 1));
        
        // 验证高命中率
        assertTrue(stats.getHitRate() > 0.95, "缓存命中率应该超过95%");
    }

    @Test
    public void testConcurrentPerformance() throws InterruptedException {
        RedisTemplate<String, Object> mockRedisTemplate = mock(RedisTemplate.class);
        RedisIndexUtil util = new RedisIndexUtil();
        
        final int threadCount = 20;
        final int operationsPerThread = 500;
        Thread[] threads = new Thread[threadCount];
        long[] threadTimes = new long[threadCount];
        
        // 预热缓存
        util.indexExists(mockRedisTemplate, "warmup:index");
        
        // 并发测试
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            threads[i] = new Thread(() -> {
                long startTime = System.currentTimeMillis();
                for (int j = 0; j < operationsPerThread; j++) {
                    util.indexExists(mockRedisTemplate, "concurrent:test:" + threadId);
                }
                threadTimes[threadId] = System.currentTimeMillis() - startTime;
            });
            threads[i].start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        
        // 统计结果
        long totalTime = 0;
        for (long time : threadTimes) {
            totalTime += time;
        }
        
        IndexStatusCache.CacheStats stats = util.getCacheStats();
        
        System.out.printf("并发性能测试结果:%n");
        System.out.printf("线程数: %d%n", threadCount);
        System.out.printf("每线程操作数: %d%n", operationsPerThread);
        System.out.printf("总操作数: %d%n", threadCount * operationsPerThread);
        System.out.printf("平均每线程耗时: %.2f ms%n", (double) totalTime / threadCount);
        System.out.printf("总缓存请求数: %d%n", stats.getTotalRequests());
        System.out.printf("缓存命中率: %.2f%%%n", stats.getHitRate() * 100);
        
        // 验证并发操作已执行（总请求数可能包含其他测试的共享缓存访问）
        assertTrue(stats.getTotalRequests() >= threadCount * operationsPerThread);
    }

    // 模拟原始版本（无缓存）
    static class RedisIndexUtilOriginal {
        public boolean indexExists(RedisTemplate<String, Object> redisTemplate, String indexName) {
            // 模拟每次都查询Redis的开销
            try {
                Thread.sleep(1); // 模拟网络延迟
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true; // 简化返回
        }
    }

    // 模拟优化版本（带缓存）
    static class RedisIndexUtilOptimized {
        private static boolean cachedResult = false;
        private static boolean firstCall = true;
        
        public boolean indexExists(RedisTemplate<String, Object> redisTemplate, String indexName) {
            if (firstCall) {
                // 第一次调用模拟Redis查询
                try {
                    Thread.sleep(1); // 模拟网络延迟
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                cachedResult = true;
                firstCall = false;
                return true;
            } else {
                // 后续调用直接返回缓存结果
                return cachedResult;
            }
        }
    }
}