package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.entity.EvalReportRecord;
import com.lhs.lawmind.mapper.EvalReportRecordMapper;
import com.lhs.lawmind.service.RagMetricsService;
import com.lhs.lawmind.service.impl.MetricsPersistenceScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * RAG 指标仪表盘控制器
 * 管理员查看缓存命中率、来源占比、延迟分布等运营数据
 */
@Slf4j
@RestController
@RequestMapping("/admin/metrics")
@RequiredArgsConstructor
public class RagMetricsController {

    private final RagMetricsService ragMetricsService;
    private final EvalReportRecordMapper evalReportRecordMapper;
    private final MetricsPersistenceScheduler persistenceScheduler;

    private boolean isAdmin() {
        return RequestContext.isAdmin();
    }

    /**
     * 今日概览
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> today() {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        return Result.success(ragMetricsService.getTodayOverview());
    }

    /**
     * 最近 N 天趋势
     */
    @GetMapping("/trend")
    public Result<Map<String, Object>> trend(@RequestParam(defaultValue = "7") int days) {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        if (days < 1 || days > 90) {
            return Result.error(400, "天数范围为 1-90");
        }
        return Result.success(ragMetricsService.getTrend(days));
    }

    /**
     * 质量指标概览 — 各来源点赞率、反馈原因分布、LLM兜底率
     */
    @GetMapping("/quality/today")
    public Result<Map<String, Object>> qualityToday() {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        return Result.success(ragMetricsService.getQualityOverview());
    }

    /**
     * 质量趋势 — 最近 N 天的质量指标变化
     */
    @GetMapping("/quality/trend")
    public Result<Map<String, Object>> qualityTrend(@RequestParam(defaultValue = "7") int days) {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        if (days < 1 || days > 90) {
            return Result.error(400, "天数范围为 1-90");
        }
        return Result.success(ragMetricsService.getQualityTrend(days));
    }

    /**
     * 评估报告历史列表
     */
    @GetMapping("/eval/reports")
    public Result<List<EvalReportRecord>> evalReports(@RequestParam(defaultValue = "20") int limit) {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        return Result.success(evalReportRecordMapper.selectRecent(limit));
    }

    /**
     * 手动将 Redis 指标持久化到 MySQL（指定日期）
     */
    @PostMapping("/persist/{dateStr}")
    public Result<Map<String, Object>> persistDate(@PathVariable String dateStr) {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        try {
            LocalDate date = LocalDate.parse(dateStr);
            Map<String, Object> res = persistenceScheduler.persistDate(date);
            return Result.success(res);
        } catch (Exception e) {
            return Result.error(400, "日期格式错误，请使用 yyyy-MM-dd: " + e.getMessage());
        }
    }

    /**
     * 补漏：持久化最近 N 天内缺失的指标数据
     */
    @PostMapping("/persist/catchup")
    public Result<Map<String, Object>> catchUp(@RequestParam(defaultValue = "14") int days) {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        if (days < 1 || days > 14) {
            return Result.error(400, "回溯天数范围为 1-14（受 Redis TTL 限制）");
        }
        return Result.success(persistenceScheduler.catchUpMissedDays(days));
    }
}
