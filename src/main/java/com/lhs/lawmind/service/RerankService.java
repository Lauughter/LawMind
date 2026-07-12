package com.lhs.lawmind.service;

import com.lhs.lawmind.entity.LawKnowledge;

import java.util.List;

/**
 * 精排服务接口
 *
 * <p>对候选文档重新打分排序，提升检索精度。</p>
 */
public interface RerankService {

    /**
     * 对候选文档进行精排
     *
     * @param query      用户查询文本
     * @param candidates 候选文档列表
     * @param maxInput   送入精排的最大候选数
     * @param topN       精排后返回的最大数量
     * @return 重排序后的文档列表
     */
    List<LawKnowledge> rerank(String query, List<LawKnowledge> candidates, int maxInput, int topN);
}
