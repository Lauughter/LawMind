package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.aop.annotation.NoLog;
import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.KnowledgeChunk;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.entity.LawVectorTask;
import com.lhs.lawmind.mapper.KnowledgeChunkMapper;
import com.lhs.lawmind.mapper.LawKnowledgeMapper;
import com.lhs.lawmind.mapper.LawVectorTaskMapper;
import com.lhs.lawmind.service.LawVectorService;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.RedisIndexUtil;
import com.lhs.lawmind.utils.RedisVectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class LawVectorServiceImpl implements LawVectorService {

    private static final String LOCK_KEY_PREFIX = "law:vector:lock:";
    private static final String CHUNK_LOCK_KEY_PREFIX = "law:chunk:lock:";
    private static final String CHUNK_KEY_PREFIX = "law:vector:chunk:";
    private static final long LOCK_EXPIRE = 30;

    private final RedisTemplate<String, Object> redisTemplate;
    private final LawKnowledgeMapper lawKnowledgeMapper;
    private final LawVectorTaskMapper lawVectorTaskMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final EmbeddingUtil embeddingUtil;
    private final RedisVectorUtil redisVectorUtil;
    private final RedisIndexUtil redisIndexUtil;
    private final RagConfig ragConfig;

    /**
     * 构造函数，注入依赖
     * <p>使用构造函数注入RedisTemplate、LawKnowledgeMapper、LawVectorTaskMapper、KnowledgeChunkMapper、EmbeddingUtil、RedisVectorUtil、RedisIndexUtil和RagConfig等依赖</p>
     * <p>确保服务的各个组件能够正确协作完成法律知识向量化和分块向量化的工作</p>
     * @param redisTemplate Redis操作模板
     * @param lawKnowledgeMapper 法律知识数据库操作对象
     * @param lawVectorTaskMapper 法律知识向量化任务数据库操作对象
     * @param knowledgeChunkMapper 知识块数据库操作对象
     * @param embeddingUtil 向量生成工具
     * @param redisVectorUtil 向量存储工具
     * @param redisIndexUtil 索引工具
     * @param ragConfig RAG配置对象
     */
    public LawVectorServiceImpl(RedisTemplate<String, Object> redisTemplate,
                                LawKnowledgeMapper lawKnowledgeMapper,
                                LawVectorTaskMapper lawVectorTaskMapper,
                                KnowledgeChunkMapper knowledgeChunkMapper,
                                EmbeddingUtil embeddingUtil,
                                RedisVectorUtil redisVectorUtil,
                                RedisIndexUtil redisIndexUtil,
                                RagConfig ragConfig) {
        this.redisTemplate = redisTemplate;
        this.lawKnowledgeMapper = lawKnowledgeMapper;
        this.lawVectorTaskMapper = lawVectorTaskMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.embeddingUtil = embeddingUtil;
        this.redisVectorUtil = redisVectorUtil;
        this.redisIndexUtil = redisIndexUtil;
        this.ragConfig = ragConfig;
    }

    /**
     * 向量化方法
     * <p>根据法律知识ID进行向量化处理，包含分布式锁控制、向量生成、Redis存储和状态更新等步骤</p>
     * <p>使用事务管理确保数据一致性，任何步骤失败都会回滚整个操作</p>
     *
     * @param knowledgeId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @NoLog
    public boolean vectorize(Long knowledgeId) {
        if (redisTemplate == null) {
            log.error("[向量化] Redis 不可用: knowledgeId={}", knowledgeId);
            return false;
        }

        // 使用分布式锁控制同一知识库的并发向量化操作，避免重复处理
        String lockKey = LOCK_KEY_PREFIX + knowledgeId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCK", LOCK_EXPIRE, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            log.warn("[向量化] 已加锁，跳过: knowledgeId={}", knowledgeId);
            return false;
        }

        LawKnowledge knowledge = null;
        try {
            knowledge = lawKnowledgeMapper.selectById(knowledgeId);
            if (knowledge == null) {
                updateTaskStatusToFailed(knowledgeId, "知识库数据不存在");
                return false;
            }

            String redisKey = ragConfig.getLawVectorKeyPrefix() + knowledgeId;
            Boolean redisExists = redisTemplate.hasKey(redisKey);
            if (redisExists != null && redisExists) {
                if (knowledge.getVectorStatus() != 1) {
                    knowledge.setVectorStatus(1);
                    lawKnowledgeMapper.update(knowledge);
                }
                upsertTaskRecord(knowledgeId, 1, null);
                return true;
            }

            if (!redisIndexUtil.indexExists(redisTemplate, "idx:law_knowledge")) {
                knowledge.setVectorStatus(2);
                lawKnowledgeMapper.update(knowledge);
                updateTaskStatusToFailed(knowledgeId, "法律知识库向量索引不存在");
                return false;
            }

            float[] vector = embeddingUtil.embed(knowledge.getContent());
            if (vector == null || vector.length == 0) {
                knowledge.setVectorStatus(2);
                lawKnowledgeMapper.update(knowledge);
                updateTaskStatusToFailed(knowledgeId, "向量生成失败，返回空");
                return false;
            }

            String lawType = knowledge.getLawType();
            if (lawType == null || lawType.isEmpty()) {
                lawType = "其他";
                knowledge.setLawType(lawType);
            }

            String enrichedContent = knowledge.getContent();
            if (knowledge.getTitle() != null && !knowledge.getTitle().isBlank()) {
                enrichedContent = "[" + knowledge.getTitle() + "]\n" + knowledge.getContent();
            }
            try {
                redisVectorUtil.storeVectorWithMetadata(redisKey, vector, lawType, knowledge.getTitle(),
                        enrichedContent, knowledge.getChapter(), knowledge.getSection(),
                        knowledge.getArticleNumber(), null);
            } catch (Exception e) {
                knowledge.setVectorStatus(2);
                lawKnowledgeMapper.update(knowledge);
                updateTaskStatusToFailed(knowledgeId, "存储向量到Redis失败：" + e.getMessage());
                return false;
            }

            knowledge.setVectorStatus(1);
            lawKnowledgeMapper.update(knowledge);

            upsertTaskRecord(knowledgeId, 1, null);

            log.info("[向量化] 成功: knowledgeId={}", knowledgeId);
            return true;
        } catch (Exception e) {
            log.error("[向量化] 失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            if (knowledge != null) {
                try {
                    knowledge.setVectorStatus(2);
                    lawKnowledgeMapper.update(knowledge);
                } catch (Exception ex) {
                    log.error("[向量化] 更新失败状态出错: knowledgeId={}", knowledgeId, ex);
                }
            }
            updateTaskStatusToFailed(knowledgeId, "向量化操作失败：" + e.getMessage());
            return false;
        } finally {
            // 删除锁
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 批量向量化方法
     * @return
     */
    @Override
    public int batchVectorize() {
        return batchVectorizeOptimized(0, 100);
    }

    /**
     * 批量向量化方法（向后兼容，委托给优化版本）
     * @param offset 批量起始偏移
     * @param limit 批量大小
     * @return 批量处理成功数量
     */
    @Override
    public int batchVectorize(int offset, int limit) {
        return batchVectorizeOptimized(offset, limit);
    }

    /**
     * 优化批量向量化：一次取 N 条 → 批量 Embedding → 批量写 Redis → 批量更新 DB
     * <p>相比逐条处理，Embedding API 调用次数从 N 次降至 ceil(N/25) 次</p>
     */
    private int batchVectorizeOptimized(int offset, int limit) {
        List<LawKnowledge> unVectorizedList = lawKnowledgeMapper.selectUnvectorizedWithOffset(offset, limit);
        if (unVectorizedList.isEmpty()) {
            return 0;
        }

        // 1. 构建待向量化的文本列表
        List<String> texts = new ArrayList<>(unVectorizedList.size());
        for (LawKnowledge knowledge : unVectorizedList) {
            String text = knowledge.getContent();
            if (knowledge.getTitle() != null && !knowledge.getTitle().isBlank()) {
                text = "[" + knowledge.getTitle() + "]\n" + knowledge.getContent();
            }
            texts.add(text);
        }

        // 2. 批量调用 Embedding API（内部自动分拆为 25 条/批）
        float[][] vectors = embeddingUtil.embedBatch(texts);

        // 3. 存储到 Redis + 收集成功的 ID
        List<Long> successIds = new ArrayList<>();
        for (int i = 0; i < unVectorizedList.size(); i++) {
            LawKnowledge knowledge = unVectorizedList.get(i);
            float[] vector = vectors[i];

            if (vector == null || vector.length == 0) {
                log.warn("[批量向量化] 向量为空: knowledgeId={}", knowledge.getId());
                continue;
            }

            try {
                String redisKey = ragConfig.getLawVectorKeyPrefix() + knowledge.getId();
                String lawType = knowledge.getLawType() != null ? knowledge.getLawType() : "其他";
                String enrichedContent = knowledge.getContent();
                if (knowledge.getTitle() != null && !knowledge.getTitle().isBlank()) {
                    enrichedContent = "[" + knowledge.getTitle() + "]\n" + knowledge.getContent();
                }

                redisVectorUtil.storeVectorWithMetadata(redisKey, vector, lawType,
                        knowledge.getTitle(), enrichedContent, knowledge.getChapter(),
                        knowledge.getSection(), knowledge.getArticleNumber(), null);
                successIds.add(knowledge.getId());
            } catch (Exception e) {
                log.error("[批量向量化] 存储失败: knowledgeId={}, error={}", knowledge.getId(), e.getMessage());
            }
        }

        // 4. 批量更新 DB 状态
        if (!successIds.isEmpty()) {
            lawKnowledgeMapper.batchUpdateVectorStatus(successIds, 1);
            // 同步更新任务表
            for (Long id : successIds) {
                upsertTaskRecord(id, 1, null);
            }
        }

        log.info("[批量向量化] 完成: offset={} limit={} 总数={} 成功={}", offset, limit, unVectorizedList.size(), successIds.size());
        return successIds.size();
    }

    /**
     * 分块向量化方法
     * <p>根据分块ID进行向量化处理，包含分布式锁控制、向量生成、Redis存储和状态更新等步骤</p>
     * <p>使用事务管理确保数据一致性，任何步骤失败都会回滚整个操作</p>
     *
     * @param chunkId
     * @return
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean vectorizeChunk(Long chunkId) {
        if (redisTemplate == null) {
            log.error("[分块向量化] Redis 不可用: chunkId={}", chunkId);
            return false;
        }

        String lockKey = CHUNK_LOCK_KEY_PREFIX + chunkId;
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(lockKey, "LOCK", LOCK_EXPIRE, TimeUnit.SECONDS);
        if (locked == null || !locked) {
            log.warn("[分块向量化] 已加锁，跳过: chunkId={}", chunkId);
            return false;
        }

        try {
            KnowledgeChunk chunk = knowledgeChunkMapper.selectById(chunkId);
            if (chunk == null) {
                log.error("[分块向量化] 分块不存在: chunkId={}", chunkId);
                return false;
            }

            String redisKey = CHUNK_KEY_PREFIX + chunkId;
            Boolean redisExists = redisTemplate.hasKey(redisKey);
            if (redisExists != null && redisExists) {
                if (chunk.getVectorStatus() != 1) {
                    chunk.setVectorStatus(1);
                    knowledgeChunkMapper.update(chunk);
                }
                return true;
            }

            if (!redisIndexUtil.indexExists(redisTemplate, ragConfig.getLawVectorIndex())) {
                log.error("[分块向量化] 索引不存在: chunkId={}", chunkId);
                return false;
            }

            float[] vector = embeddingUtil.embed(chunk.getContent());
            if (vector == null || vector.length == 0) {
                chunk.setVectorStatus(2);
                chunk.setErrorMsg("向量生成失败");
                knowledgeChunkMapper.update(chunk);
                return false;
            }

            LawKnowledge knowledge = lawKnowledgeMapper.selectById(chunk.getKnowledgeId());
            String lawType = knowledge != null && knowledge.getLawType() != null
                    ? knowledge.getLawType() : "其他";
            String title = knowledge != null && knowledge.getTitle() != null
                    ? knowledge.getTitle() : "分块-" + chunkId;

            String enrichedContent = chunk.getContextPrefix() != null && !chunk.getContextPrefix().isBlank()
                    ? chunk.getContextPrefix() + "\n" + chunk.getContent()
                    : chunk.getContent();
            redisVectorUtil.storeVectorWithMetadata(redisKey, vector, lawType, title, enrichedContent,
                    knowledge != null ? knowledge.getChapter() : null,
                    knowledge != null ? knowledge.getSection() : null,
                    knowledge != null ? knowledge.getArticleNumber() : null,
                    chunk.getContextPrefix());

            chunk.setVectorStatus(1);
            knowledgeChunkMapper.update(chunk);
            log.info("[分块向量化] 成功: chunkId={}", chunkId);
            return true;
        } catch (Exception e) {
            log.error("[分块向量化] 失败: chunkId={}, error={}", chunkId, e.getMessage(), e);
            try {
                KnowledgeChunk chunk = knowledgeChunkMapper.selectById(chunkId);
                if (chunk != null) {
                    chunk.setVectorStatus(2);
                    chunk.setErrorMsg(e.getMessage());
                    chunk.setRetryCount((chunk.getRetryCount() == null ? 0 : chunk.getRetryCount()) + 1);
                    knowledgeChunkMapper.update(chunk);
                }
            } catch (Exception ex) {
                log.error("[分块向量化] 更新失败状态出错: chunkId={}", chunkId, ex);
            }
            return false;
        } finally {
            redisTemplate.delete(lockKey);
        }
    }

    /**
     * 批量分块向量化方法
     * @param offset 批量起始偏移
     * @param limit 批量大小
     * @return 批量处理成功数量
     */
    @Override
    public int batchVectorizeChunks(int offset, int limit) {
        return batchVectorizeChunksOptimized(offset, limit);
    }

    private int batchVectorizeChunksOptimized(int offset, int limit) {
        List<KnowledgeChunk> unVectorizedChunks = knowledgeChunkMapper.selectUnvectorizedWithOffset(offset, limit);
        if (unVectorizedChunks.isEmpty()) {
            return 0;
        }

        // 1. 构建文本列表
        List<String> texts = new ArrayList<>(unVectorizedChunks.size());
        for (KnowledgeChunk chunk : unVectorizedChunks) {
            String text = chunk.getContextPrefix() != null && !chunk.getContextPrefix().isBlank()
                    ? chunk.getContextPrefix() + "\n" + chunk.getContent()
                    : chunk.getContent();
            texts.add(text);
        }

        // 2. 批量 Embedding
        float[][] vectors = embeddingUtil.embedBatch(texts);

        // 3. 存储到 Redis + 收集成功 ID
        List<Long> successIds = new ArrayList<>();
        for (int i = 0; i < unVectorizedChunks.size(); i++) {
            KnowledgeChunk chunk = unVectorizedChunks.get(i);
            float[] vector = vectors[i];

            if (vector == null || vector.length == 0) {
                log.warn("[批量分块向量化] 向量为空: chunkId={}", chunk.getId());
                continue;
            }

            try {
                String redisKey = CHUNK_KEY_PREFIX + chunk.getId();
                LawKnowledge knowledge = lawKnowledgeMapper.selectById(chunk.getKnowledgeId());
                String lawType = knowledge != null && knowledge.getLawType() != null
                        ? knowledge.getLawType() : "其他";
                String title = knowledge != null && knowledge.getTitle() != null
                        ? knowledge.getTitle() : "分块-" + chunk.getId();
                String enrichedContent = chunk.getContextPrefix() != null && !chunk.getContextPrefix().isBlank()
                        ? chunk.getContextPrefix() + "\n" + chunk.getContent()
                        : chunk.getContent();

                redisVectorUtil.storeVectorWithMetadata(redisKey, vector, lawType, title, enrichedContent,
                        knowledge != null ? knowledge.getChapter() : null,
                        knowledge != null ? knowledge.getSection() : null,
                        knowledge != null ? knowledge.getArticleNumber() : null,
                        chunk.getContextPrefix());
                successIds.add(chunk.getId());
            } catch (Exception e) {
                log.error("[批量分块向量化] 存储失败: chunkId={}, error={}", chunk.getId(), e.getMessage());
            }
        }

        // 4. 批量更新 DB 状态
        if (!successIds.isEmpty()) {
            knowledgeChunkMapper.batchUpdateVectorStatus(successIds, 1);
        }

        log.info("[批量分块向量化] 完成: offset={} limit={} 总数={} 成功={}", offset, limit, unVectorizedChunks.size(), successIds.size());
        return successIds.size();
    }

    /**
     * 更新任务表状态为失败
     * @param knowledgeId 知识ID
     * @param errorMsg 错误信息
     */
    private void updateTaskStatusToFailed(Long knowledgeId, String errorMsg) {
        upsertTaskRecord(knowledgeId, 2, errorMsg);
    }

    /**
     * 插入或更新向量任务记录
     * @param knowledgeId 知识ID
     * @param vectorStatus 向量化状态 (1=成功, 2=失败)
     * @param errorMsg 错误信息（成功时为null）
     */
    private void upsertTaskRecord(Long knowledgeId, int vectorStatus, String errorMsg) {
        try {
            List<LawVectorTask> taskList = lawVectorTaskMapper.selectByKnowledgeId(knowledgeId);
            LawVectorTask task = taskList != null && !taskList.isEmpty() ? taskList.get(0) : null;
            if (task == null) {
                task = new LawVectorTask();
                task.setKnowledgeId(knowledgeId);
                task.setVectorStatus(vectorStatus);
                task.setRedisSearchSync(vectorStatus == 1 ? 1 : 0);
                if (errorMsg != null) {
                    task.setErrorMsg(errorMsg.length() > 200 ? errorMsg.substring(0, 200) : errorMsg);
                }
                lawVectorTaskMapper.insert(task);
            } else {
                task.setVectorStatus(vectorStatus);
                task.setRedisSearchSync(vectorStatus == 1 ? 1 : 0);
                if (errorMsg != null) {
                    task.setErrorMsg(errorMsg.length() > 200 ? errorMsg.substring(0, 200) : errorMsg);
                }
                lawVectorTaskMapper.update(task);
            }
        } catch (Exception e) {
            log.error("[向量化] 更新任务表失败: knowledgeId={}, error={}", knowledgeId, e.getMessage());
        }
    }
}
