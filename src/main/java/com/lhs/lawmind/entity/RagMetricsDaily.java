package com.lhs.lawmind.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class RagMetricsDaily {
    private Long id;
    private Date metricDate;
    private Long totalRequests;
    private Long cacheHits;
    private Long similarHits;
    private Long knowledgeHits;
    private Long llmDirectCount;
    private Long nonLegalCount;
    private Long avgLatencyMs;
    private Long p50LatencyMs;
    private Long p95LatencyMs;
    private Long totalLikes;
    private Long totalDislikes;
    private BigDecimal avgTopScore;
    private BigDecimal llmFallbackRate;
    private Long hydeCount;
    private Long feedbackInaccurate;
    private Long feedbackWrongCitation;
    private Long feedbackIrrelevant;
    private Long feedbackTooVague;
    private Long feedbackOther;
    private Date createdAt;
    private Date updatedAt;
}
