package com.lhs.lawmind.agent.memory;

import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.RedisVectorUtil;
import com.lhs.lawmind.utils.RedisVectorUtil.SearchResult;
import com.lhs.lawmind.utils.RedisIndexUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 记忆存储层 —— MySQL CRUD + Redis 向量索引。
 */
@Slf4j
@Component
public class MemoryStore {

    private static final String MEMORY_INDEX = "idx:memory";
    private static final String MEMORY_KEY_PREFIX = "memory:vector:";

    private final AiMemoryMapper mapper;
    private final EmbeddingUtil embeddingUtil;
    private final RedisVectorUtil redisVectorUtil;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisIndexUtil redisIndexUtil;

    public MemoryStore(AiMemoryMapper mapper,
                       EmbeddingUtil embeddingUtil,
                       RedisVectorUtil redisVectorUtil,
                       RedisTemplate<String, Object> redisTemplate,
                       RedisIndexUtil redisIndexUtil) {
        this.mapper = mapper;
        this.embeddingUtil = embeddingUtil;
        this.redisVectorUtil = redisVectorUtil;
        this.redisTemplate = redisTemplate;
        this.redisIndexUtil = redisIndexUtil;
    }

    // ---- MySQL CRUD ----

    public AiMemory save(AiMemory memory) {
        if (memory.getImportance() == null) memory.setImportance(0.5);
        if (memory.getConfidence() == null) memory.setConfidence(0.5);
        if (memory.getAccessCount() == null) memory.setAccessCount(0);
        mapper.insert(memory);
        // 向量化并写入 Redis
        embedAndStore(memory);
        return memory;
    }

    public AiMemory findById(Long id) {
        return mapper.selectById(id);
    }

    public List<AiMemory> findByUserId(Long userId) {
        return mapper.selectByUserId(userId);
    }

    public List<AiMemory> findByUserIdAndType(Long userId, MemoryType type) {
        return mapper.selectByUserIdAndType(userId, type.name());
    }

    public List<AiMemory> findTopByImportance(Long userId, int limit) {
        return mapper.selectTopByImportance(userId, limit);
    }

    public void update(AiMemory memory) {
        mapper.update(memory);
        // 更新 Redis 向量（如果 embedding 变了）
        if (memory.getEmbedding() != null) {
            embedAndStore(memory);
        }
    }

    public void updateAccessInfo(Long id) {
        mapper.updateAccessInfo(id);
    }

    public boolean deleteById(Long id, Long userId) {
        int rows = mapper.deleteById(id, userId);
        if (rows > 0) {
            deleteVector(id);
        }
        return rows > 0;
    }

    public int deleteByUserId(Long userId) {
        List<AiMemory> memories = mapper.selectByUserId(userId);
        for (AiMemory m : memories) {
            deleteVector(m.getId());
        }
        return mapper.deleteByUserId(userId);
    }

    public long countByUserId(Long userId) {
        return mapper.countByUserId(userId);
    }

    // ---- 衰减与清理 ----

    public List<AiMemory> findForDecay(Long userId, MemoryType type, String beforeDate) {
        return mapper.selectForDecay(userId, type.name(), beforeDate);
    }

    public void updateImportance(Long id, double importance) {
        mapper.updateImportance(id, importance);
    }

    public List<AiMemory> findLowestImportance(Long userId, int limit) {
        return mapper.selectLowestImportance(userId, limit);
    }

    // ---- Redis 向量操作 ----

    private void embedAndStore(AiMemory memory) {
        try {
            float[] vector = embeddingUtil.embed(memory.getBody());
            if (vector == null || vector.length == 0) return;
            memory.setEmbedding(arrayToJson(vector));
            // 更新 MySQL 中的向量
            mapper.update(memory);
            // 写入 Redis
            String key = MEMORY_KEY_PREFIX + memory.getId();
            byte[] vectorBytes = RedisVectorUtil.floatArrayToBytes(vector);
            redisTemplate.opsForHash().put(key, "vector", vectorBytes);
            redisTemplate.opsForHash().put(key, "memory_id",
                    String.valueOf(memory.getId()).getBytes());
        } catch (Exception e) {
            log.error("记忆向量化失败: id={}, error={}", memory.getId(), e.getMessage());
        }
    }

    private void deleteVector(Long memoryId) {
        redisVectorUtil.deleteVector(MEMORY_KEY_PREFIX + memoryId);
    }

    /**
     * 向量语义检索 —— 返回记忆 ID + 相似度。
     */
    public List<Long> searchByVector(float[] queryVector, MemoryType typeFilter, int topK, double minSimilarity) {
        if (queryVector == null || queryVector.length == 0) return Collections.emptyList();
        try {
            List<SearchResult> results = redisVectorUtil.searchSimilar(
                    MEMORY_INDEX, queryVector, topK, MEMORY_KEY_PREFIX);
            List<Long> memoryIds = new ArrayList<>();
            for (SearchResult result : results) {
                double similarity = RedisVectorUtil.cosineDistanceToSimilarity(result.getScore());
                if (similarity < minSimilarity) continue;
                String key = result.getKey();
                if (key == null) continue;
                // 从 key 中提取 memory ID：memory:vector:{id}
                String idStr = key.substring(MEMORY_KEY_PREFIX.length());
                try {
                    long id = Long.parseLong(idStr);
                    // 如果指定了类型过滤，检查类型
                    if (typeFilter != null) {
                        AiMemory memory = mapper.selectById(id);
                        if (memory == null || memory.getType() != typeFilter) continue;
                    }
                    memoryIds.add(id);
                } catch (NumberFormatException e) {
                    log.debug("无法解析记忆 ID: {}", idStr);
                }
            }
            return memoryIds;
        } catch (Exception e) {
            log.error("向量搜索失败: error={}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 确保记忆向量索引存在。
     */
    public void ensureIndex() {
        if (redisIndexUtil.indexExists(redisTemplate, MEMORY_INDEX)) {
            log.info("记忆向量索引已存在: {}", MEMORY_INDEX);
            return;
        }
        log.info("创建记忆向量索引: {}", MEMORY_INDEX);
        try {
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Void>) connection -> {
                connection.execute("FT.CREATE",
                        MEMORY_INDEX.getBytes(),
                        "ON".getBytes(), "HASH".getBytes(),
                        "PREFIX".getBytes(), "1".getBytes(), MEMORY_KEY_PREFIX.getBytes(),
                        "SCHEMA".getBytes(),
                        "memory_id".getBytes(), "TEXT".getBytes(),
                        "vector".getBytes(), "VECTOR".getBytes(),
                        "FLAT".getBytes(), "6".getBytes(),
                        "TYPE".getBytes(), "FLOAT32".getBytes(),
                        "DIM".getBytes(), "1536".getBytes(),
                        "DISTANCE_METRIC".getBytes(), "COSINE".getBytes());
                return null;
            });
            log.info("记忆向量索引创建成功: {}", MEMORY_INDEX);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("Index already exists")) {
                log.info("记忆向量索引已存在: {}", MEMORY_INDEX);
            } else {
                log.warn("创建记忆向量索引失败（非致命）: {}", e.getMessage());
            }
        }
    }

    private static String arrayToJson(float[] array) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(array[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
