package com.lhs.lawmind.entity;

import lombok.Data;

import java.util.Date;

/**
 * 会话实体
 * 用于管理用户的多轮对话会话
 */
@Data
public class Conversation {
    private Long id;
    private Long userId;
    private String title;
    private Date createTime;
    private Date updateTime;
    private Integer isDeleted;
}
