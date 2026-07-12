package com.lhs.lawmind.utils;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.SimilarQuestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 相似问题库 Redis 工具类
 * 负责相似问题在 Redis 中的 CRUD 及向量检索
 */
@Slf4j
@Component
public class SimilarQuestionRedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagConfig ragConfig;
    private final RedisVectorUtil redisVectorUtil;
    private final RedisIndexUtil redisIndexUtil;

    public SimilarQuestionRedisUtil(
            Optional<RedisTemplate<String, Object>> redisTemplate,
            RagConfig ragConfig,
            RedisVectorUtil redisVectorUtil,
            RedisIndexUtil redisIndexUtil) {
        this.redisTemplate = redisTemplate.orElse(null);
        this.ragConfig = ragConfig;
        this.redisVectorUtil = redisVectorUtil;
        this.redisIndexUtil = redisIndexUtil;
    }

    public RedisTemplate<String, Object> getRedisTemplate() {
        return redisTemplate;
    }

    /**
     * 存储相似问题到 Redis（所有字段统一使用原始字节存储，避免序列化不一致）
     * 写入前检查向量索引是否存在，避免先写数据后建索引导致索引失效
     */
    public void storeSimilarQuestion(SimilarQuestion similarQuestion, float[] vector) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法存储相似问题");
            return;
        }

        if (!redisIndexUtil.indexExists(redisTemplate, ragConfig.getSimilarQuestionIndex())) {
            log.error("相似问题库向量索引不存在，无法存储相似问题: index={}", ragConfig.getSimilarQuestionIndex());
            return;
        }

        try {
            String key = ragConfig.getSimilarQuestionKeyPrefix() + similarQuestion.getId();
            byte[] vectorBytes = RedisVectorUtil.floatArrayToBytes(vector);
            byte[] questionBytes = similarQuestion.getQuestion() != null ? similarQuestion.getQuestion().getBytes() : new byte[0];
            byte[] answerBytes = similarQuestion.getAnswer() != null ? similarQuestion.getAnswer().getBytes() : new byte[0];
            byte[] knowledgeIdsBytes = similarQuestion.getKnowledgeIds() != null ? similarQuestion.getKnowledgeIds().getBytes() : new byte[0];

            redisTemplate.execute((RedisCallback<Void>) connection -> {
                byte[] kb = key.getBytes();
                connection.hSet(kb, "vector".getBytes(), vectorBytes);
                connection.hSet(kb, "question".getBytes(), questionBytes);
                connection.hSet(kb, "answer".getBytes(), answerBytes);
                connection.hSet(kb, "knowledgeIds".getBytes(), knowledgeIdsBytes);
                return null;
            });

            log.info("存储相似问题成功: {}", key);
        } catch (Exception e) {
            log.error("存储相似问题失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 从 Redis 获取相似问题（统一使用原始字节读取）
     */
    public SimilarQuestion getSimilarQuestion(Long id) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法获取相似问题");
            return null;
        }

        try {
            String key = ragConfig.getSimilarQuestionKeyPrefix() + id;

            byte[][] result = redisTemplate.execute((RedisCallback<byte[][]>) connection -> {
                byte[] keyBytes = key.getBytes();
                byte[] questionBytes = connection.hGet(keyBytes, "question".getBytes());
                byte[] answerBytes = connection.hGet(keyBytes, "answer".getBytes());
                byte[] knowledgeIdsBytes = connection.hGet(keyBytes, "knowledgeIds".getBytes());
                return new byte[][]{questionBytes, answerBytes, knowledgeIdsBytes};
            });

            if (result == null || (result[0] == null && result[1] == null)) {
                return null;
            }

            SimilarQuestion question = new SimilarQuestion();
            question.setId(id);
            question.setQuestion(result[0] != null ? new String(result[0]) : "");
            question.setAnswer(result[1] != null ? new String(result[1]) : "");
            question.setKnowledgeIds(result[2] != null ? new String(result[2]) : "");
            return question;
        } catch (Exception e) {
            log.error("获取相似问题失败: id={}, error={}", id, e.getMessage());
            return null;
        }
    }

    /**
     * 向量搜索相似问题（委托给 RedisVectorUtil）
     */
    public List<RedisVectorUtil.SearchResult> searchSimilarQuestions(float[] questionVector, int topK) {
        if (redisTemplate == null) return new ArrayList<>();
        return redisVectorUtil.searchSimilar(ragConfig.getSimilarQuestionIndex(), questionVector, topK,
                ragConfig.getSimilarQuestionKeyPrefix());
    }
}
