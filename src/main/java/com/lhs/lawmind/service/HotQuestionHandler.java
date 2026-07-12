package com.lhs.lawmind.service;

import com.lhs.lawmind.entity.HotQuestion;

/**
 * 热点问题处理器接口
 * 协调各个服务完成完整的热点问题处理流程
 */
public interface HotQuestionHandler {
    
    /**
     * 处理热点问题查询流程
     * @param questionMd5 问题MD5值
     * @return 热点问题对象，如果不存在返回null
     */
    HotQuestion handleHotQuestionQuery(String questionMd5);
    
    /**
     * 处理问题访问统计和热点升级
     * @param questionMd5 问题MD5值
     * @param originalQuestion 原始问题
     * @param answer 答案内容
     * @param knowledgeIds 关联知识点ID
     * @return 是否成功处理
     */
    boolean handleVisitAndUpgrade(String questionMd5, String originalQuestion, 
                                String answer, String knowledgeIds);
    
    /**
     * 检查并升级热点问题
     * @param questionMd5 问题MD5值
     * @param originalQuestion 原始问题
     * @param answer 答案内容
     * @param knowledgeIds 关联知识点ID
     * @return 是否升级成功
     */
    boolean checkAndUpgradeHotQuestion(String questionMd5, String originalQuestion, 
                                     String answer, String knowledgeIds);
    
    /**
     * 获取热点问题答案（如果存在）
     * @param questionMd5 问题MD5值
     * @return 答案内容，如果不存在返回null
     */
    String getHotAnswerIfExist(String questionMd5);
    
    /**
     * 执行热点缓存清理任务
     * @return 清理的数量
     */
    int performCacheCleanup();
    
    /**
     * 获取热点处理统计信息
     * @return 统计信息
     */
    HotQuestionStats getHotQuestionStats();
    
    /**
     * 热点问题统计信息
     */
    class HotQuestionStats {
        private int totalCacheCount;
        private int todayVisitCount;
        private int upgradedToday;
        private int cleanedUpCount;
        
        // getters and setters
        public int getTotalCacheCount() { return totalCacheCount; }
        public void setTotalCacheCount(int totalCacheCount) { this.totalCacheCount = totalCacheCount; }
        
        public int getTodayVisitCount() { return todayVisitCount; }
        public void setTodayVisitCount(int todayVisitCount) { this.todayVisitCount = todayVisitCount; }
        
        public int getUpgradedToday() { return upgradedToday; }
        public void setUpgradedToday(int upgradedToday) { this.upgradedToday = upgradedToday; }
        
        public int getCleanedUpCount() { return cleanedUpCount; }
        public void setCleanedUpCount(int cleanedUpCount) { this.cleanedUpCount = cleanedUpCount; }
    }
}