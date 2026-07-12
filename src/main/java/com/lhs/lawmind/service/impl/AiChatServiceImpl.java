package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.ReviewLog;
import com.lhs.lawmind.mapper.AiChatMapper;
import com.lhs.lawmind.mapper.ReviewLogMapper;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.service.RagMetricsService;
import com.lhs.lawmind.service.RagService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

/**
 * 聊天记录服务实现类
 * 封装聊天记录相关的数据库操作
 * 实现AiChatService接口，提供聊天记录的插入、更新、删除和查询功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatServiceImpl implements AiChatService {

    private final AiChatMapper aiChatMapper;

    private final RagService ragService;

    private final RagMetricsService ragMetricsService;

    private final ReviewLogMapper reviewLogMapper;

    /**
     * 插入聊天记录
     * <p>将新的聊天记录插入到数据库中</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param aiChat 聊天记录实体
     * @return 插入的聊天记录ID，插入失败返回null
     */
    @Override
    public Long insert(AiChat aiChat) {
        int result = aiChatMapper.insert(aiChat);
        if (result > 0) {
            log.info("插入聊天记录成功: id={}", aiChat.getId());
            return aiChat.getId();
        } else {
            log.warn("插入聊天记录失败");
            return null;
        }
    }

    /**
     * 更新聊天记录
     * <p>更新已存在的聊天记录信息</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param aiChat 聊天记录实体
     * @return 更新成功的记录数，更新失败返回0
     */
    @Override
    public int update(AiChat aiChat) {
        int result = aiChatMapper.update(aiChat);
        if (result > 0) {
            log.debug("更新聊天记录成功: id={}", aiChat.getId());
        } else {
            log.debug("更新聊天记录失败: id={}", aiChat.getId());
        }
        return result;
    }

    /**
     * 根据ID查询聊天记录
     * <p>根据聊天记录的唯一标识ID查询完整的聊天记录信息</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 聊天记录ID
     * @return 聊天记录实体，未找到或查询失败返回null
     */
    @Override
    public AiChat getById(Long id) {
        AiChat aiChat = aiChatMapper.selectById(id);
        if (aiChat != null) {
            log.debug("查询聊天记录成功: id={}", id);
        } else {
            log.debug("未找到聊天记录: id={}", id);
        }
        return aiChat;
    }

    /**
     * 获取所有聊天记录
     * <p>获取所有聊天记录列表</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @return 聊天记录列表
     */
    @Override
    public List<AiChat> selectAll() {
        List<AiChat> aiChats = aiChatMapper.selectAll();
        log.debug("获取所有聊天记录成功: 数量={}", aiChats.size());
        return aiChats;
    }

    /**
     * 根据用户ID查询聊天记录
     * <p>根据用户ID查询聊天记录列表</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param userId 用户ID
     * @return 聊天记录列表
     */
    @Override
    public List<AiChat> selectByUserId(Long userId) {
        List<AiChat> aiChats = aiChatMapper.selectByUserId(userId);
        log.debug("根据用户ID查询聊天记录成功: userId={}, 数量={}", userId, aiChats.size());
        return aiChats;
    }

    @Override
    public List<AiChat> selectByConversationId(Long conversationId) {
        List<AiChat> aiChats = aiChatMapper.selectByConversationId(conversationId);
        log.debug("根据会话ID查询聊天记录: conversationId={}, 数量={}", conversationId, aiChats.size());
        return aiChats;
    }

    @Override
    public PageResult<AiChat> selectByConversationIdPage(Long conversationId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AiChat> list = aiChatMapper.selectByConversationIdPage(conversationId, offset, pageSize);
        long total = aiChatMapper.countByConversationId(conversationId);
        return PageResult.of(total, list, page, pageSize);
    }

    /**
     * 删除聊天记录
     * <p>根据ID删除聊天记录</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 聊天记录ID
     * @return 删除成功的记录数
     */
    @Override
    public int delete(Long id) {
        int result = aiChatMapper.delete(id);
        if (result > 0) {
            log.debug("删除聊天记录成功: id={}", id);
        } else {
            log.debug("删除聊天记录失败: id={}", id);
        }
        return result;
    }

    /**
     * 处理用户问题
     * <p>处理用户的问题并返回AI回答</p>
     * <p>调用RagService来处理用户问题</p>
     *
     * @param userId 用户ID
     * @param question 用户问题
     * @return AI回答响应
     */
    @Override
    public AIChatResponse askQuestion(Long userId, String question, Long conversationId) {
        log.debug("处理用户问题: userId={}, conversationId={}, question={}", userId, conversationId, question);
        return ragService.processQuestion(userId, question, conversationId);
    }

    @Override
    public int updateFeedback(Long id, Integer feedback, String feedbackContent) {
        int result = aiChatMapper.updateFeedback(id, feedback);
        if (result > 0) {
            log.info("更新消息反馈成功: id={}, feedback={}", id, feedback);
            if (feedbackContent != null && !feedbackContent.isBlank()) {
                aiChatMapper.updateFeedbackContent(id, feedbackContent.trim());
                ragMetricsService.recordFeedbackReason(feedbackContent.trim());
            }
            if (feedback != null && feedback == -1) {
                aiChatMapper.updateReview(id, "PENDING_REVIEW", null, null);
                log.info("消息反馈点踩，已入审核队列: id={}", id);
            } else {
                aiChatMapper.updateReview(id, null, null, null);
            }
        } else {
            log.warn("更新消息反馈失败（记录不存在）: id={}", id);
        }
        return result;
    }

    @Override
    public PageResult<AiChat> selectPendingReviewPage(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AiChat> list = aiChatMapper.selectPendingReviewPage(offset, pageSize);
        long total = aiChatMapper.countPendingReview();
        return PageResult.of(total, list, page, pageSize);
    }

    @Override
    public int submitReview(Long id, String feedbackStatus, Long reviewedBy, String reviewNotes) {
        // 先获取完整记录，用于自动化后处理
        AiChat chat = aiChatMapper.selectById(id);
        int result = aiChatMapper.updateReview(id, feedbackStatus, reviewedBy, reviewNotes);
        if (result > 0) {
            log.info("审核完成: id={}, status={}, reviewedBy={}", id, feedbackStatus, reviewedBy);
            // 审核确认后的自动化动作
            triggerPostReviewActions(chat, feedbackStatus);
        } else {
            log.warn("审核失败（记录不存在）: id={}", id);
        }
        return result;
    }

    /**
     * 审核后的自动化动作
     */
    private void triggerPostReviewActions(AiChat chat, String feedbackStatus) {
        if (!"REVIEWED".equals(feedbackStatus) || chat == null) return;
        String question = chat.getUserQuestion();
        String feedbackContent = chat.getFeedbackContent();
        if (question == null) return;

        String lowerFeedback = feedbackContent != null ? feedbackContent.toLowerCase() : "";
        String answer = chat.getAiAnswer() != null ? chat.getAiAnswer() : "";
        String shortQuestion = question.length() > 80 ? question.substring(0, 80) + "..." : question;

        // 1. 法条引用错误 → 写入 review_log + 自动加入 Golden Dataset
        if (lowerFeedback.contains("wrong_citation") || lowerFeedback.contains("引用的法条有误")) {
            log.info("[审核后处理] 法条引用错误案例: id={} question={}", chat.getId(), shortQuestion);
            insertReviewLog(chat.getId(), question, "ADD_TO_GOLDEN_DATASET",
                    "法条引用错误:" + (feedbackContent != null ? feedbackContent : ""), feedbackContent);
            appendToGoldenDataset(question, answer, feedbackContent);
        }

        // 2. 回答不准确 / 答非所问 → 写入 review_log（知识缺口）
        if (lowerFeedback.contains("inaccurate") || lowerFeedback.contains("irrelevant")
                || lowerFeedback.contains("回答不准确") || lowerFeedback.contains("没有回答我的问题")) {
            log.info("[审核后处理] 知识缺口标记: id={} question={}", chat.getId(), shortQuestion);
            insertReviewLog(chat.getId(), question, "KNOWLEDGE_GAP",
                    "回答不准确/答非所问:" + (feedbackContent != null ? feedbackContent : ""), feedbackContent);
        }

        // 3. 任何已确认的问题 → 写入 review_log
        insertReviewLog(chat.getId(), question, "CONFIRMED_ISSUE",
                "审核确认:" + (feedbackContent != null ? feedbackContent : "未填写原因"), feedbackContent);
    }

    private void insertReviewLog(Long chatId, String question, String actionType, String detail, String reason) {
        try {
            ReviewLog logEntry = new ReviewLog();
            logEntry.setChatId(chatId);
            logEntry.setQuestion(question.length() > 500 ? question.substring(0, 500) : question);
            logEntry.setActionType(actionType);
            logEntry.setActionDetail(detail);
            logEntry.setFeedbackReason(reason);
            logEntry.setProcessed(0);
            reviewLogMapper.insert(logEntry);
        } catch (Exception e) {
            log.warn("[审核后处理] 写入 review_log 失败: {}", e.getMessage());
        }
    }

    private static final String DATASET_PATH = "docs/golden-dataset-rag-evaluation.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    private void appendToGoldenDataset(String question, String answer, String feedbackContent) {
        try {
            File file = new File(DATASET_PATH);
            if (!file.exists()) {
                log.warn("[审核后处理] Golden Dataset 文件不存在: {}", DATASET_PATH);
                return;
            }
            @SuppressWarnings("unchecked")
            var records = JSON.readValue(file, java.util.ArrayList.class);
            var newEntry = new java.util.LinkedHashMap<String, Object>();
            newEntry.put("id", "GR" + System.currentTimeMillis());
            newEntry.put("question", question);
            newEntry.put("intent", "LEGAL_CONSULTATION");
            newEntry.put("expected_law_type", null);
            newEntry.put("difficulty", "hard");
            newEntry.put("key_points", List.of("人工审核补充"));
            newEntry.put("expected_answer_contains", List.of("人工审核"));
            newEntry.put("source_requirement", "law_knowledge");
            newEntry.put("review_notes", feedbackContent != null ? feedbackContent : "法条引用错误");
            records.add(newEntry);
            JSON.writerWithDefaultPrettyPrinter().writeValue(file, records);
            log.info("[审核后处理] 已加入 Golden Dataset: id={}", newEntry.get("id"));
        } catch (Exception e) {
            log.warn("[审核后处理] 写入 Golden Dataset 失败: {}", e.getMessage());
        }
    }

    @Override
    public PageResult<AiChat> selectPage(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AiChat> list = aiChatMapper.selectPage(offset, pageSize);
        long total = aiChatMapper.countAll();
        return PageResult.of(total, list, page, pageSize);
    }

    @Override
    public PageResult<AiChat> selectByUserIdPage(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<AiChat> list = aiChatMapper.selectByUserIdPage(userId, offset, pageSize);
        long total = aiChatMapper.countByUserId(userId);
        return PageResult.of(total, list, page, pageSize);
    }

}
