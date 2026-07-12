package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

@Data
public class LawFileUpload {
    private Long id;
    private Long userId;
    private Long knowledgeId;
    private String processingStatus;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String filePath;
    private String content;
    private String aiReviewResult;
    private String aiRevisedContent;
    private String riskLevel;
    private Date uploadTime;
    private Integer isDeleted;
}