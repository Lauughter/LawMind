package com.lhs.lawmind.dto;

import lombok.Data;

import java.util.List;

@Data
public class AIChatResponse {
    /**
     * AI 回答
     */
    private String answer;
    
    /**
     * 相关的法律知识
     */
    private List<Object> relatedKnowledge;
    
    /**
     * 对话 ID
     */
    private Long chatId;

    /**
     * 会话 ID
     */
    private Long conversationId;
}
