package com.lhs.lawmind.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class EvalReportRecord {
    private Long id;
    private String runId;
    private String datasetPath;
    private Integer datasetVersion;
    private Integer totalCases;
    private Integer passedCases;
    private Integer failedCases;
    private BigDecimal avgKeywordRecall;
    private BigDecimal avgSourceMatch;
    private BigDecimal avgLawTypeMatch;
    private BigDecimal avgAnswerLength;
    private BigDecimal avgTotalScore;
    private BigDecimal avgFaithfulness;
    private BigDecimal avgAnswerRelevance;
    private String reportJson;
    private Date createdAt;
}
