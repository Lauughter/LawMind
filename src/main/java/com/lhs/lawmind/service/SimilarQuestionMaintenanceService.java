package com.lhs.lawmind.service;

/**
 * 相似问题库维护服务接口
 * 负责相似问题库的更新和维护
 */
public interface SimilarQuestionMaintenanceService {
    
    /**
     * 将问答对存入相似问题库
     * @param question 原始问题
     * @param answer 答案内容
     * @param knowledgeIds 关联知识点ID
     * @return 是否存储成功
     */
    boolean storeToSimilarQuestionLibrary(String question, String answer, String knowledgeIds);
    
    /**
     * 异步存储到相似问题库
     * @param question 原始问题
     * @param answer 答案内容
     * @param knowledgeIds 关联知识点ID
     */
    void asyncStoreToSimilarQuestionLibrary(String question, String answer, String knowledgeIds);
    
    /**
     * 批量更新相似问题的知识点关联
     * @param question 问题文本
     * @param knowledgeIds 知识点ID列表（逗号分隔）
     * @return 是否更新成功
     */
    boolean updateSimilarQuestionKnowledgeIds(String question, String knowledgeIds);
    
    /**
     * 异步更新相似问题的知识点关联
     * @param question 问题文本
     * @param knowledgeIds 知识点ID列表（逗号分隔）
     */
    void asyncUpdateSimilarQuestionKnowledgeIds(String question, String knowledgeIds);
    
    /**
     * 清理低质量的相似问题记录
     * @param minVisitCount 最小访问次数阈值
     * @return 清理的数量
     */
    int cleanupLowQualitySimilarQuestions(int minVisitCount);
    
    /**
     * 获取相似问题库统计信息
     * @return 总记录数
     */
    int getSimilarQuestionCount();
}