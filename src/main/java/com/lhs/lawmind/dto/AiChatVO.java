package com.lhs.lawmind.dto;

import com.lhs.lawmind.entity.AiChat;
import lombok.Data;

import java.util.Date;

/**
 * AI 聊天记录响应对象（对外 API 使用，隐藏 token 用量、成本等内部字段）
 */
@Data
public class AiChatVO {
    private Long id;
    private Long conversationId;
    private String userQuestion;
    private String aiAnswer;
    private Integer feedback;
    private String feedbackContent;
    private String feedbackStatus;
    private Date reviewedAt;
    private String reviewNotes;
    private Date createTime;

    public static AiChatVO from(AiChat entity) {
        AiChatVO vo = new AiChatVO();
        vo.setId(entity.getId());
        vo.setConversationId(entity.getConversationId());
        vo.setUserQuestion(entity.getUserQuestion());
        vo.setAiAnswer(entity.getAiAnswer());
        vo.setFeedback(entity.getFeedback());
        vo.setFeedbackContent(entity.getFeedbackContent());
        vo.setFeedbackStatus(entity.getFeedbackStatus());
        vo.setReviewedAt(entity.getReviewedAt());
        vo.setReviewNotes(entity.getReviewNotes());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }
}
