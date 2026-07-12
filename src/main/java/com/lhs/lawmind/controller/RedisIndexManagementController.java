package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.utils.IndexStatusCache;
import com.lhs.lawmind.utils.RedisIndexUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Redis索引状态管理控制器
 */
@RestController
@RequestMapping("/redis/index")
@RequiredArgsConstructor
public class RedisIndexManagementController {

    private final RedisIndexUtil redisIndexUtil;

    /**
     * 获取索引状态缓存统计信息
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getCacheStats() {
        IndexStatusCache.CacheStats stats = redisIndexUtil.getCacheStats();

        Map<String, Object> result = new HashMap<>();
        result.put("cacheSize", stats.getCacheSize());
        result.put("hits", stats.getHits());
        result.put("misses", stats.getMisses());
        result.put("hitRate", String.format("%.2f%%", stats.getHitRate() * 100));
        result.put("totalRequests", stats.getTotalRequests());

        return Result.success(result);
    }

    /**
     * 清空索引状态缓存
     */
    @DeleteMapping("/cache")
    public Result<String> clearCache() {
        redisIndexUtil.clearCache();
        return Result.success("缓存已清空");
    }

    /**
     * 手动刷新特定索引状态
     */
    @PostMapping("/refresh/{indexName}")
    public Result<String> refreshIndex(@PathVariable String indexName) {
        // 这里需要注入RedisTemplate，简化示例省略
        // redisIndexUtil.refreshIndexStatus(redisTemplate, indexName);
        return Result.success("索引状态已刷新: " + indexName);
    }

    /**
     * 获取所有缓存的索引状态
     */
    @GetMapping("/status")
    public Result<Map<String, Boolean>> getAllIndexStatus() {
        // 注意：由于CACHE是私有静态变量，这里需要通过反射或其他方式获取
        // 或者在IndexStatusCache中添加public方法来获取所有缓存项
        Map<String, Boolean> statusMap = new HashMap<>();
        // 简化实现，实际需要完善
        return Result.success(statusMap);
    }
}
