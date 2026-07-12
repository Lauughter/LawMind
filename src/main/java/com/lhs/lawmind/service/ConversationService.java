package com.lhs.lawmind.service;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.entity.Conversation;

import java.util.List;

/**
 * 会话服务接口
 * 提供会话的创建、查询、重命名、删除等功能
 */
public interface ConversationService {

    /**
     * 创建新会话
     *
     * @param userId 用户ID
     * @param title  会话标题
     * @return 创建的会话实体（含生成的ID）
     */
    Conversation create(Long userId, String title);

    /**
     * 根据ID获取会话
     *
     * @param id 会话ID
     * @return 会话实体，不存在或已删除返回 null
     */
    Conversation getById(Long id);

    /**
     * 获取用户的会话列表（按更新时间倒序）
     *
     * @param userId 用户ID
     * @return 会话列表
     */
    List<Conversation> listByUserId(Long userId);

    /**
     * 重命名会话
     *
     * @param id    会话ID
     * @param title 新标题
     * @return 更新的记录数
     */
    int rename(Long id, String title);

    /**
     * 分页获取用户的会话列表
     */
    PageResult<Conversation> listByUserIdPage(Long userId, int page, int pageSize);

    /**
     * 软删除会话
     *
     * @param id 会话ID
     * @return 更新的记录数
     */
    int delete(Long id);

    /**
     * 更新会话的最后更新时间
     *
     * @param id 会话ID
     */
    void touch(Long id);
}
