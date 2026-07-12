package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

@Data
public class LawVectorTask {
    private Long id;
    private Long knowledgeId;
    private Integer vectorStatus;// 0-待处理，1-已完成，2-失败
    private Integer redisSearchSync;// 是否已同步到Redis中，0-未同步，1-已同步
    private String errorMsg;// 失败原因
    private Integer retryCount;// 重试次数
    private Date createTime;
    private Date updateTime;
}