package com.lhs.lawmind.service;

import java.util.Map;

/**
 * RAG 指标收集与查询服务
 * 为仪表盘提供缓存命中率、来源占比、延迟分布等数据
 */
public interface RagMetricsService {

    /**
     * 记录一次 RAG 请求的指标数据
     *
     * @param source       数据来源（hot_cache/similar_question/law_knowledge/llm_direct/non_legal_reject）
     * @param preMs        预处理耗时（毫秒）
     * @param embedMs      向量化耗时（毫秒）
     * @param searchMs     检索耗时（毫秒）
     * @param genMs        生成耗时（毫秒）
     * @param totalMs      总耗时（毫秒）
     * @param retrieved    检索到的知识条数
     * @param topScore     最高相似度得分
     * @param hydeEnabled  是否启用了 HyDE
     * @param feedback     用户反馈（1=赞, -1=踩, null=无）
     */
    void recordRequest(String source, long preMs, long embedMs, long searchMs,
                       long genMs, long totalMs, int retrieved, double topScore,
                       boolean hydeEnabled, Integer feedback);

    /**
     * 获取某日概览数据
     */
    Map<String, Object> getDayOverview(java.time.LocalDate date);

    /**
     * 获取今日概览数据
     */
    Map<String, Object> getTodayOverview();

    /**
     * 记录用户反馈原因分类（点踩时调用）
     *
     * @param content 反馈内容，管道符分隔，如 "inaccurate|wrong_citation|备注"
     */
    void recordFeedbackReason(String content);

    /**
     * 获取最近 N 天的趋势数据
     *
     * @param days 天数
     */
    Map<String, Object> getTrend(int days);

    /**
     * 获取质量指标概览（各来源点赞率、反馈原因分布、LLM兜底率）
     */
    Map<String, Object> getQualityOverview();

    /**
     * 获取最近 N 天的质量趋势
     */
    Map<String, Object> getQualityTrend(int days);
}
