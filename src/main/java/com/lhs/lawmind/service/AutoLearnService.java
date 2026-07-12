package com.lhs.lawmind.service;

import com.lhs.lawmind.entity.AiChat;

/**
 * 自动学习服务接口
 * 实现助手的自动学习功能，包括从大模型回答中提取法律知识、向量化存储等
 */
public interface AutoLearnService {

    /**
     * 处理大模型回答，提取法律知识并存储
     * 
     * @param aiChat 聊天记录实体
     */
    void processLLMAnswerAndExtractKnowledge(AiChat aiChat);

    /**
     * 扫描未匹配知识的聊天记录并处理
     */
    void scanAndProcessUnmatchedChats();

    /**
     * 向量化并存储法律知识
     * 
     * @param knowledgeId 法律知识ID
     */
    void vectorizeAndStoreKnowledge(Long knowledgeId);

    /**
     * 从大模型回答中提取法律知识
     * 
     * @param answer 大模型回答
     * @return 提取的法律知识列表（JSON格式）
     */
    String extractLegalKnowledgeFromAnswer(String answer);
}
