package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.service.AutoLearnService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.service.SimilarQuestionService;
import com.lhs.lawmind.utils.JsonUtil;
import com.lhs.lawmind.utils.LawKnowledgeRedisUtil;
import com.lhs.lawmind.utils.TextPreprocessUtil;
import com.lhs.lawmind.utils.VisitStatsUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * RAG 异步持久化服务
 * <p>
 * 从 RagServiceImpl 拆出，解决 @Async 自调用绕过代理的问题。
 * 所有方法均由 Spring AOP 代理触发，确保真正异步执行。
 * </p>
 */
@Slf4j
@Service
public class RagPersistenceService {

    private static final double COST_PER_1K_INPUT_TOKENS = 0.0008;
    private static final double COST_PER_1K_OUTPUT_TOKENS = 0.002;

    private final AiChatService aiChatService;
    private final VisitStatsUtil visitStatsUtil;
    private final AutoLearnService autoLearnService;
    private final SimilarQuestionService similarQuestionService;
    private final LawKnowledgeRedisUtil lawKnowledgeRedisUtil;
    private final LawKnowledgeService lawKnowledgeService;

    public RagPersistenceService(
            @Lazy AiChatService aiChatService,
            VisitStatsUtil visitStatsUtil,
            @Lazy AutoLearnService autoLearnService,
            SimilarQuestionService similarQuestionService,
            LawKnowledgeRedisUtil lawKnowledgeRedisUtil,
            LawKnowledgeService lawKnowledgeService) {
        this.aiChatService = aiChatService;
        this.visitStatsUtil = visitStatsUtil;
        this.autoLearnService = autoLearnService;
        this.similarQuestionService = similarQuestionService;
        this.lawKnowledgeRedisUtil = lawKnowledgeRedisUtil;
        this.lawKnowledgeService = lawKnowledgeService;
    }

    @Async("taskExecutor")
    public Long asyncLogVisit(Long userId, String question, String answer, String knowledgeMatch,
                              String source, Long conversationId) {
        return saveChatRecord(userId, question, answer, knowledgeMatch, source, conversationId, 0, 0);
    }

    @Async("taskExecutor")
    public Long asyncLogVisit(Long userId, String question, String answer, String knowledgeMatch,
                              String source, Long conversationId, int tokenInput, int tokenOutput) {
        return saveChatRecord(userId, question, answer, knowledgeMatch, source, conversationId, tokenInput, tokenOutput);
    }

    public Long saveChatRecord(Long userId, String question, String answer, String knowledgeMatch,
                               String source, Long conversationId, int tokenInput, int tokenOutput) {
        try {
            AiChat aiChat = new AiChat();
            aiChat.setUserId(userId);
            aiChat.setConversationId(conversationId);
            aiChat.setUserQuestion(question);
            aiChat.setAiAnswer(answer);
            aiChat.setKnowledgeMatch(knowledgeMatch);
            aiChat.setTokenUsageInput(tokenInput);
            aiChat.setTokenUsageOutput(tokenOutput);
            aiChat.setEstimatedCost(calculateCost(tokenInput, tokenOutput));
            aiChat.setCreateTime(new Date());

            aiChatService.insert(aiChat);
            log.info("[聊天记录] 保存成功: chatId={}, source={}", aiChat.getId(), source);

            TextPreprocessUtil.PreprocessResult preprocessResult = TextPreprocessUtil.preprocessAndGenerateMD5(question);
            visitStatsUtil.incrementVisitCount(preprocessResult.getMd5());

            if ("llm_direct".equals(source)) {
                log.info("大模型直接回答，调用自动学习服务提取法律知识: chatId={}", aiChat.getId());
                autoLearnService.processLLMAnswerAndExtractKnowledge(aiChat);
            }

            return aiChat.getId();

        } catch (Exception e) {
            log.error("[聊天记录] 保存失败: source={}, error={}", source, e.getMessage(), e);
            return null;
        }
    }

    @Async("taskExecutor")
    public void asyncUpdateSimilarQuestion(String question, String answer, String knowledgeIds, float[] questionVector) {
        try {
            similarQuestionService.asyncSaveSimilarQuestion(question, answer, knowledgeIds);
            log.info("异步更新相似问题库完成: question={}, knowledgeIds={}",
                question.substring(0, Math.min(50, question.length())), knowledgeIds);
        } catch (Exception e) {
            log.error("异步更新相似问题库失败: {}", e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    public void asyncInsertQuestionAndKnowledge(Long userId, String question, String answer,
                                                 String knowledgeIds, Long conversationId) {
        try {
            if (userId == null || question == null || answer == null || knowledgeIds == null || knowledgeIds.isEmpty()) {
                log.warn("参数为空，跳过插入");
                return;
            }

            log.info("开始异步插入问题和法律知识到聊天记录: userId={}, knowledgeIds={}", userId, knowledgeIds);

            String[] idArray = knowledgeIds.split(",");
            List<LawKnowledge> relatedKnowledge = new ArrayList<>();

            for (String idStr : idArray) {
                try {
                    Long knowledgeId = Long.parseLong(idStr.trim());
                    LawKnowledgeRedisUtil.LawKnowledge redisKnowledge = lawKnowledgeRedisUtil.getLawKnowledge(knowledgeId);
                    if (redisKnowledge != null) {
                        LawKnowledge knowledge = new LawKnowledge();
                        knowledge.setId(redisKnowledge.getId());
                        knowledge.setTitle(redisKnowledge.getTitle());
                        knowledge.setLawType(redisKnowledge.getLawType());
                        knowledge.setContent(redisKnowledge.getContent());
                        relatedKnowledge.add(knowledge);
                        log.info("从Redis获取法律知识成功: id={}", knowledgeId);
                    } else {
                        LawKnowledge knowledge = lawKnowledgeService.getById(knowledgeId);
                        if (knowledge != null) {
                            relatedKnowledge.add(knowledge);
                            log.info("从MySQL获取法律知识成功: id={}", knowledgeId);
                        } else {
                            log.warn("未找到法律知识: id={}", knowledgeId);
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的知识点ID: {}", idStr);
                }
            }

            String knowledgeMatch = JsonUtil.buildKnowledgeMatchJson(relatedKnowledge);

            AiChat aiChat = new AiChat();
            aiChat.setUserId(userId);
            aiChat.setConversationId(conversationId);
            aiChat.setUserQuestion(question);
            aiChat.setAiAnswer(answer);
            aiChat.setKnowledgeMatch(knowledgeMatch);
            aiChat.setCreateTime(new Date());
            aiChatService.insert(aiChat);

            log.info("异步插入问题和法律知识到聊天记录完成: chatId={}, 知识数量={}", aiChat.getId(), relatedKnowledge.size());

            TextPreprocessUtil.PreprocessResult preprocessResult = TextPreprocessUtil.preprocessAndGenerateMD5(question);
            visitStatsUtil.incrementVisitCount(preprocessResult.getMd5());

        } catch (Exception e) {
            log.error("异步插入问题和法律知识到聊天记录失败: {}", e.getMessage(), e);
        }
    }

    @Async("taskExecutor")
    public void asyncUpdateKnowledgeToChatRecord(Long chatId, String knowledgeIds) {
        try {
            if (chatId == null || knowledgeIds == null || knowledgeIds.isEmpty()) {
                log.warn("聊天记录ID或知识点ID为空，跳过更新");
                return;
            }

            log.info("开始异步更新法律知识到聊天记录: chatId={}, knowledgeIds={}", chatId, knowledgeIds);

            String[] idArray = knowledgeIds.split(",");
            List<LawKnowledge> relatedKnowledge = new ArrayList<>();

            for (String idStr : idArray) {
                try {
                    Long knowledgeId = Long.parseLong(idStr.trim());
                    LawKnowledgeRedisUtil.LawKnowledge redisKnowledge = lawKnowledgeRedisUtil.getLawKnowledge(knowledgeId);
                    if (redisKnowledge != null) {
                        LawKnowledge knowledge = new LawKnowledge();
                        knowledge.setId(redisKnowledge.getId());
                        knowledge.setTitle(redisKnowledge.getTitle());
                        knowledge.setLawType(redisKnowledge.getLawType());
                        knowledge.setContent(redisKnowledge.getContent());
                        relatedKnowledge.add(knowledge);
                        log.info("从Redis获取法律知识成功: id={}", knowledgeId);
                    } else {
                        LawKnowledge knowledge = lawKnowledgeService.getById(knowledgeId);
                        if (knowledge != null) {
                            relatedKnowledge.add(knowledge);
                            log.info("从MySQL获取法律知识成功: id={}", knowledgeId);
                        } else {
                            log.warn("未找到法律知识: id={}", knowledgeId);
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("无效的知识点ID: {}", idStr);
                }
            }

            String knowledgeMatch = JsonUtil.buildKnowledgeMatchJson(relatedKnowledge);

            AiChat aiChat = new AiChat();
            aiChat.setId(chatId);
            aiChat.setKnowledgeMatch(knowledgeMatch);
            int updated = aiChatService.update(aiChat);

            if (updated > 0) {
                log.info("异步更新法律知识到聊天记录完成: chatId={}, 知识数量={}", chatId, relatedKnowledge.size());
            } else {
                log.warn("更新聊天记录失败: chatId={}", chatId);
            }

        } catch (Exception e) {
            log.error("异步更新法律知识到聊天记录失败: {}", e.getMessage(), e);
        }
    }

    static BigDecimal calculateCost(int inputTokens, int outputTokens) {
        double cost = (inputTokens / 1000.0) * COST_PER_1K_INPUT_TOKENS
                + (outputTokens / 1000.0) * COST_PER_1K_OUTPUT_TOKENS;
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }
}
