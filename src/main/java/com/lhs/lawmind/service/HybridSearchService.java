package com.lhs.lawmind.service;

import com.lhs.lawmind.entity.LawKnowledge;

import java.util.List;

/**
 * 混合搜索服务接口
 *
 * <p>结合向量检索与全文检索，通过 RRF 融合排序提升召回率。</p>
 */
public interface HybridSearchService {

    /**
     * 混合搜索：向量 KNN + MySQL Ngram 全文检索 → RRF 融合
     *
     * @param queryVector   查询向量（可为空，降级为纯全文搜索）
     * @param expandedQuery 扩展后的查询文本（用于全文搜索）
     * @param topK          返回数量
     * @return 带 RRF 分数的 LawKnowledge 列表
     */
    List<LawKnowledge> searchHybrid(float[] queryVector, String expandedQuery, int topK);

    /**
     * 混合搜索 + 元数据过滤：仅搜索现行有效法律，可选限定法律类型
     *
     * @param queryVector   查询向量（可为空，降级为纯全文搜索）
     * @param expandedQuery 扩展后的查询文本（用于全文搜索）
     * @param topK          返回数量
     * @param lawType       法律类型过滤（null 表示不限定）
     * @return 带 RRF 分数的 LawKnowledge 列表
     */
    List<LawKnowledge> searchHybridFiltered(float[] queryVector, String expandedQuery, int topK, String lawType);
}
