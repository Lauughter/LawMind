package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.service.VisitStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

/**
 * 访问统计服务实现类
 * 使用Redis实现高效的访问统计
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VisitStatisticsServiceImpl implements VisitStatisticsService {
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final RagConfig ragConfig;
    
    // Redis键前缀
    private static final String VISIT_COUNT_PREFIX = "visit:count:";
    private static final String VISIT_WINDOW_PREFIX = "visit:window:";
    
    @Override
    public int recordVisit(String questionMd5) {
        if (!StringUtils.hasText(questionMd5)) {
            return 0;
        }
        
        try {
            String key = VISIT_COUNT_PREFIX + questionMd5;
            
            // 增加访问计数
            Long count = redisTemplate.opsForValue().increment(key, 1);
            
            // 设置过期时间（30天）
            if (count != null && count == 1) {
                redisTemplate.expire(key, 30, TimeUnit.DAYS);
            }
            
            log.debug("记录问题访问: questionMd5={}, count={}", questionMd5, count);
            return count != null ? count.intValue() : 0;
            
        } catch (Exception e) {
            log.error("记录问题访问失败: questionMd5={}", questionMd5, e);
            return 0;
        }
    }
    
    @Override
    public int getVisitCount(String questionMd5) {
        if (!StringUtils.hasText(questionMd5)) {
            return 0;
        }
        
        try {
            String key = VISIT_COUNT_PREFIX + questionMd5;
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? Integer.parseInt(value.toString()) : 0;
            
        } catch (Exception e) {
            log.error("获取访问次数失败: questionMd5={}", questionMd5, e);
            return 0;
        }
    }
    
    @Override
    public boolean isHotThresholdReached(String questionMd5, int timeWindow, int threshold) {
        if (!StringUtils.hasText(questionMd5)) {
            return false;
        }
        
        try {
            int visitCount = getVisitCountInTimeWindow(questionMd5, timeWindow);
            boolean reached = visitCount >= threshold;
            
            if (reached) {
                log.info("问题达到热点阈值: questionMd5={}, timeWindow={}分钟, count={}, threshold={}", 
                        questionMd5, timeWindow, visitCount, threshold);
            }
            
            return reached;
            
        } catch (Exception e) {
            log.error("检查热点阈值失败: questionMd5={}", questionMd5, e);
            return false;
        }
    }
    
    @Override
    public int getVisitCountInTimeWindow(String questionMd5, int timeWindowMinutes) {
        if (!StringUtils.hasText(questionMd5) || timeWindowMinutes <= 0) {
            return 0;
        }
        
        try {
            // 使用滑动时间窗口统计
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - (timeWindowMinutes * 60 * 1000L);
            
            String key = VISIT_WINDOW_PREFIX + questionMd5;
            
            // 清理过期的访问记录
            redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);
            
            // 统计时间窗口内的访问次数
            Long count = redisTemplate.opsForZSet().zCard(key);
            
            // 记录当前访问（使用当前时间戳作为score）
            redisTemplate.opsForZSet().add(key, String.valueOf(currentTime), currentTime);
            
            // 设置过期时间
            redisTemplate.expire(key, timeWindowMinutes * 2, TimeUnit.MINUTES);
            
            return count != null ? count.intValue() : 0;
            
        } catch (Exception e) {
            log.error("获取时间窗口访问统计失败: questionMd5={}, timeWindow={}", 
                    questionMd5, timeWindowMinutes, e);
            return 0;
        }
    }
    
    @Override
    public boolean resetVisitStatistics(String questionMd5) {
        if (!StringUtils.hasText(questionMd5)) {
            return false;
        }
        
        try {
            // 删除访问计数
            String countKey = VISIT_COUNT_PREFIX + questionMd5;
            redisTemplate.delete(countKey);
            
            // 删除时间窗口统计
            String windowKey = VISIT_WINDOW_PREFIX + questionMd5;
            redisTemplate.delete(windowKey);
            
            log.info("重置问题访问统计: questionMd5={}", questionMd5);
            return true;
            
        } catch (Exception e) {
            log.error("重置访问统计失败: questionMd5={}", questionMd5, e);
            return false;
        }
    }
}