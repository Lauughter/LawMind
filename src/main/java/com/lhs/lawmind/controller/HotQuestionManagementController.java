package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.service.HotQuestionHandler;
import com.lhs.lawmind.service.HotQuestionCacheService;
import com.lhs.lawmind.service.SimilarQuestionMaintenanceService;
import com.lhs.lawmind.service.VisitStatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 热点问题管理控制器
 * 提供热点问题相关的管理接口和监控功能
 */
@RestController
@RequestMapping("/hot-question")
@RequiredArgsConstructor
@Slf4j
public class HotQuestionManagementController {
    
    private final HotQuestionHandler hotQuestionHandler;
    private final HotQuestionCacheService hotQuestionCacheService;
    private final SimilarQuestionMaintenanceService similarQuestionMaintenanceService;
    private final VisitStatisticsService visitStatisticsService;
    
    @GetMapping("/stats")
    public Result<Map<String, Object>> getHotQuestionStats() {
        var handlerStats = hotQuestionHandler.getHotQuestionStats();
        var stats = new HashMap<String, Object>();
        
        stats.put("totalCacheCount", handlerStats.getTotalCacheCount());
        stats.put("todayVisitCount", handlerStats.getTodayVisitCount());
        stats.put("todayUpgradedCount", handlerStats.getUpgradedToday());
        stats.put("similarQuestionCount", similarQuestionMaintenanceService.getSimilarQuestionCount());
        
        return Result.success(stats);
    }
    
    @PostMapping("/cleanup")
    public Result<String> performManualCleanup() {
        int cleanedCount = hotQuestionHandler.performCacheCleanup();
        return Result.success("清理完成，共清理 " + cleanedCount + " 条过期缓存");
    }
    
    @GetMapping("/visit-count/{questionMd5}")
    public Result<Integer> getVisitCount(@PathVariable String questionMd5) {
        int visitCount = visitStatisticsService.getVisitCount(questionMd5);
        return Result.success(visitCount);
    }
    
    @PostMapping("/reset-stats/{questionMd5}")
    public Result<String> resetVisitStatistics(@PathVariable String questionMd5) {
        boolean success = visitStatisticsService.resetVisitStatistics(questionMd5);
        if (success) {
            return Result.success("重置成功");
        } else {
            return Result.error("重置失败");
        }
    }
    
    @GetMapping("/similar-question/count")
    public Result<Integer> getSimilarQuestionCount() {
        int count = similarQuestionMaintenanceService.getSimilarQuestionCount();
        return Result.success(count);
    }
    
    @PostMapping("/similar-question/cleanup")
    public Result<String> cleanupLowQualitySimilarQuestions(@RequestParam(defaultValue = "5") int minVisitCount) {
        int cleanedCount = similarQuestionMaintenanceService.cleanupLowQualitySimilarQuestions(minVisitCount);
        return Result.success("清理完成，共清理 " + cleanedCount + " 条低质量记录");
    }
    
    @GetMapping("/cache/status")
    public Result<Map<String, Object>> getCacheStatus() {
        var status = new HashMap<String, Object>();
        status.put("cacheCount", hotQuestionCacheService.getCacheCount());
        status.put("isEnabled", true);
        status.put("nextCleanupTime", "每小时执行");
        
        return Result.success(status);
    }
}
