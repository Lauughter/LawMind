package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.SimilarQuestion;
import com.lhs.lawmind.service.SimilarQuestionService;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.SimilarQuestionRedisUtil;
import com.lhs.lawmind.utils.RedisIndexUtil;
import com.lhs.lawmind.utils.RedisVectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 相似问题库服务实现类
 * 处理相似问题的存储、检索和更新
 * 该服务负责处理相似问题的存储、检索和更新操作。
 * 它与相似问题库的数据库交互，用于存储和更新相似问题的文本内容。
 * 它还与 Redis 向量工具类 RedisVectorUtil 交互，用于向量的存储和搜索。
 * 它还与 Redis 索引工具类 RedisIndexUtil 交互，用于向量的索引创建和删除。
 * 它还与相似问题库的数据库交互，用于存储和更新相似问题的向量任务状态。
 * */
@Slf4j
@Service
public class SimilarQuestionServiceImpl implements SimilarQuestionService {

    private final SimilarQuestionRedisUtil similarQuestionRedisUtil;

    private final RagConfig ragConfig;

    private final EmbeddingUtil embeddingUtil;

    private final RedisIndexUtil redisIndexUtil;

    public SimilarQuestionServiceImpl(SimilarQuestionRedisUtil similarQuestionRedisUtil,
                                      RagConfig ragConfig,
                                      Optional<EmbeddingUtil> embeddingUtil,
                                      RedisIndexUtil redisIndexUtil) {
        this.similarQuestionRedisUtil = similarQuestionRedisUtil;
        this.ragConfig = ragConfig;
        this.embeddingUtil = embeddingUtil.orElse(null);
        this.redisIndexUtil = redisIndexUtil;
    }

    /**
     * 保存相似问题到 Redis
     *
     * @param question     用户问题
     * @param answer       AI 回答
     * @param knowledgeIds 关联的知识点 ID
     */
    public void saveSimilarQuestion(String question, String answer, String knowledgeIds) {
        if (embeddingUtil == null) {
            log.warn("EmbeddingUtil未初始化，无法保存相似问题");
            return;
        }

        try {
            // 检查相似问题库向量索引是否存在
            if (!redisIndexUtil.indexExists(similarQuestionRedisUtil.getRedisTemplate(), "idx:similar_question")) {
                log.error("相似问题库向量索引不存在，无法执行向量化操作");
                return;
            }

            // 生成问题向量
            float[] vector = embeddingUtil.embed(question);

            // 创建相似问题实体
            SimilarQuestion similarQuestion = new SimilarQuestion();
            similarQuestion.setQuestion(question);
            similarQuestion.setAnswer(answer);
            similarQuestion.setKnowledgeIds(knowledgeIds);
            similarQuestion.setVisitCount(1);
            similarQuestion.setCreateTime(new Date());
            similarQuestion.setLastVisitTime(new Date());

            // 生成ID（可以使用时间戳或数据库自增ID）
            long id = System.currentTimeMillis() * 1000 + ThreadLocalRandom.current().nextInt(1000);
            similarQuestion.setId(id);

            // 存储到Redis
            similarQuestionRedisUtil.storeSimilarQuestion(similarQuestion, vector);

            log.info("保存相似问题成功: id={}, question={}", id, question.substring(0, Math.min(50, question.length())));

        } catch (Exception e) {
            log.error("保存相似问题失败: {}", e.getMessage(), e);
        }
    }



    /**
     * 异步保存相似问题
     */
    @Override
    @Async("taskExecutor")
    public void asyncSaveSimilarQuestion(String question, String answer, String knowledgeIds) {
        saveSimilarQuestion(question, answer, knowledgeIds);
    }

    /**
     * 搜索相似问题
     *
     * @param question 用户问题
     * @return 匹配的相似问题，未命中返回 null
     */
    @Override
    public SimilarQuestion searchSimilarQuestion(String question) {
        try {
            // 检查相似问题库向量索引是否存在
            if (!redisIndexUtil.indexExists(similarQuestionRedisUtil.getRedisTemplate(), "idx:similar_question")) {
                log.error("相似问题库向量索引不存在，无法执行搜索操作");
                return null;
            }

            // 生成问题向量
            float[] questionVector = embeddingUtil.embed(question);

            // 搜索相似问题
            List<RedisVectorUtil.SearchResult> results = similarQuestionRedisUtil.searchSimilarQuestions(questionVector, 1);

            if (results != null && !results.isEmpty()) {
                RedisVectorUtil.SearchResult result = results.get(0);
                // 从 Redis 中获取完整的 SimilarQuestion 对象
                String key = result.getKey();
                // 从key中提取ID
                String idStr = key.replace(ragConfig.getSimilarQuestionKeyPrefix(), "");
                try {
                    Long id = Long.parseLong(idStr);
                    return similarQuestionRedisUtil.getSimilarQuestion(id);
                } catch (NumberFormatException e) {
                    log.error("无法从key中提取ID: {}", key, e);
                    return null;
                }
            }

            return null;
        } catch (Exception e) {
            log.error("搜索相似问题失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 异步更新相似问题的knowledgeIds字段
     */
    @Override
    @Async("taskExecutor")
    public void asyncUpdateSimilarQuestionKnowledgeIds(String question, String knowledgeIds) {
        try {
            log.info("开始更新相似问题的knowledgeIds字段: question={}", question.substring(0, Math.min(50, question.length())));

            // 搜索相似问题
            SimilarQuestion similarQuestion = searchSimilarQuestion(question);
            if (similarQuestion == null) {
                log.info("未找到相似问题，跳过更新: question={}", question.substring(0, Math.min(50, question.length())));
                return;
            }

            // 更新knowledgeIds字段
            similarQuestion.setKnowledgeIds(knowledgeIds);

            // 重新生成向量
            float[] vector = embeddingUtil.embed(question);

            // 存储到Redis
            similarQuestionRedisUtil.storeSimilarQuestion(similarQuestion, vector);

            log.info("更新相似问题的knowledgeIds字段成功: id={}, knowledgeIds={}", similarQuestion.getId(), knowledgeIds);

        } catch (Exception e) {
            log.error("更新相似问题的knowledgeIds字段失败: error={}", e.getMessage(), e);
        }
    }

    /**
     * 搜索相似问题（向量方式）
     *
     * @param questionVector 问题向量
     * @param topK           返回数量
     * @return 搜索结果列表
     */
    public List<RedisVectorUtil.SearchResult> searchSimilarQuestions(float[] questionVector, int topK) {
        return similarQuestionRedisUtil.searchSimilarQuestions(questionVector, topK);
    }

    /**
     * 根据 ID 获取相似问题
     *
     * @param id 相似问题 ID
     * @return SimilarQuestion 实体
     */
    public SimilarQuestion getSimilarQuestion(Long id) {
        return similarQuestionRedisUtil.getSimilarQuestion(id);
    }
}
