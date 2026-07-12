package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

@Data
public class ReviewLog {
    private Long id;
    private Long chatId;
    private String question;
    private String actionType;
    private String actionDetail;
    private String feedbackReason;
    private Integer processed;
    private Date processedAt;
    private Date createdAt;
}
