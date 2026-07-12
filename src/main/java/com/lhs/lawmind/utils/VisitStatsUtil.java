package com.lhs.lawmind.utils;

import com.lhs.lawmind.config.RagConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 访问统计工具类
 * 使用 Lua 脚本实现原子性的访问计数 + 过期时间设置
 *
 * <p>使用 StringRedisTemplate 确保值以纯整数形式存储，避免
 * GenericJackson2JsonRedisSerializer 导致的 JSON 序列化问题。</p>
 */
@Slf4j
@Component
public class VisitStatsUtil {

    private final StringRedisTemplate redisTemplate;
    private final RagConfig ragConfig;
    private final DefaultRedisScript<List> incrScript;

    // Lua 脚本：原子性地增加访问计数，并设置过期时间
    // 使用 pcall 防御非整数 key 值
    private static final String INCR_SCRIPT =
            "local function safe_incr(key) " +
            "  local ok, val = pcall(redis.call, 'INCR', key) " +
            "  if ok then return val end " +
            "  redis.call('DEL', key) " +
            "  return redis.call('INCR', key) " +
            "end " +
            "local count = safe_incr(KEYS[1]) " +
            "local v5 = safe_incr(KEYS[2]) " +
            "local v1h = safe_incr(KEYS[3]) " +
            "local v1d = safe_incr(KEYS[4]) " +
            "redis.call('EXPIRE', KEYS[2], ARGV[1]) " +
            "redis.call('EXPIRE', KEYS[3], ARGV[2]) " +
            "redis.call('EXPIRE', KEYS[4], ARGV[3]) " +
            "return {count, v5, v1h, v1d}";

    public VisitStatsUtil(StringRedisTemplate redisTemplate, RagConfig ragConfig) {
        this.redisTemplate = redisTemplate;
        this.ragConfig = ragConfig;
        this.incrScript = new DefaultRedisScript<>(INCR_SCRIPT, List.class);
    }

    /**
     * 原子性地增加问题访问次数
     * <p>使用 Lua 脚本同时增加总访问次数、5分钟内、1小时内和1天内的访问次数</p>
     * @param md5 问题的MD5值
     */
    public void incrementVisitCount(String md5) {
        try {
            List<String> keys = List.of(
                    "visit:count:" + md5,
                    "visit:5min:" + md5,
                    "visit:1hour:" + md5,
                    "visit:1day:" + md5
            );
            redisTemplate.execute(incrScript, keys, "300", "3600", "86400");
        } catch (Exception e) {
            log.error("增加访问次数失败: md5={}, error={}", md5, e.getMessage());
        }
    }

    /**
     * 获取访问次数统计
     * @param md5 问题的MD5值
     * @return 访问统计对象
     */
    public VisitStats getVisitStats(String md5) {
        try {
            String v5 = redisTemplate.opsForValue().get("visit:5min:" + md5);
            String v1h = redisTemplate.opsForValue().get("visit:1hour:" + md5);
            String v1d = redisTemplate.opsForValue().get("visit:1day:" + md5);

            return new VisitStats(
                    v5 != null ? Integer.parseInt(v5) : 0,
                    v1h != null ? Integer.parseInt(v1h) : 0,
                    v1d != null ? Integer.parseInt(v1d) : 0
            );
        } catch (Exception e) {
            log.error("获取访问统计失败: md5={}, error={}", md5, e.getMessage());
            return new VisitStats(0, 0, 0);
        }
    }

    /**
     * 判断是否达到热点阈值
     */
    public boolean isHotQuestion(VisitStats stats) {
        return stats.getCount5Minutes() >= ragConfig.getHotThreshold5Minutes()
                || stats.getCount1Hour() >= ragConfig.getHotThreshold1Hour()
                || stats.getCount1Day() >= ragConfig.getHotThreshold1Day();
    }

    public static class VisitStats {
        private final int count5Minutes;
        private final int count1Hour;
        private final int count1Day;

        public VisitStats(int count5Minutes, int count1Hour, int count1Day) {
            this.count5Minutes = count5Minutes;
            this.count1Hour = count1Hour;
            this.count1Day = count1Day;
        }

        public int getCount5Minutes() { return count5Minutes; }
        public int getCount1Hour() { return count1Hour; }
        public int getCount1Day() { return count1Day; }
    }
}
