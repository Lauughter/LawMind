package com.lhs.lawmind.scheduler;

import com.lhs.lawmind.service.HotQuestionHandler;
import com.lhs.lawmind.service.SimilarQuestionMaintenanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 热点问题定时清理任务
 * 定期清理过期的热点缓存和低质量的相似问题记录
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class HotQuestionCleanupScheduler {
    
    private final HotQuestionHandler hotQuestionHandler;
    private final SimilarQuestionMaintenanceService similarQuestionMaintenanceService;
    
    /**
     * 每小时执行一次热点缓存清理
     * 清理过期的热点问题缓存
     */
    @Scheduled(cron = "0 0 * * * ?") // 每小时整点执行
    public void cleanupExpiredHotCache() {
        try {
            log.info("开始执行热点缓存清理任务");
            
            int cleanedCount = hotQuestionHandler.performCacheCleanup();
            
            log.info("热点缓存清理任务完成: 清理数量={}", cleanedCount);
            
        } catch (Exception e) {
            log.error("热点缓存清理任务执行失败", e);
        }
    }
    
    /**
     * 每天凌晨3点执行相似问题库清理
     * 清理访问次数过低的相似问题记录
     */
    @Scheduled(cron = "0 0 3 * * ?") // 每天凌晨3点执行
    public void cleanupLowQualitySimilarQuestions() {
        try {
            log.info("开始执行相似问题库清理任务");
            
            // 清理访问次数小于5的低质量问题
            int minVisitCount = 5;
            int cleanedCount = similarQuestionMaintenanceService.cleanupLowQualitySimilarQuestions(minVisitCount);
            
            log.info("相似问题库清理任务完成: 清理数量={}", cleanedCount);
            
        } catch (Exception e) {
            log.error("相似问题库清理任务执行失败", e);
        }
    }
    
    /**
     * 每天凌晨4点执行统计信息汇总
     * 汇总昨日的热点问题统计数据
     */
    @Scheduled(cron = "0 0 4 * * ?") // 每天凌晨4点执行
    public void summarizeDailyStatistics() {
        try {
            log.info("开始执行热点问题统计汇总任务");
            
            var stats = hotQuestionHandler.getHotQuestionStats();
            
            log.info("热点问题统计汇总完成: 总缓存数={}, 今日访问={}, 今日升级={}", 
                    stats.getTotalCacheCount(), stats.getTodayVisitCount(), stats.getUpgradedToday());
            
        } catch (Exception e) {
            log.error("热点问题统计汇总任务执行失败", e);
        }
    }
}