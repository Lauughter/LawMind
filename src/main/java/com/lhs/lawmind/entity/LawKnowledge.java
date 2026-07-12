package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

@Data
public class LawKnowledge {
    private Long id;
    private Long userId;
    private String lawType;// 法律类型，如 "民法", "刑法", "行政法" 等
    private String title;// 法律文档标题，如 "中华人民共和国民法典"
    private String chapter;// 可选，部分法律文档可能没有章
    private String section;// 可选，部分法律文档可能没有节
    private Integer articleNumber;
    private String content;
    private Integer vectorStatus;// 0-未生成向量，1-已生成向量，2-向量生成失败
    private Date effectiveDate;// 法律生效日期
    private Date expiryDate;// 法律失效日期，若无失效日期可设置为 null
    private String status;// 法律状态，如 "EFFECTIVE", "REPEALED", "DRAFT" 等
    private String source;// 法律知识来源，如 "BATCH_IMPORT", "MANUAL", "AUTO_LEARN" 等
    private String publisher;
    private Date publishDate;// 法律发布日期
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
    private Double score; // 相似度值（非DB字段，检索时填充）
}