package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.entity.RagMetricsDaily;
import com.lhs.lawmind.mapper.RagMetricsDailyMapper;
import com.lhs.lawmind.service.RagMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * RAG 指标收集服务实现
 * 使用 Redis 存储每日聚合指标，定时任务 + 仪表盘查询
 */
@Slf4j
@Service
public class RagMetricsServiceImpl implements RagMetricsService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagMetricsDailyMapper dailyMapper;

    private static final String PREFIX = "rag:metrics:daily:";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter MYSQL_DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public RagMetricsServiceImpl(Optional<RedisTemplate<String, Object>> redisTemplate,
                                  RagMetricsDailyMapper dailyMapper) {
        this.redisTemplate = redisTemplate.orElse(null);
        this.dailyMapper = dailyMapper;
    }

    @Override
    public void recordRequest(String source, long preMs, long embedMs, long searchMs,
                              long genMs, long totalMs, int retrieved, double topScore,
                              boolean hydeEnabled, Integer feedback) {
        if (redisTemplate == null) return;
        String today = todaysKey();
        try {
            String hashKey = PREFIX + today + ":hash";
            redisTemplate.opsForHash().increment(hashKey, "total", 1);
            if (source != null) {
                redisTemplate.opsForHash().increment(hashKey, "src:" + source, 1);
            }
            redisTemplate.opsForHash().increment(hashKey, "latency_sum", totalMs);
            redisTemplate.opsForHash().increment(hashKey, "latency_count", 1);
            redisTemplate.opsForHash().increment(hashKey, "pre_sum", preMs);
            redisTemplate.opsForHash().increment(hashKey, "embed_sum", embedMs);
            redisTemplate.opsForHash().increment(hashKey, "search_sum", searchMs);
            redisTemplate.opsForHash().increment(hashKey, "gen_sum", genMs);
            redisTemplate.opsForHash().increment(hashKey, "retrieved_sum", retrieved);
            // track max scores
            String currentMax = (String) redisTemplate.opsForHash().get(hashKey, "top_score_max");
            double existingMax = currentMax != null ? Double.parseDouble(currentMax) : 0;
            if (topScore > existingMax) {
                redisTemplate.opsForHash().put(hashKey, "top_score_max", String.format("%.4f", topScore));
            }
            String currentLatMax = (String) redisTemplate.opsForHash().get(hashKey, "latency_max");
            long existingLatMax = currentLatMax != null ? Long.parseLong(currentLatMax) : 0;
            if (totalMs > existingLatMax) {
                redisTemplate.opsForHash().put(hashKey, "latency_max", String.valueOf(totalMs));
            }
            if (hydeEnabled) {
                redisTemplate.opsForHash().increment(hashKey, "hyde_count", 1);
            }
            if (feedback != null && feedback == 1) {
                redisTemplate.opsForHash().increment(hashKey, "feedback_up", 1);
            } else if (feedback != null && feedback == -1) {
                redisTemplate.opsForHash().increment(hashKey, "feedback_down", 1);
            }
            // latency samples for percentile (sorted set: score=latency, member=uniqueId)
            redisTemplate.opsForZSet().add(PREFIX + today + ":latency",
                    System.nanoTime() + ":" + totalMs, totalMs);
            // expire after 14 days
            redisTemplate.expire(hashKey, java.time.Duration.ofDays(14));
            redisTemplate.expire(PREFIX + today + ":latency", java.time.Duration.ofDays(14));
        } catch (Exception e) {
            log.warn("指标记录失败: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getTodayOverview() {
        return getDayOverview(LocalDate.now());
    }

    @Override
    public void recordFeedbackReason(String content) {
        if (redisTemplate == null || content == null || content.isBlank()) return;
        String today = todaysKey();
        try {
            String hashKey = PREFIX + today + ":hash";
            for (String part : content.split("\\|")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                // 预设类别直接计数，其他文本归入 "other"
                if (List.of("inaccurate", "wrong_citation", "irrelevant", "too_vague").contains(trimmed)) {
                    redisTemplate.opsForHash().increment(hashKey, "feedback_reason:" + trimmed, 1);
                } else {
                    redisTemplate.opsForHash().increment(hashKey, "feedback_reason:other", 1);
                }
            }
        } catch (Exception e) {
            log.warn("反馈原因记录失败: {}", e.getMessage());
        }
    }

    @Override
    public Map<String, Object> getTrend(int days) {
        List<Map<String, Object>> dailyStats = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dailyStats.add(getDayOverview(date));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("daily", dailyStats);
        return result;
    }

    // ──────────── 质量指标 ────────────

    @Override
    public Map<String, Object> getQualityOverview() {
        return buildQualityOverview(LocalDate.now());
    }

    @Override
    public Map<String, Object> getQualityTrend(int days) {
        List<Map<String, Object>> dailyQuality = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            dailyQuality.add(buildQualityOverview(date));
        }
        // MySQL fallback: if Redis is empty for all days, use persisted data
        boolean redisHasData = dailyQuality.stream().anyMatch(m -> m.containsKey("feedbackUp"));
        if (!redisHasData && dailyMapper != null) {
            List<RagMetricsDaily> mysqlRows = dailyMapper.selectTrend(days);
            if (mysqlRows != null && !mysqlRows.isEmpty()) {
                Map<String, RagMetricsDaily> byDate = new LinkedHashMap<>();
                for (RagMetricsDaily row : mysqlRows) {
                    String dateStr = row.getMetricDate().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT);
                    byDate.put(dateStr, row);
                }
                dailyQuality.replaceAll(m -> {
                    String d = (String) m.get("date");
                    RagMetricsDaily entity = byDate.get(d);
                    return entity != null ? qualityOverviewFromEntity(entity) : m;
                });
                log.info("质量趋势 MySQL 回退: days={} redisEmpty={} mysqlRows={}",
                        days, dailyQuality.size(), mysqlRows.size());
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", days);
        result.put("daily", dailyQuality);
        return result;
    }

    private Map<String, Object> buildQualityOverview(LocalDate date) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date.format(DATE_FMT));
        if (redisTemplate == null) {
            result.put("llmFallbackRate", 0.0);
            return result;
        }
        try {
            return doBuildQualityOverview(date, result);
        } catch (Exception e) {
            log.warn("获取质量概览失败: date={}, error={}", date, e.getMessage());
            result.put("llmFallbackRate", 0.0);
            return result;
        }
    }

    private Map<String, Object> doBuildQualityOverview(LocalDate date, Map<String, Object> result) {
        String hashKey = PREFIX + date.format(DATE_FMT) + ":hash";
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(hashKey);
        if (raw.isEmpty()) {
            // MySQL fallback
            if (dailyMapper != null) {
                RagMetricsDaily entity = dailyMapper.selectByDate(date.format(MYSQL_DATE_FMT));
                if (entity != null && entity.getTotalRequests() != null && entity.getTotalRequests() > 0) {
                    return qualityOverviewFromEntity(entity);
                }
            }
            result.put("llmFallbackRate", 0.0);
            return result;
        }

        long total = getLong(raw, "total");
        long llmDirect = getLong(raw, "src:llm_direct");
        result.put("llmFallbackRate", total > 0 ? (double) llmDirect / total : 0.0);

        // 各来源的点踩率
        for (String src : List.of("hot_cache", "similar_question", "law_knowledge", "llm_direct")) {
            long srcCount = getLong(raw, "src:" + src);
            // 反馈计数是全局的，这里用总数近似；精确统计需要按来源拆分 feedback
            result.put("sourceCount_" + src, srcCount);
        }

        // 反馈原因分布
        Map<String, Long> reasons = new LinkedHashMap<>();
        for (String reason : List.of("inaccurate", "wrong_citation", "irrelevant", "too_vague", "other")) {
            long count = getLong(raw, "feedback_reason:" + reason);
            if (count > 0) reasons.put(reason, count);
        }
        result.put("feedbackReasons", reasons);

        long feedbackUp = getLong(raw, "feedback_up");
        long feedbackDown = getLong(raw, "feedback_down");
        result.put("feedbackUp", feedbackUp);
        result.put("feedbackDown", feedbackDown);
        long fbTotal = feedbackUp + feedbackDown;
        result.put("feedbackRate", fbTotal > 0 ? (double) feedbackUp / fbTotal : 0.0);

        return result;
    }

    // ──────────── 内部方法 ────────────

    private String todaysKey() {
        return LocalDate.now().format(DATE_FMT);
    }

    @Override
    public Map<String, Object> getDayOverview(LocalDate date) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date.format(DATE_FMT));
        if (redisTemplate == null) {
            result.put("total", 0);
            return result;
        }
        try {
            return doGetDayOverview(date, result);
        } catch (Exception e) {
            log.warn("获取日概览失败: date={}, error={}", date, e.getMessage());
            result.put("total", 0);
            return result;
        }
    }

    private Map<String, Object> doGetDayOverview(LocalDate date, Map<String, Object> result) {
        String hashKey = PREFIX + date.format(DATE_FMT) + ":hash";
        Map<Object, Object> raw = redisTemplate.opsForHash().entries(hashKey);
        if (raw.isEmpty()) {
            // MySQL fallback
            if (dailyMapper != null) {
                RagMetricsDaily entity = dailyMapper.selectByDate(date.format(MYSQL_DATE_FMT));
                if (entity != null && entity.getTotalRequests() != null && entity.getTotalRequests() > 0) {
                    return dayOverviewFromEntity(entity, result);
                }
            }
            result.put("total", 0L);
            return result;
        }
        long total = getLong(raw, "total");
        result.put("total", total);
        result.put("cacheHitRate", total > 0 ? (double) getLong(raw, "src:hot_cache") / total : 0.0);

        Map<String, Long> sourceDist = new LinkedHashMap<>();
        for (String src : List.of("hot_cache", "similar_question", "law_knowledge", "llm_direct", "non_legal_reject")) {
            sourceDist.put(src, getLong(raw, "src:" + src));
        }
        result.put("sourceDistribution", sourceDist);

        long latencySum = getLong(raw, "latency_sum");
        long latencyCount = getLong(raw, "latency_count");
        long latencyMax = getLong(raw, "latency_max");
        result.put("avgLatencyMs", latencyCount > 0 ? latencySum / latencyCount : 0);
        result.put("maxLatencyMs", latencyMax);

        long preSum = getLong(raw, "pre_sum");
        long embedSum = getLong(raw, "embed_sum");
        long searchSum = getLong(raw, "search_sum");
        long genSum = getLong(raw, "gen_sum");
        if (latencyCount > 0) {
            Map<String, Long> latencyBreakdown = new LinkedHashMap<>();
            latencyBreakdown.put("preprocess", preSum / latencyCount);
            latencyBreakdown.put("embedding", embedSum / latencyCount);
            latencyBreakdown.put("search", searchSum / latencyCount);
            latencyBreakdown.put("generation", genSum / latencyCount);
            result.put("latencyBreakdown", latencyBreakdown);
        }

        result.put("avgRetrieved", total > 0 ? (double) getLong(raw, "retrieved_sum") / total : 0.0);
        result.put("topScoreMax", raw.getOrDefault("top_score_max", "0"));
        result.put("hydeCount", getLong(raw, "hyde_count"));

        long feedbackUp = getLong(raw, "feedback_up");
        long feedbackDown = getLong(raw, "feedback_down");
        result.put("feedbackUp", feedbackUp);
        result.put("feedbackDown", feedbackDown);
        long feedbackTotal = feedbackUp + feedbackDown;
        result.put("feedbackRate", feedbackTotal > 0 ? (double) feedbackUp / feedbackTotal : 0.0);

        // 反馈原因分布
        Map<String, Long> feedbackReasons = new LinkedHashMap<>();
        for (String reason : List.of("inaccurate", "wrong_citation", "irrelevant", "too_vague", "other")) {
            long count = getLong(raw, "feedback_reason:" + reason);
            if (count > 0) feedbackReasons.put(reason, count);
        }
        result.put("feedbackReasons", feedbackReasons);

        // P50, P95 from sorted set
        String latencyKey = PREFIX + date.format(DATE_FMT) + ":latency";
        Long latSize = redisTemplate.opsForZSet().size(latencyKey);
        if (latSize != null && latSize > 0) {
            long p50Idx = latSize * 50 / 100;
            long p95Idx = latSize * 95 / 100;
            Set<Object> p50Set = redisTemplate.opsForZSet().range(latencyKey, p50Idx, p50Idx);
            Set<Object> p95Set = redisTemplate.opsForZSet().range(latencyKey, p95Idx, p95Idx);
            if (p50Set != null && !p50Set.isEmpty()) {
                result.put("p50LatencyMs", extractScore(p50Set.iterator().next()));
            }
            if (p95Set != null && !p95Set.isEmpty()) {
                result.put("p95LatencyMs", extractScore(p95Set.iterator().next()));
            }
        }

        return result;
    }

    // ──────────── MySQL 回退转换 ────────────

    /**
     * 将 MySQL 持久化数据转换为质量概览格式
     */
    private Map<String, Object> qualityOverviewFromEntity(RagMetricsDaily e) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", e.getMetricDate().toInstant()
                .atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FMT));
        result.put("llmFallbackRate", e.getLlmFallbackRate() != null
                ? e.getLlmFallbackRate().doubleValue() : 0.0);
        result.put("sourceCount_hot_cache", nvl(e.getCacheHits()));
        result.put("sourceCount_similar_question", nvl(e.getSimilarHits()));
        result.put("sourceCount_law_knowledge", nvl(e.getKnowledgeHits()));
        result.put("sourceCount_llm_direct", nvl(e.getLlmDirectCount()));
        result.put("feedbackUp", nvl(e.getTotalLikes()));
        result.put("feedbackDown", nvl(e.getTotalDislikes()));
        long fbTotal = nvl(e.getTotalLikes()) + nvl(e.getTotalDislikes());
        result.put("feedbackRate", fbTotal > 0
                ? (double) nvl(e.getTotalLikes()) / fbTotal : 0.0);

        Map<String, Long> reasons = new LinkedHashMap<>();
        putIfPos(reasons, "inaccurate", nvl(e.getFeedbackInaccurate()));
        putIfPos(reasons, "wrong_citation", nvl(e.getFeedbackWrongCitation()));
        putIfPos(reasons, "irrelevant", nvl(e.getFeedbackIrrelevant()));
        putIfPos(reasons, "too_vague", nvl(e.getFeedbackTooVague()));
        putIfPos(reasons, "other", nvl(e.getFeedbackOther()));
        result.put("feedbackReasons", reasons);
        return result;
    }

    /**
     * 将 MySQL 持久化数据转换为日概览格式
     */
    private Map<String, Object> dayOverviewFromEntity(RagMetricsDaily e, Map<String, Object> result) {
        long total = nvl(e.getTotalRequests());
        result.put("total", total);
        result.put("cacheHitRate", total > 0 ? (double) nvl(e.getCacheHits()) / total : 0.0);

        Map<String, Long> sourceDist = new LinkedHashMap<>();
        sourceDist.put("hot_cache", nvl(e.getCacheHits()));
        sourceDist.put("similar_question", nvl(e.getSimilarHits()));
        sourceDist.put("law_knowledge", nvl(e.getKnowledgeHits()));
        sourceDist.put("llm_direct", nvl(e.getLlmDirectCount()));
        sourceDist.put("non_legal_reject", nvl(e.getNonLegalCount()));
        result.put("sourceDistribution", sourceDist);

        result.put("avgLatencyMs", nvl(e.getAvgLatencyMs()));
        result.put("maxLatencyMs", 0L);
        result.put("p50LatencyMs", nvl(e.getP50LatencyMs()));
        result.put("p95LatencyMs", nvl(e.getP95LatencyMs()));
        result.put("avgRetrieved", 0.0);
        result.put("topScoreMax", e.getAvgTopScore() != null ? e.getAvgTopScore().toString() : "0");
        result.put("hydeCount", nvl(e.getHydeCount()));

        result.put("feedbackUp", nvl(e.getTotalLikes()));
        result.put("feedbackDown", nvl(e.getTotalDislikes()));
        long fbTotal = nvl(e.getTotalLikes()) + nvl(e.getTotalDislikes());
        result.put("feedbackRate", fbTotal > 0 ? (double) nvl(e.getTotalLikes()) / fbTotal : 0.0);

        Map<String, Long> feedbackReasons = new LinkedHashMap<>();
        putIfPos(feedbackReasons, "inaccurate", nvl(e.getFeedbackInaccurate()));
        putIfPos(feedbackReasons, "wrong_citation", nvl(e.getFeedbackWrongCitation()));
        putIfPos(feedbackReasons, "irrelevant", nvl(e.getFeedbackIrrelevant()));
        putIfPos(feedbackReasons, "too_vague", nvl(e.getFeedbackTooVague()));
        putIfPos(feedbackReasons, "other", nvl(e.getFeedbackOther()));
        result.put("feedbackReasons", feedbackReasons);

        return result;
    }

    private long nvl(Long v) { return v == null ? 0L : v; }

    private void putIfPos(Map<String, Long> map, String key, long value) {
        if (value > 0) map.put(key, value);
    }

    private long getLong(Map<Object, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return 0;
        if (v instanceof Number n) return n.longValue();
        try { return Long.parseLong(v.toString()); } catch (NumberFormatException e) { return 0; }
    }

    private long extractScore(Object member) {
        if (member == null) return 0;
        String s = member.toString();
        int lastColon = s.lastIndexOf(':');
        if (lastColon >= 0) {
            try { return Long.parseLong(s.substring(lastColon + 1)); } catch (NumberFormatException e) { return 0; }
        }
        return 0;
    }
}
