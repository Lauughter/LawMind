package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.entity.Conversation;
import com.lhs.lawmind.mapper.ConversationMapper;
import com.lhs.lawmind.service.ConversationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationMapper conversationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Conversation create(Long userId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title != null && !title.isBlank() ? title : "新对话");
        conversationMapper.insert(conversation);
        log.info("创建会话成功: id={}, userId={}, title={}", conversation.getId(), userId, conversation.getTitle());
        return conversation;
    }

    @Override
    public Conversation getById(Long id) {
        Conversation conversation = conversationMapper.selectById(id);
        if (conversation != null) {
            log.debug("查询会话成功: id={}", id);
        } else {
            log.debug("未找到会话: id={}", id);
        }
        return conversation;
    }

    @Override
    public List<Conversation> listByUserId(Long userId) {
        try {
            log.info("开始查询会话列表: userId={}", userId);
            List<Conversation> list = conversationMapper.selectByUserId(userId);
            log.info("查询会话列表成功: userId={}, 数量={}", userId, list.size());
            log.info("会话列表详情: {}", list);
            return list;
        } catch (Exception e) {
            log.error("查询会话列表失败: userId={}", userId, e);
            throw e;
        }
    }

    @Override
    public PageResult<Conversation> listByUserIdPage(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Conversation> list = conversationMapper.selectByUserIdPage(userId, offset, pageSize);
        long total = conversationMapper.countByUserId(userId);
        return PageResult.of(total, list, page, pageSize);
    }

    @Override
    public int rename(Long id, String title) {
        int result = conversationMapper.updateTitle(id, title);
        log.info("重命名会话: id={}, newTitle={}, result={}", id, title, result);
        return result;
    }

    @Override
    public int delete(Long id) {
        int result = conversationMapper.softDelete(id);
        log.info("删除会话: id={}, result={}", id, result);
        return result;
    }

    @Override
    public void touch(Long id) {
        conversationMapper.updateUpdateTime(id);
    }
}
