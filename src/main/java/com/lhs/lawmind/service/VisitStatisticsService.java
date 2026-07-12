package com.lhs.lawmind.service;

/**
 * 访问统计服务接口
 * 负责问题访问次数统计和热点阈值判断
 */
public interface VisitStatisticsService {
    
    /**
     * 记录问题访问
     * @param questionMd5 问题MD5值
     * @return 更新后的访问次数
     */
    int recordVisit(String questionMd5);
    
    /**
     * 获取问题访问次数
     * @param questionMd5 问题MD5值
     * @return 访问次数
     */
    int getVisitCount(String questionMd5);
    
    /**
     * 检查是否达到热点阈值
     * @param questionMd5 问题MD5值
     * @param timeWindow 时间窗口（分钟）
     * @param threshold 阈值次数
     * @return true-达到阈值，false-未达到阈值
     */
    boolean isHotThresholdReached(String questionMd5, int timeWindow, int threshold);
    
    /**
     * 获取指定时间窗口内的访问统计
     * @param questionMd5 问题MD5值
     * @param timeWindowMinutes 时间窗口（分钟）
     * @return 访问次数
     */
    int getVisitCountInTimeWindow(String questionMd5, int timeWindowMinutes);
    
    /**
     * 重置问题访问统计
     * @param questionMd5 问题MD5值
     * @return 是否重置成功
     */
    boolean resetVisitStatistics(String questionMd5);
}