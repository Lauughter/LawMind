package com.lhs.lawmind.dto;

import lombok.Data;

@Data
public class AgentAskRequest {
    private String question;
    private Long conversationId;
    /** 前端手动模式切换："agent" = 强制Agent模式, null/"" = 自动（门控路由） */
    private String mode;
}
