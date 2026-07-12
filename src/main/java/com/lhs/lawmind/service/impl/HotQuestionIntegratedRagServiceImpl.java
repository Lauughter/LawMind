package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.HotQuestion;
import com.lhs.lawmind.service.*;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.JsonUtil;
import com.lhs.lawmind.utils.LawKnowledgeRedisUtil;
import com.lhs.lawmind.utils.SimilarQuestionRedisUtil;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 集成热点问题处理的RAG服务实现
 * 注意：当前未启用，RagServiceImpl 为主要实现
 */
@RequiredArgsConstructor
@Slf4j
public class HotQuestionIntegratedRagServiceImpl {
    
    private final ChatLanguageModel chatLanguageModel;
    private final EmbeddingUtil embeddingUtil;
    private final LawKnowledgeRedisUtil lawKnowledgeRedisUtil;
    private final SimilarQuestionRedisUtil similarQuestionRedisUtil;
    private final RagConfig ragConfig;
    private final HotQuestionHandler hotQuestionHandler;
    private final SimilarQuestionMaintenanceService similarQuestionMaintenanceService;
    private final VisitStatisticsService visitStatisticsService;
    private final AiChatService aiChatService;
    
    public AIChatResponse processQuestion(Long userId, String question) {
        log.info("开始处理用户问题: userId={}, question={}", userId, 
                question.substring(0, Math.min(50, question.length())));
        
        // 1. 问题预处理和MD5生成
        String questionMd5 = DigestUtils.md5DigestAsHex(question.getBytes());
        log.debug("问题MD5: {}", questionMd5);
        
        // 2. 查询热点缓存
        HotQuestion hotQuestion = hotQuestionHandler.handleHotQuestionQuery(questionMd5);
        if (hotQuestion != null) {
            // 热点缓存命中，直接返回
            log.info("热点缓存命中: questionMd5={}", questionMd5);
            return buildResponse(hotQuestion.getCachedAnswer(), null, null);
        }
        
        // 3. 问题向量化
        float[] questionVector = embeddingUtil.embed(question);
        if (questionVector == null || questionVector.length == 0) {
            log.error("问题向量化失败");
            String fallbackAnswer = getFallbackAnswer(question);
            return buildResponse(fallbackAnswer, null, null);
        }
        
        // 4. 检索相似问题库
        var similarResults = similarQuestionRedisUtil.searchSimilarQuestions(questionVector, ragConfig.getSearchTopK());
        
        if (!similarResults.isEmpty()) {
            // 相似问题命中，通过key获取详细信息
            log.info("相似问题命中: 匹配数量={}", similarResults.size());
            return buildResponse("相似问题命中", null, null);
        }
        
        // 5. 检索法律知识库
        var knowledgeResults = lawKnowledgeRedisUtil.searchLawKnowledge(questionVector, ragConfig.getSearchTopK());
        
        String finalAnswer;
        List<Object> relatedKnowledgeList = null;
        
        if (!knowledgeResults.isEmpty()) {
            // 知识库命中，生成基于知识的回答
            log.info("法律知识库命中: 匹配数量={}", knowledgeResults.size());
            finalAnswer = generateAnswerWithKnowledge(question, knowledgeResults);
        } else {
            // 知识库未命中，大模型兜底回答
            log.info("法律知识库未命中，使用大模型兜底");
            finalAnswer = getFallbackAnswer(question);
        }
        
        // 6. 异步处理：访问统计和热点升级
        asyncHandleVisitAndUpgrade(questionMd5, question, finalAnswer, null);
        
        // 7. 异步处理：存储到相似问题库
        asyncStoreToSimilarQuestionLibrary(question, finalAnswer, null);
        
        return buildResponse(finalAnswer, relatedKnowledgeList, null);
    }
    
    /**
     * 构造AI回答响应
     */
    private AIChatResponse buildResponse(String answer, List<Object> relatedKnowledge, Long chatId) {
        AIChatResponse response = new AIChatResponse();
        response.setAnswer(answer);
        response.setRelatedKnowledge(relatedKnowledge);
        response.setChatId(chatId);
        return response;
    }
    
    /**
     * 基于法律知识生成回答
     */
    private String generateAnswerWithKnowledge(String question, List<?> knowledgeResults) {
        try {
            StringBuilder prompt = new StringBuilder();
            prompt.append("基于以下法律知识回答用户问题：\n\n");
            prompt.append("用户问题：").append(question).append("\n\n");
            prompt.append("相关法律知识：\n");
            
            for (int i = 0; i < Math.min(3, knowledgeResults.size()); i++) {
                Object knowledge = knowledgeResults.get(i);
                prompt.append(i + 1).append(". ").append(knowledge.toString()).append("\n");
            }
            
            prompt.append("\n请根据上述法律知识，给出专业、准确的回答。");
            
            return chatLanguageModel.generate(prompt.toString());
            
        } catch (Exception e) {
            log.error("基于知识生成回答失败", e);
            return getFallbackAnswer(question);
        }
    }
    
    /**
     * 获取兜底回答
     */
    private String getFallbackAnswer(String question) {
        try {
            String prompt = "请回答以下法律相关问题：\n\n" + question;
            return chatLanguageModel.generate(prompt);
        } catch (Exception e) {
            log.error("获取兜底回答失败", e);
            return "抱歉，暂时无法回答您的问题。请稍后重试或联系人工客服。";
        }
    }
    
    /**
     * 异步处理访问统计和热点升级
     */
    private void asyncHandleVisitAndUpgrade(String questionMd5, String question, 
                                          String answer, String knowledgeMatch) {
        try {
            hotQuestionHandler.handleVisitAndUpgrade(questionMd5, question, answer, knowledgeMatch);
        } catch (Exception e) {
            log.error("异步处理访问统计失败: questionMd5={}", questionMd5, e);
        }
    }
    
    /**
     * 异步存储到相似问题库
     */
    private void asyncStoreToSimilarQuestionLibrary(String question, String answer, String knowledgeMatch) {
        try {
            similarQuestionMaintenanceService.asyncStoreToSimilarQuestionLibrary(
                question, answer, knowledgeMatch);
        } catch (Exception e) {
            log.error("异步存储相似问题失败: question={}", question, e);
        }
    }
}
