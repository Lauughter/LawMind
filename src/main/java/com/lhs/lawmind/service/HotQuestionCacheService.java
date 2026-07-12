package com.lhs.lawmind.service;

import com.lhs.lawmind.entity.HotQuestion;

/**
 * 热点问题缓存服务接口
 * 负责热点问题的查询、存储和管理
 */
public interface HotQuestionCacheService {
    
    /**
     * 根据问题MD5查询热点缓存
     * @param questionMd5 问题MD5值
     * @return 热点问题对象，如果不存在或已过期返回null
     */
    HotQuestion getHotQuestionByMd5(String questionMd5);
    
    /**
     * 检查问题是否为热点问题
     * @param questionMd5 问题MD5值
     * @return true-是热点问题，false-不是热点问题
     */
    boolean isHotQuestion(String questionMd5);
    
    /**
     * 获取热点问题的答案
     * @param questionMd5 问题MD5值
     * @return 答案内容，如果不存在返回null
     */
    String getHotAnswer(String questionMd5);
    
    /**
     * 将问题升级为热点缓存
     * @param questionMd5 问题MD5值
     * @param originalQuestion 原始问题文本
     * @param answer 答案内容
     * @param knowledgeIds 关联的知识点ID
     * @param initialTtlDays 初始TTL天数
     * @return 是否升级成功
     */
    boolean upgradeToHotCache(String questionMd5, String originalQuestion, 
                            String answer, String knowledgeIds, int initialTtlDays);
    
    /**
     * 更新热点问题的访问统计
     * @param questionMd5 问题MD5值
     * @return 更新后的访问次数
     */
    int updateVisitCount(String questionMd5);
    
    /**
     * 清理过期的热点缓存
     * @return 清理的数量
     */
    int cleanupExpiredCache();
    
    /**
     * 获取热点缓存统计信息
     * @return 缓存总数
     */
    int getCacheCount();
}