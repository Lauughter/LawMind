package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.Result;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Redis 信息查询控制器
 */
@RestController
@RequestMapping("/api/redis")
public class RedisInfoController {

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisInfoController(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 获取 Redis Search 模块版本信息
     */
    @GetMapping("/search-version")
    public Result<Map<String, Object>> getRedisSearchVersion() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Object modules = redisTemplate.execute((RedisConnection connection) -> {
                // 执行 MODULE LIST 命令获取已加载的模块列表
                byte[][] args = new byte[][]{"MODULE LIST".getBytes()};
                return connection.execute("MODULE LIST", args);
            });
            
            if (modules != null) {
                result.put("success", true);
                result.put("modules", modules);
                result.put("message", "Redis 模块列表获取成功");
            } else {
                result.put("success", false);
                result.put("message", "未找到 Redis 模块信息");
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("message", "获取 Redis 模块信息失败：" + e.getMessage());
        }
        
        return Result.success(result);
    }

    /**
     * 获取 Redis 服务器信息
     */
    @GetMapping("/info")
    public Result<Map<String, String>> getRedisInfo() {
        Map<String, String> result = new HashMap<>();
        
        try {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            try {
                Properties info = connection.info();
                
                if (info != null) {
                    result.put("redis_version", info.getProperty("redis_version", "unknown"));
                    result.put("redis_mode", info.getProperty("redis_mode", "unknown"));
                    result.put("os", info.getProperty("os", "unknown"));
                    result.put("arch_bits", info.getProperty("arch_bits", "unknown"));
                    result.put("tcp_port", info.getProperty("tcp_port", "unknown"));
                    result.put("uptime_in_seconds", info.getProperty("uptime_in_seconds", "unknown"));
                    result.put("message", "Redis 服务器信息获取成功");
                }
            } finally {
                if (connection != null) {
                    try {
                        connection.close();
                    } catch (Exception e) {
                        // 忽略关闭异常
                    }
                }
            }
        } catch (Exception e) {
            result.put("error", e.getMessage());
            result.put("message", "获取 Redis 信息失败：" + e.getMessage());
        }
        
        return Result.success(result);
    }
}
