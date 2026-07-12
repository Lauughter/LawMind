package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.entity.LawVectorTask;
import com.lhs.lawmind.service.*;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.JsonUtil;
import com.lhs.lawmind.utils.LawKnowledgeRedisUtil;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * 自动学习服务实现类
 * 实现助手的自动学习功能，包括从大模型回答中提取法律知识、向量化存储等
 */
@Slf4j
@Service
public class AutoLearnServiceImpl implements AutoLearnService {

    private final ChatLanguageModel chatLanguageModel;

    private final LawKnowledgeService lawKnowledgeService;

    private final AiChatService aiChatService;

    private final LawVectorTaskService lawVectorTaskService;

    private final SimilarQuestionService similarQuestionService;

    private final EmbeddingUtil embeddingUtil;

    private final LawKnowledgeRedisUtil lawKnowledgeRedisUtil;

    public AutoLearnServiceImpl(Optional<ChatLanguageModel> chatLanguageModel,
                                LawKnowledgeService lawKnowledgeService,
                                @Lazy AiChatService aiChatService,
                                LawVectorTaskService lawVectorTaskService,
                                SimilarQuestionService similarQuestionService,
                                Optional<EmbeddingUtil> embeddingUtil,
                                LawKnowledgeRedisUtil lawKnowledgeRedisUtil) {
        this.chatLanguageModel = chatLanguageModel.orElse(null);
        this.lawKnowledgeService = lawKnowledgeService;
        this.aiChatService = aiChatService;
        this.lawVectorTaskService = lawVectorTaskService;
        this.similarQuestionService = similarQuestionService;
        this.embeddingUtil = embeddingUtil.orElse(null);
        this.lawKnowledgeRedisUtil = lawKnowledgeRedisUtil;
    }

    /**
     * 处理大模型回答并提取法律知识
     * <p>从大模型回答中提取法律知识，解析并存储到数据库中</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param aiChat 包含大模型回答的聊天记录实体
     * @return void
     * @throws Exception 处理过程中可能抛出的异常
     * @see Exception
     */
    @Override
    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void processLLMAnswerAndExtractKnowledge(AiChat aiChat) {
        try {
            log.info("开始处理大模型回答并提取法律知识: chatId={}", aiChat.getId());

            // 从大模型回答中提取法律知识
            log.info("aiChat.getAiAnswer()={}", aiChat.getAiAnswer());
            String knowledgeJson = extractLegalKnowledgeFromAnswer(aiChat.getAiAnswer());
            log.info("knowledgeJson={}", knowledgeJson);
            if (knowledgeJson == null || knowledgeJson.isEmpty()) {
                log.error("未从回答中提取到法律知识: chatId={}", aiChat.getId());
                return;
            }

            // 解析提取的法律知识
            List<LawKnowledge> extractedKnowledge = JsonUtil.parseLegalKnowledgeFromJson(knowledgeJson);
            if (extractedKnowledge.isEmpty()) {
                log.error("解析法律知识失败: chatId={}", aiChat.getId());
                return;
            }

            // 计算提取的法律知识与原始问题的相似度
            if (embeddingUtil != null) {
                try {
                    // 向量化原始问题
                    float[] questionVector = embeddingUtil.embed(aiChat.getUserQuestion());

                    for (LawKnowledge knowledge : extractedKnowledge) {
                        // 向量化法律知识内容
                        float[] knowledgeVector = embeddingUtil.embed(knowledge.getContent());

                        // 计算余弦相似度
                        double similarity = calculateCosineSimilarity(questionVector, knowledgeVector);
                        knowledge.setScore(similarity);
                        log.info("计算法律知识相似度: title={}, score={}", knowledge.getTitle(), similarity);
                    }
                } catch (Exception e) {
                    log.error("计算相似度失败: error={}", e.getMessage(), e);
                }
            }

            // 存储法律知识并获取ID列表
            List<Long> knowledgeIds = new ArrayList<>();
            for (LawKnowledge knowledge : extractedKnowledge) {
                knowledge.setVectorStatus(0); // 初始向量状态为0
                knowledge.setCreateTime(new Date());
                int result = lawKnowledgeService.insert(knowledge);
                if (result > 0) {
                    knowledgeIds.add(knowledge.getId());
                    log.info("存储法律知识成功: id={}, title={}, score={}", knowledge.getId(), knowledge.getTitle(), knowledge.getScore());

                    // 创建向量任务
                    createVectorTask(knowledge.getId());
                }
            }

            if (!knowledgeIds.isEmpty()) {
                // 更新聊天记录的knowledge_match字段
                updateChatKnowledgeMatch(aiChat.getId(), extractedKnowledge);

                // 更新相似问题库的knowledgeIds字段
                updateSimilarQuestionKnowledgeIds(aiChat.getUserQuestion(), knowledgeIds);

                // 异步向量化并存储法律知识
                for (Long knowledgeId : knowledgeIds) {
                    vectorizeAndStoreKnowledge(knowledgeId);
                }
            }

            log.info("处理大模型回答并提取法律知识完成: chatId={}, 提取知识数量={}", aiChat.getId(), extractedKnowledge.size());

        } catch (Exception e) {
            log.error("处理大模型回答并提取法律知识失败: chatId={}, error={}", aiChat.getId(), e.getMessage(), e);
        }
    }

    @Override
    @Async("taskExecutor")
    public void scanAndProcessUnmatchedChats() {
        try {
            log.info("开始扫描未匹配知识的聊天记录");

            // 获取所有聊天记录
            List<AiChat> allChats = aiChatService.selectAll();
            int processedCount = 0;

            for (AiChat chat : allChats) {
                // 检查knowledge_match字段是否为[]
                if ("[]".equals(chat.getKnowledgeMatch())) {
                    log.info("处理未匹配知识的聊天记录: chatId={}", chat.getId());

                    // 重新处理这个问题，调用大模型回答
                    // 注意：这里应该使用AiChatService的askQuestion方法，但为了避免循环依赖，我们直接处理
                    processLLMAnswerAndExtractKnowledge(chat);
                    processedCount++;
                }
            }

            log.info("扫描未匹配知识的聊天记录完成，处理数量: {}", processedCount);

        } catch (Exception e) {
            log.error("扫描未匹配知识的聊天记录失败: error={}", e.getMessage(), e);
        }
    }

    /**
     * 计算两个向量的余弦相似度
     *
     * @param vector1 第一个向量
     * @param vector2 第二个向量
     * @return 余弦相似度值
     */
    private double calculateCosineSimilarity(float[] vector1, float[] vector2) {
        if (vector1 == null || vector2 == null || vector1.length != vector2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        if (norm1 == 0 || norm2 == 0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    @Override
    @Async("taskExecutor")
    @Transactional(rollbackFor = Exception.class)
    public void vectorizeAndStoreKnowledge(Long knowledgeId) {
        try {
            log.info("开始向量化并存储法律知识: id={}", knowledgeId);

            // 获取法律知识
            LawKnowledge knowledge = lawKnowledgeService.getById(knowledgeId);
            if (knowledge == null) {
                log.warn("未找到法律知识: id={}", knowledgeId);
                return;
            }

            // 生成向量
            if (embeddingUtil == null) {
                log.warn("EmbeddingUtil未初始化，跳过向量化");
                return;
            }

            // 优化向量化策略：对标题和内容一起进行向量化，提高匹配度
            String textToEmbed = knowledge.getTitle() + " " + knowledge.getContent();
            float[] vector = embeddingUtil.embed(textToEmbed);

            // 存储到Redis
            lawKnowledgeRedisUtil.storeLawKnowledge(
                    knowledge.getId(),
                    knowledge.getTitle(),
                    knowledge.getLawType(),
                    knowledge.getContent(),
                    vector
            );

            // 更新法律知识的向量状态
            knowledge.setVectorStatus(1); // 向量状态设置为1，表示已向量化
            lawKnowledgeService.update(knowledge);

            log.info("向量化并存储法律知识完成: id={}", knowledgeId);

        } catch (Exception e) {
            log.error("向量化并存储法律知识失败: id={}, error={}", knowledgeId, e.getMessage(), e);
        }
    }

    /**
     * 从大模型回答中提取法律知识
     *
     * @param answer 大模型回答
     * @return 法律知识JSON字符串
     */
    @Override
    public String extractLegalKnowledgeFromAnswer(String answer) {
        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel未初始化，无法提取法律知识");
            return "[]";
        }

        try {
            String prompt = "你是一个专业的法律知识提取助手，请从以下AI回答中提取出涉及到的法律知识，包括法律的类型、标题和内容。\n\n"
                    + "AI回答：" + answer + "\n\n"
                    + "提取要求：\n"
                    + "1. 只提取与法律相关的内容\n"
                    + "2. 每个法律知识需要包含：lawType（法律类型）、title（法律标题）、content（法律内容）\n"
                    + "3. 输出格式为JSON数组，例如：[{\"lawType\":\"劳动合同法\",\"title\":\"劳动合同的解除\",\"content\":\"用人单位与劳动者协商一致，可以解除劳动合同\"}]\n"
                    + "4. 如果没有提取到法律知识，输出空数组 []\n";

            String result = chatLanguageModel.generate(prompt);
            log.info("提取法律知识成功");
            return result;

        } catch (Exception e) {
            log.error("提取法律知识失败: error={}", e.getMessage(), e);
            return "[]";
        }
    }

    /**
     * 创建向量任务
     *
     * @param knowledgeId 法律知识ID
     */
    private void createVectorTask(Long knowledgeId) {
        try {
            LawVectorTask task = new LawVectorTask();
            task.setKnowledgeId(knowledgeId);
            task.setVectorStatus(0); // 初始状态为0，表示待处理
            task.setCreateTime(new Date());
            lawVectorTaskService.insert(task);
            log.info("创建向量任务成功: knowledgeId={}", knowledgeId);
        } catch (Exception e) {
            log.error("创建向量任务失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
        }
    }

    /**
     * 更新聊天记录的knowledge_match字段
     *
     * @param chatId 聊天记录ID
     * @param knowledgeList 法律知识列表
     */
    private void updateChatKnowledgeMatch(Long chatId, List<LawKnowledge> knowledgeList) {
        try {
            if (chatId == null || knowledgeList.isEmpty()) {
                return;
            }

            // 构建knowledge_match JSON
            String knowledgeMatch = JsonUtil.buildKnowledgeMatchJson(knowledgeList);

            // 更新聊天记录
            AiChat aiChat = new AiChat();
            aiChat.setId(chatId);
            aiChat.setKnowledgeMatch(knowledgeMatch);
            int updated = aiChatService.update(aiChat);

            if (updated > 0) {
                log.info("更新聊天记录的knowledge_match字段成功: chatId={}", chatId);
            } else {
                log.warn("更新聊天记录的knowledge_match字段失败: chatId={}", chatId);
            }

        } catch (Exception e) {
            log.error("更新聊天记录的knowledge_match字段失败: chatId={}, error={}", chatId, e.getMessage(), e);
        }
    }

    /**
     * 更新相似问题库的knowledgeIds字段
     *
     * @param question 用户问题
     * @param knowledgeIds 法律知识ID列表
     */
    private void updateSimilarQuestionKnowledgeIds(String question, List<Long> knowledgeIds) {
        try {
            if (question == null || knowledgeIds.isEmpty()) {
                return;
            }

            // 将knowledgeIds转换为逗号分隔的字符串
            StringBuilder idsStr = new StringBuilder();
            for (int i = 0; i < knowledgeIds.size(); i++) {
                idsStr.append(knowledgeIds.get(i));
                if (i < knowledgeIds.size() - 1) {
                    idsStr.append(",");
                }
            }

            // 调用相似问题服务更新knowledgeIds
            similarQuestionService.asyncUpdateSimilarQuestionKnowledgeIds(question, idsStr.toString());
            log.info("更新相似问题库的knowledgeIds字段成功: question={}", question.substring(0, Math.min(50, question.length())));

        } catch (Exception e) {
            log.error("更新相似问题库的knowledgeIds字段失败: error={}", e.getMessage(), e);
        }
    }
}
