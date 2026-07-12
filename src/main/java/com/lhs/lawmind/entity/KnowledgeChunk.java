package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

/**
 * 知识块实体类
 * <p>表示法律知识库中的一个知识块，包含知识块的基本信息
 * <p>包含知识块ID、所属法律知识ID、块索引、内容、向量化状态、错误信息、重试次数以及创建和更新时间等字段</p>
 *
 */
@Data
public class KnowledgeChunk {
    /**
     * 知识块ID
     */
    private Long id;
    /**
     * 所属法律知识ID
     */
    private Long knowledgeId;
    /**
     * 块索引
     */
    private Integer chunkIndex;
    /**
     * 上下文前缀（如 "[网络安全法 第一章 总纲 第一条]"），帮助检索定位法律出处
     */
    private String contextPrefix;
    /**
     * 块内容
     */
    private String content;
    /**
     * 向量化状态
     * <p>0表示未向量化，1表示向量化成功，2表示向量化失败</p>
     */
    private Integer vectorStatus;
    /**
     * 错误信息
     */
    private String errorMsg;
    /**
     * 重试次数
     */
    private Integer retryCount;
    /**
     * 创建时间
     */
    private Date createTime;
    /**
     * 更新时间
     */
    private Date updateTime;

}
