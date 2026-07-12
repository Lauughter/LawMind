package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

@Data
public class AiChat {
    private Long id;
    private Long userId;
    private Long conversationId;
    private String userQuestion;
    private String aiAnswer;
    private String knowledgeMatch;
    /** Token用量：输入Token数 */
    private Integer tokenUsageInput;
    /** Token用量：输出Token数 */
    private Integer tokenUsageOutput;
    /** 预估成本（元） */
    private java.math.BigDecimal estimatedCost;
    /** 用户反馈：1=赞, -1=踩, null=无反馈 */
    private Integer feedback;
    /** 反馈内容（用户填写的文字说明） */
    private String feedbackContent;
    /** 反馈状态：PENDING_REVIEW(待审核)/REVIEWED(已审核)/DISMISSED(已忽略) */
    private String feedbackStatus;
    /** 审核人ID */
    private Long reviewedBy;
    /** 审核时间 */
    private Date reviewedAt;
    /** 审核备注 */
    private String reviewNotes;
    private Date createTime;
}