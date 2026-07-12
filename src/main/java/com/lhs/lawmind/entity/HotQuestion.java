package com.lhs.lawmind.entity;

import lombok.Data;
import java.util.Date;

/**
 * 热点问题实体类
 * 用于存储高频访问的问题及其答案缓存
 */
@Data
public class HotQuestion {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 问题MD5值（唯一标识）
     */
    private String questionMd5;
    
    /**
     * 原始问题文本
     */
    private String originalQuestion;
    
    /**
     * 缓存的答案
     */
    private String cachedAnswer;
    
    /**
     * 关联的法律知识点ID（如果有）
     */
    private String knowledgeIds;
    
    /**
     * 访问次数
     */
    private Integer visitCount;
    
    /**
     * 首次访问时间
     */
    private Date firstVisitTime;
    
    /**
     * 最后访问时间
     */
    private Date lastVisitTime;
    
    /**
     * 缓存创建时间
     */
    private Date createTime;
    
    /**
     * 缓存过期时间
     */
    private Date expireTime;
    
    /**
     * 缓存状态：0-有效，1-已过期，2-已淘汰
     */
    private Integer status;
}