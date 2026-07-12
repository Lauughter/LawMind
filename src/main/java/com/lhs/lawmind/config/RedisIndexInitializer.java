package com.lhs.lawmind.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import com.lhs.lawmind.utils.IndexStatusCache;
import com.lhs.lawmind.utils.RedisIndexUtil;

import java.util.Optional;

/**
 * Redis索引初始化器
 * 应用启动时自动创建所需的向量索引，并初始化缓存
 */
@Slf4j
@Component
public class RedisIndexInitializer implements CommandLineRunner {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RagConfig ragConfig;
    private final RedisIndexUtil redisIndexUtil;

    public RedisIndexInitializer(
            Optional<RedisTemplate<String, Object>> redisTemplate,
            RagConfig ragConfig,
            RedisIndexUtil redisIndexUtil) {
        this.redisTemplate = redisTemplate.orElse(null);
        this.ragConfig = ragConfig;
        this.redisIndexUtil = redisIndexUtil;
    }

    @Override
    public void run(String... args) {
        log.info("=== RedisIndexInitializer 开始执行 ===");
        log.info("=== 开始初始化Redis向量索引和状态缓存 ===");

        if (redisTemplate == null) {
            log.warn("RedisTemplate is null, 跳过索引初始化");
            return;
        }

        try {
            createAndCacheIndex(ragConfig.getLawVectorIndex(), this::createLawVectorIndexSchema);
            createAndCacheIndex(ragConfig.getSimilarQuestionIndex(), this::createSimilarQuestionIndexSchema);
            log.info("索引状态缓存初始化完成: {}", redisIndexUtil.getCacheStats());
            log.info("=== Redis向量索引和状态缓存初始化完成 ===");
        } catch (Exception e) {
            log.error("Redis向量索引初始化失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 创建索引并缓存状态
     */
    private void createAndCacheIndex(String indexName, Runnable schemaCreator) {
        log.info("开始处理索引: {}", indexName);

        try {
            boolean exists = indexExists(indexName);

            if (exists) {
                log.info("索引已存在，跳过创建: {}", indexName);
            } else {
                log.info("索引不存在，开始创建: {}", indexName);
                schemaCreator.run();
                exists = indexExists(indexName);
                log.info("索引创建完成: {}", indexName);
            }

            IndexStatusCache.put(indexName, exists);
            log.info("索引状态已缓存: {} = {}", indexName, exists);

        } catch (Exception e) {
            log.error("处理索引失败: {} - {}", indexName, e.getMessage(), e);
            IndexStatusCache.put(indexName, false, 60_000);
        }
    }

    /**
     * 创建法律知识库向量索引的Schema
     */
    private void createLawVectorIndexSchema() {
        String indexName = ragConfig.getLawVectorIndex();
        String keyPrefix = ragConfig.getLawVectorKeyPrefix();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try {
                Object response = connection.execute("ft.create",
                    indexName.getBytes(),
                    "ON".getBytes(),
                    "HASH".getBytes(),
                    "PREFIX".getBytes(),
                    "1".getBytes(),
                    keyPrefix.getBytes(),
                    "SCHEMA".getBytes(),
                    "title".getBytes(),
                    "TEXT".getBytes(),
                    "SORTABLE".getBytes(),
                    "law_type".getBytes(),
                    "TAG".getBytes(),
                    "SORTABLE".getBytes(),
                    "content".getBytes(),
                    "TEXT".getBytes(),
                    "vector".getBytes(),
                    "VECTOR".getBytes(),
                    "FLAT".getBytes(),
                    "6".getBytes(),
                    "TYPE".getBytes(),
                    "FLOAT32".getBytes(),
                    "DIM".getBytes(),
                    "1536".getBytes(),
                    "DISTANCE_METRIC".getBytes(),
                    "COSINE".getBytes());
                log.info("创建法律知识库向量索引成功: {}, 响应: {}", indexName, response);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Index already exists")) {
                    log.info("法律知识库向量索引已存在: {}", indexName);
                } else {
                    log.error("创建法律知识库向量索引失败: {}", e.getMessage(), e);
                    throw e;
                }
            }
            return null;
        });
    }

    /**
     * 创建相似问题库向量索引的Schema
     */
    private void createSimilarQuestionIndexSchema() {
        String indexName = ragConfig.getSimilarQuestionIndex();
        String keyPrefix = ragConfig.getSimilarQuestionKeyPrefix();

        redisTemplate.execute((RedisCallback<Void>) connection -> {
            try {
                Object response = connection.execute("ft.create",
                    indexName.getBytes(),
                    "ON".getBytes(),
                    "HASH".getBytes(),
                    "PREFIX".getBytes(),
                    "1".getBytes(),
                    keyPrefix.getBytes(),
                    "SCHEMA".getBytes(),
                    "question".getBytes(),
                    "TEXT".getBytes(),
                    "SORTABLE".getBytes(),
                    "answer".getBytes(),
                    "TEXT".getBytes(),
                    "NOINDEX".getBytes(),
                    "knowledgeIds".getBytes(),
                    "TAG".getBytes(),
                    "SEPARATOR".getBytes(),
                    ",".getBytes(),
                    "vector".getBytes(),
                    "VECTOR".getBytes(),
                    "FLAT".getBytes(),
                    "6".getBytes(),
                    "TYPE".getBytes(),
                    "FLOAT32".getBytes(),
                    "DIM".getBytes(),
                    "1536".getBytes(),
                    "DISTANCE_METRIC".getBytes(),
                    "COSINE".getBytes());

                log.info("创建相似问题库向量索引成功: {}, 响应: {}", indexName, response);
            } catch (Exception e) {
                if (e.getMessage() != null && e.getMessage().contains("Index already exists")) {
                    log.info("相似问题库向量索引已存在: {}", indexName);
                } else {
                    log.error("创建相似问题库向量索引失败: {}", e.getMessage(), e);
                    throw e;
                }
            }
            return null;
        });
    }

    /**
     * 检查索引是否已存在
     */
    private boolean indexExists(String indexName) {
        boolean exists = redisIndexUtil.indexExists(redisTemplate, indexName);
        if (exists) {
            log.info("=== 索引已存在: {}", indexName);
        } else {
            log.info("=== 索引不存在: {}", indexName);
        }
        return exists;
    }
}
