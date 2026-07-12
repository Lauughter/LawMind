package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.HotQuestion;
import com.lhs.lawmind.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 热点问题处理器实现类
 * 协调各个服务完成完整的热点问题处理流程
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HotQuestionHandlerImpl implements HotQuestionHandler {
    
    private final HotQuestionCacheService hotQuestionCacheService;
    private final VisitStatisticsService visitStatisticsService;
    private final RagConfig ragConfig;
    
    // 统计相关键名
    private static final String TODAY_VISIT_COUNT_KEY = "hot:stats:today:visit";
    private static final String TODAY_UPGRADED_COUNT_KEY = "hot:stats:today:upgraded";
    
    @Override
    public HotQuestion handleHotQuestionQuery(String questionMd5) {
        if (questionMd5 == null || questionMd5.isEmpty()) {
            return null;
        }
        
        try {
            // 查询热点缓存
            HotQuestion hotQuestion = hotQuestionCacheService.getHotQuestionByMd5(questionMd5);
            
            if (hotQuestion != null) {
                // 命中热点缓存，更新访问统计
                hotQuestionCacheService.updateVisitCount(questionMd5);
                recordDailyVisit();
                log.info("热点问题查询命中: questionMd5={}", questionMd5);
            }
            
            return hotQuestion;
            
        } catch (Exception e) {
            log.error("处理热点问题查询失败: questionMd5={}", questionMd5, e);
            return null;
        }
    }
    
    @Override
    public boolean handleVisitAndUpgrade(String questionMd5, String originalQuestion, 
                                       String answer, String knowledgeIds) {
        if (questionMd5 == null || questionMd5.isEmpty()) {
            return false;
        }
        
        try {
            // 1. 记录访问
            int visitCount = visitStatisticsService.recordVisit(questionMd5);
            
            // 2. 检查是否需要升级为热点
            boolean shouldUpgrade = checkAndUpgradeHotQuestion(
                questionMd5, originalQuestion, answer, knowledgeIds);
            
            log.debug("访问处理完成: questionMd5={}, visitCount={}, upgraded={}", 
                     questionMd5, visitCount, shouldUpgrade);
            
            return true;
            
        } catch (Exception e) {
            log.error("处理访问和升级失败: questionMd5={}", questionMd5, e);
            return false;
        }
    }
    
    @Override
    public boolean checkAndUpgradeHotQuestion(String questionMd5, String originalQuestion, 
                                            String answer, String knowledgeIds) {
        if (questionMd5 == null || questionMd5.isEmpty() || answer == null || answer.isEmpty()) {
            return false;
        }
        
        try {
            // 检查不同时间窗口的访问阈值
            boolean isHot5Min = visitStatisticsService.isHotThresholdReached(
                questionMd5, 5, ragConfig.getHotThreshold5Minutes());
            
            boolean isHot1Hour = visitStatisticsService.isHotThresholdReached(
                questionMd5, 60, ragConfig.getHotThreshold1Hour());
            
            boolean isHot1Day = visitStatisticsService.isHotThresholdReached(
                questionMd5, 1440, ragConfig.getHotThreshold1Day());
            
            int ttlDays = ragConfig.getHotCacheInitialTtlDays();
            
            // 根据访问频率确定不同的TTL
            if (isHot1Day) {
                ttlDays = ragConfig.getHotCacheInitialTtlDays(); // 30天
                log.info("检测到大热点问题（1天）: questionMd5={}", questionMd5);
            } else if (isHot1Hour) {
                ttlDays = 7; // 7天
                log.info("检测到中热点问题（1小时）: questionMd5={}", questionMd5);
            } else if (isHot5Min) {
                ttlDays = 1; // 1天
                log.info("检测到小热点问题（5分钟）: questionMd5={}", questionMd5);
            } else {
                return false; // 未达到任何阈值
            }
            
            // 升级为热点缓存
            boolean upgraded = hotQuestionCacheService.upgradeToHotCache(
                questionMd5, originalQuestion, answer, knowledgeIds, ttlDays);
            
            if (upgraded) {
                recordDailyUpgrade();
                log.info("热点问题升级成功: questionMd5={}, ttlDays={}", questionMd5, ttlDays);
            }
            
            return upgraded;
            
        } catch (Exception e) {
            log.error("检查并升级热点问题失败: questionMd5={}", questionMd5, e);
            return false;
        }
    }
    
    @Override
    public String getHotAnswerIfExist(String questionMd5) {
        if (questionMd5 == null || questionMd5.isEmpty()) {
            return null;
        }
        
        try {
            return hotQuestionCacheService.getHotAnswer(questionMd5);
        } catch (Exception e) {
            log.error("获取热点答案失败: questionMd5={}", questionMd5, e);
            return null;
        }
    }
    
    @Override
    public int performCacheCleanup() {
        try {
            int cleanedCount = hotQuestionCacheService.cleanupExpiredCache();
            
            if (cleanedCount > 0) {
                log.info("热点缓存清理完成: 清理数量={}", cleanedCount);
            }
            
            return cleanedCount;
            
        } catch (Exception e) {
            log.error("执行缓存清理失败", e);
            return 0;
        }
    }
    
    @Override
    public HotQuestionStats getHotQuestionStats() {
        HotQuestionStats stats = new HotQuestionStats();
        
        try {
            stats.setTotalCacheCount(hotQuestionCacheService.getCacheCount());
            stats.setTodayVisitCount(getTodayVisitCount());
            stats.setUpgradedToday(getTodayUpgradedCount());
            stats.setCleanedUpCount(0); // 这个需要在清理时更新
            
        } catch (Exception e) {
            log.error("获取热点统计信息失败", e);
        }
        
        return stats;
    }
    
    /**
     * 记录今日访问次数
     */
    private void recordDailyVisit() {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String key = TODAY_VISIT_COUNT_KEY + ":" + today;
            // 这里可以使用Redis或其他存储记录每日统计数据
        } catch (Exception e) {
            log.warn("记录每日访问统计失败", e);
        }
    }
    
    /**
     * 记录今日升级次数
     */
    private void recordDailyUpgrade() {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String key = TODAY_UPGRADED_COUNT_KEY + ":" + today;
            // 这里可以使用Redis或其他存储记录每日统计数据
        } catch (Exception e) {
            log.warn("记录每日升级统计失败", e);
        }
    }
    
    /**
     * 获取今日访问次数
     */
    private int getTodayVisitCount() {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String key = TODAY_VISIT_COUNT_KEY + ":" + today;
            // 从存储中获取统计数据
            return 0;
        } catch (Exception e) {
            log.warn("获取今日访问统计失败", e);
            return 0;
        }
    }
    
    /**
     * 获取今日升级次数
     */
    private int getTodayUpgradedCount() {
        try {
            String today = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
            String key = TODAY_UPGRADED_COUNT_KEY + ":" + today;
            // 从存储中获取统计数据
            return 0;
        } catch (Exception e) {
            log.warn("获取今日升级统计失败", e);
            return 0;
        }
    }
}