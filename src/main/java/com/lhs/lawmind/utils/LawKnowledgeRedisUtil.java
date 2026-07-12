package com.lhs.lawmind.utils;

import com.lhs.lawmind.config.RagConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 法律知识库 Redis 工具类
 * 负责法律知识在 Redis 中的 CRUD 及向量检索
 */
@Slf4j
@Component
public class LawKnowledgeRedisUtil {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagConfig ragConfig;
    private final RedisVectorUtil redisVectorUtil;
    private final RedisIndexUtil redisIndexUtil;

    // 构造函数注入 RedisTemplate、RagConfig、RedisVectorUtil 和 RedisIndexUtil
    public LawKnowledgeRedisUtil(
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
     * 存储法律知识到 Redis
     * 写入前检查向量索引是否存在，避免先写数据后建索引导致索引失效
     */
    public void storeLawKnowledge(Long knowledgeId, String title, String lawType, String content, float[] vector) {
        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 无法存储法律知识");
            return;
        }

        if (!redisIndexUtil.indexExists(redisTemplate, ragConfig.getLawVectorIndex())) {
            log.error("法律知识库向量索引不存在，无法存储法律知识: index={}", ragConfig.getLawVectorIndex());
            return;
        }

        try {
            String key = ragConfig.getLawVectorKeyPrefix() + knowledgeId;
            redisVectorUtil.storeVectorWithMetadata(key, vector, lawType, title, content,
                    null, null, null, null);
        } catch (Exception e) {
            log.error("存储法律知识失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
    }

    /**
     * 从 Redis 获取法律知识
     */
    public LawKnowledge getLawKnowledge(Long knowledgeId) {
        if (redisTemplate == null) return null;

        try {
            String key = ragConfig.getLawVectorKeyPrefix() + knowledgeId;

            byte[][] results = redisTemplate.execute((RedisCallback<byte[][]>) connection -> {
                byte[] keyBytes = key.getBytes();
                byte[][] fields = {
                        "title".getBytes(), "law_type".getBytes(),
                        "content".getBytes(), "vector".getBytes(),
                        "chapter".getBytes(), "section".getBytes(),
                        "article_number".getBytes()
                };
                byte[][] values = new byte[7][];
                for (int i = 0; i < fields.length; i++) {
                    values[i] = connection.hGet(keyBytes, fields[i]);
                }
                return values;
            });

            if (results == null) return null;
            if (results[0] == null && results[2] == null && results[3] == null) return null;

            LawKnowledge knowledge = new LawKnowledge();
            knowledge.setId(knowledgeId);
            knowledge.setTitle(results[0] != null ? new String(results[0]) : "");
            knowledge.setLawType(results[1] != null ? new String(results[1]) : "");
            knowledge.setContent(results[2] != null ? new String(results[2]) : "");
            knowledge.setVector(results[3]);
            knowledge.setChapter(results[4] != null ? new String(results[4]) : null);
            knowledge.setSection(results[5] != null ? new String(results[5]) : null);
            if (results[6] != null) {
                try { knowledge.setArticleNumber(Integer.parseInt(new String(results[6]))); } catch (NumberFormatException ignored) {}
            }
            return knowledge;
        } catch (Exception e) {
            log.error("获取法律知识失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
            return null;
        }
    }

    /**
     * 向量搜索法律知识（委托给 RedisVectorUtil）
     */
    public List<RedisVectorUtil.SearchResult> searchLawKnowledge(float[] vector, int topK) {
        if (redisTemplate == null) return new ArrayList<>();
        return redisVectorUtil.searchSimilar(ragConfig.getLawVectorIndex(), vector, topK,
                ragConfig.getLawVectorKeyPrefix());
    }

    /**
     * 法律知识数据类
     */
    public static class LawKnowledge {
        private Long id;
        private String title;
        private String lawType;
        private String content;
        private String chapter;
        private String section;
        private Integer articleNumber;
        private String status;
        private byte[] vector;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getLawType() { return lawType; }
        public void setLawType(String lawType) { this.lawType = lawType; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public String getChapter() { return chapter; }
        public void setChapter(String chapter) { this.chapter = chapter; }
        public String getSection() { return section; }
        public void setSection(String section) { this.section = section; }
        public Integer getArticleNumber() { return articleNumber; }
        public void setArticleNumber(Integer articleNumber) { this.articleNumber = articleNumber; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public byte[] getVector() { return vector; }
        public void setVector(byte[] vector) { this.vector = vector; }
    }
}
