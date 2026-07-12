package com.lhs.lawmind.dto;

import lombok.Data;

/**
 * 流式聊天请求 DTO
 */
@Data
public class StreamChatRequest {
    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 用户问题
     */
    private String question;

    /**
     * 会话ID（可为null，首次发送时后端自动创建）
     */
    private Long conversationId;
}
