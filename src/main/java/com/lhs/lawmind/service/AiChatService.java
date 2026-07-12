package com.lhs.lawmind.service;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.entity.AiChat;

import java.util.List;

/**
 * 聊天记录服务接口
 * 封装聊天记录相关的数据库操作
 * 提供聊天记录的插入、更新、删除和查询功能
 */
public interface AiChatService {

    /**
     * 插入聊天记录
     * <p>将新的聊天记录插入到数据库中</p>
     *
     * @param aiChat 聊天记录实体
     * @return 插入的聊天记录ID，插入失败返回null
     */
    Long insert(AiChat aiChat);

    /**
     * 更新聊天记录
     * <p>更新已存在的聊天记录信息</p>
     *
     * @param aiChat 聊天记录实体
     * @return 更新成功的记录数，更新失败返回0
     */
    int update(AiChat aiChat);

    /**
     * 根据ID查询聊天记录
     * <p>根据聊天记录的唯一标识ID查询完整的聊天记录信息</p>
     *
     * @param id 聊天记录ID
     * @return 聊天记录实体，未找到返回null
     */
    AiChat getById(Long id);

    /**
     * 获取所有聊天记录
     * <p>获取所有聊天记录列表</p>
     *
     * @return 聊天记录列表
     */
    List<AiChat> selectAll();

    /**
     * 根据用户ID查询聊天记录
     * <p>根据用户ID查询聊天记录列表</p>
     *
     * @param userId 用户ID
     * @return 聊天记录列表
     */
    List<AiChat> selectByUserId(Long userId);

    /**
     * 根据会话ID查询聊天记录
     *
     * @param conversationId 会话ID
     * @return 聊天记录列表（按时间正序）
     */
    List<AiChat> selectByConversationId(Long conversationId);

    /**
     * 分页查询会话消息
     */
    PageResult<AiChat> selectByConversationIdPage(Long conversationId, int page, int pageSize);

    /**
     * 分页查询所有聊天记录（管理员）
     */
    PageResult<AiChat> selectPage(int page, int pageSize);

    /**
     * 分页查询用户聊天记录
     */
    PageResult<AiChat> selectByUserIdPage(Long userId, int page, int pageSize);

    /**
     * 删除聊天记录
     * <p>根据ID删除聊天记录</p>
     *
     * @param id 聊天记录ID
     * @return 删除成功的记录数
     */
    int delete(Long id);

    /**
     * 处理用户问题
     * <p>处理用户的问题并返回AI回答</p>
     *
     * @param userId         用户ID
     * @param question       用户问题
     * @param conversationId 会话ID（可为null，为null时自动创建会话）
     * @return AI回答响应
     */
    AIChatResponse askQuestion(Long userId, String question, Long conversationId);

    /**
     * 更新消息反馈
     *
     * @param id              聊天记录ID
     * @param feedback        反馈值（1=赞, -1=踩, null=取消反馈）
     * @param feedbackContent 反馈文字说明（可选）
     * @return 更新成功的记录数
     */
    int updateFeedback(Long id, Integer feedback, String feedbackContent);

    /**
     * 分页查询待审核的反馈（管理员）
     */
    PageResult<AiChat> selectPendingReviewPage(int page, int pageSize);

    /**
     * 提交审核结果
     *
     * @param id             聊天记录ID
     * @param feedbackStatus 审核状态（REVIEWED/DISMISSED）
     * @param reviewedBy     审核人ID
     * @param reviewNotes    审核备注
     * @return 更新成功的记录数
     */
    int submitReview(Long id, String feedbackStatus, Long reviewedBy, String reviewNotes);
}