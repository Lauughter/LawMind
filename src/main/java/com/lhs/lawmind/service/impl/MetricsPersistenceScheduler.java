package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.entity.RagMetricsDaily;
import com.lhs.lawmind.mapper.RagMetricsDailyMapper;
import com.lhs.lawmind.service.RagMetricsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 每日凌晨将前一天的 Redis 指标快照写入 MySQL，保留长期历史趋势
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsPersistenceScheduler {

    private final RagMetricsService ragMetricsService;
    private final RagMetricsDailyMapper ragMetricsDailyMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 每天凌晨 1:07 执行（避开整点高峰）
     */
    @Scheduled(cron = "0 7 1 * * *")
    public void persistYesterdayMetrics() {
        persistDate(LocalDate.now().minusDays(1));
    }

    /**
     * 手动持久化指定日期的指标数据
     *
     * @return 持久化结果摘要，包含日期和请求总数；Redis 无数据时返回 null
     */
    public Map<String, Object> persistDate(LocalDate date) {
        log.info("[指标持久化] 开始同步 {} 的指标数据到 MySQL", date);
        try {
            Map<String, Object> overview = ragMetricsService.getDayOverview(date);
            long total = overview.containsKey("total") ? ((Number) overview.get("total")).longValue() : 0;
            RagMetricsDaily entity = buildEntity(date, overview);
            ragMetricsDailyMapper.upsert(entity);
            log.info("[指标持久化] 同步完成: date={} totalRequests={}", date, entity.getTotalRequests());
            return Map.of("date", date.toString(), "totalRequests", total, "success", true);
        } catch (Exception e) {
            log.error("[指标持久化] 同步失败: date={}", date, e);
            return Map.of("date", date.toString(), "totalRequests", 0, "success", false,
                    "error", e.getMessage() != null ? e.getMessage() : "未知错误");
        }
    }

    /**
     * 补漏：持久化最近 maxDays 天内 Redis 有数据但 MySQL 缺失的日期
     *
     * @param maxDays 最多回溯天数（受 Redis 14 天 TTL 限制）
     * @return 补漏结果
     */
    public Map<String, Object> catchUpMissedDays(int maxDays) {
        int persisted = 0;
        int skipped = 0;
        for (int i = 1; i <= Math.min(maxDays, 14); i++) {
            LocalDate date = LocalDate.now().minusDays(i);
            // 检查 MySQL 是否已有该日数据
            RagMetricsDaily existing = ragMetricsDailyMapper.selectByDate(date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
            if (existing != null && existing.getTotalRequests() != null && existing.getTotalRequests() > 0) {
                skipped++;
                continue;
            }
            Map<String, Object> result = persistDate(date);
            if (Boolean.TRUE.equals(result.get("success")) && ((Number) result.get("totalRequests")).longValue() > 0) {
                persisted++;
            } else {
                skipped++;
            }
        }
        log.info("[指标持久化] 补漏完成: persisted={} skipped={}", persisted, skipped);
        return Map.of("persisted", persisted, "skipped", skipped, "maxDays", maxDays);
    }

    @PostConstruct
    void startupCatchUp() {
        try {
            catchUpMissedDays(14);
        } catch (Exception e) {
            log.warn("[指标持久化] 启动补漏失败: {}", e.getMessage());
        }
    }

    /**
     * 从 Redis 概览数据构建实体
     */
    @SuppressWarnings("unchecked")
    private RagMetricsDaily buildEntity(LocalDate date, Map<String, Object> overview) {
        RagMetricsDaily e = new RagMetricsDaily();
        e.setMetricDate(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant()));

        e.setTotalRequests(getLong(overview, "total"));
        e.setAvgLatencyMs(getLong(overview, "avgLatencyMs"));
        e.setP50LatencyMs(getLong(overview, "p50LatencyMs"));
        e.setP95LatencyMs(getLong(overview, "p95LatencyMs"));
        e.setTotalLikes(getLong(overview, "feedbackUp"));
        e.setTotalDislikes(getLong(overview, "feedbackDown"));
        e.setHydeCount(getLong(overview, "hydeCount"));

        // 来源分布
        Map<String, Long> sourceDist = (Map<String, Long>) overview.get("sourceDistribution");
        if (sourceDist != null) {
            e.setCacheHits(sourceDist.getOrDefault("hot_cache", 0L));
            e.setSimilarHits(sourceDist.getOrDefault("similar_question", 0L));
            e.setKnowledgeHits(sourceDist.getOrDefault("law_knowledge", 0L));
            e.setLlmDirectCount(sourceDist.getOrDefault("llm_direct", 0L));
            e.setNonLegalCount(sourceDist.getOrDefault("non_legal_reject", 0L));
        }

        // 反馈原因
        Map<String, Long> reasons = (Map<String, Long>) overview.get("feedbackReasons");
        if (reasons != null) {
            e.setFeedbackInaccurate(reasons.getOrDefault("inaccurate", 0L));
            e.setFeedbackWrongCitation(reasons.getOrDefault("wrong_citation", 0L));
            e.setFeedbackIrrelevant(reasons.getOrDefault("irrelevant", 0L));
            e.setFeedbackTooVague(reasons.getOrDefault("too_vague", 0L));
            e.setFeedbackOther(reasons.getOrDefault("other", 0L));
        }

        // 计算 LLM 兜底率
        long total = e.getTotalRequests();
        if (total > 0) {
            e.setLlmFallbackRate(BigDecimal.valueOf((double) e.getLlmDirectCount() / total)
                    .setScale(4, RoundingMode.HALF_UP));
        } else {
            e.setLlmFallbackRate(BigDecimal.ZERO);
        }

        // 最高相似度
        Object topScore = overview.get("topScoreMax");
        if (topScore != null) {
            try {
                e.setAvgTopScore(new BigDecimal(topScore.toString()));
            } catch (NumberFormatException ignored) {
                e.setAvgTopScore(BigDecimal.ZERO);
            }
        } else {
            e.setAvgTopScore(BigDecimal.ZERO);
        }

        return e;
    }

    private long getLong(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException ex) { return 0; }
    }
}
