package com.lhs.lawmind.service;

import com.lhs.lawmind.entity.SimilarQuestion;

/**
 * 相似问题服务接口
 * 提供相似问题的存储和搜索功能
 */
public interface SimilarQuestionService {
    /**
     * 异步保存相似问题
     * <p>将用户问题、AI回答和关联的知识点ID异步保存到相似问题库</p>
     * 
     * @param question 用户问题
     * @param answer AI生成的回答
     * @param knowledgeIds 关联的知识点ID列表，逗号分隔
     */
    void asyncSaveSimilarQuestion(String question, String answer, String knowledgeIds);

    /**
     * 搜索相似问题
     * <p>根据用户问题搜索相似的问题</p>
     * 
     * @param question 用户问题
     * @return 匹配的相似问题，未命中返回null
     */
    SimilarQuestion searchSimilarQuestion(String question);

    /**
     * 异步更新相似问题的knowledgeIds字段
     * <p>根据用户问题更新相似问题库中对应问题的knowledgeIds字段</p>
     * 
     * @param question 用户问题
     * @param knowledgeIds 关联的知识点ID列表，逗号分隔
     */
    void asyncUpdateSimilarQuestionKnowledgeIds(String question, String knowledgeIds);
}