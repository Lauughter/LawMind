package com.lhs.lawmind.scheduler;

import com.lhs.lawmind.config.VectorizeSchedulerConfig;
import com.lhs.lawmind.mapper.KnowledgeChunkMapper;
import com.lhs.lawmind.mapper.LawKnowledgeMapper;
import com.lhs.lawmind.service.LawVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 向量化定时兜底调度器
 *
 * <p>主要向量化已改为事件驱动（{@link com.lhs.lawmind.event.VectorizeEventListener}），
 * 此定时任务仅作为兜底，每小时扫描一次补漏。</p>
 *
 * <p>优化点：
 * <ul>
 *   <li>fixedDelay 避免任务堆积</li>
 *   <li>空转检测：count=0 直接跳过</li>
 *   <li>调度级 Redis 分布式锁，多实例安全</li>
 * </ul>
 */
@Slf4j
@Component
public class VectorizeScheduler {

    private final LawVectorService lawVectorService;
    private final VectorizeSchedulerConfig schedulerConfig;
    private final LawKnowledgeMapper lawKnowledgeMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public VectorizeScheduler(LawVectorService lawVectorService,
                              VectorizeSchedulerConfig schedulerConfig,
                              LawKnowledgeMapper lawKnowledgeMapper,
                              KnowledgeChunkMapper knowledgeChunkMapper,
                              RedisTemplate<String, Object> redisTemplate) {
        this.lawVectorService = lawVectorService;
        this.schedulerConfig = schedulerConfig;
        this.lawKnowledgeMapper = lawKnowledgeMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.redisTemplate = redisTemplate;
        log.info("向量化定时任务配置：{}", schedulerConfig);
    }

    /**
     * 定时任务：扫描未向量化数据，批量向量化
     * <p>使用 fixedDelay 确保上次执行完成后才启动下一次，避免任务堆积</p>
     */
    @Scheduled(fixedDelayString = "${lawmind.vectorize.scheduler.fixed-delay}")
    public void autoVectorize() {
        if (!schedulerConfig.isEnabled()) {
            log.debug("[向量化定时] 已禁用，跳过");
            return;
        }

        // P2-2: 调度级分布式锁，多实例安全
        if (!acquireSchedulerLock()) {
            log.debug("[向量化定时] 其他实例正在执行，跳过");
            return;
        }

        try {
            // P1-2: 空转检测——无待处理数据直接跳过
            long knowledgeCount = lawKnowledgeMapper.countUnvectorized();
            long chunkCount = knowledgeChunkMapper.countUnvectorized();

            if (knowledgeCount == 0 && chunkCount == 0) {
                log.debug("[向量化定时] 无待向量化数据，跳过 (knowledge=0 chunk=0)");
                return;
            }

            log.info("[向量化定时] 开始执行: knowledgePending={} chunkPending={}", knowledgeCount, chunkCount);

            int knowledgeSuccess = 0;
            int chunkSuccess = 0;

            if (knowledgeCount > 0) {
                knowledgeSuccess = lawVectorService.batchVectorize(0, schedulerConfig.getBatchSize());
            }
            if (chunkCount > 0) {
                chunkSuccess = lawVectorService.batchVectorizeChunks(0, schedulerConfig.getBatchSize());
            }

            log.info("[向量化定时] 执行完成: knowledge={}/{} chunk={}/{}",
                    knowledgeSuccess, knowledgeCount, chunkSuccess, chunkCount);
        } catch (Exception e) {
            log.error("[向量化定时] 执行失败: {}", e.getMessage(), e);
        } finally {
            releaseSchedulerLock();
        }
    }

    private boolean acquireSchedulerLock() {
        if (redisTemplate == null) {
            return true; // Redis 不可用时不阻塞
        }
        try {
            Boolean locked = redisTemplate.opsForValue()
                    .setIfAbsent(schedulerConfig.getSchedulerLockKey(), "LOCK",
                            schedulerConfig.getSchedulerLockExpire(), TimeUnit.SECONDS);
            return locked != null && locked;
        } catch (Exception e) {
            log.warn("[向量化定时] 获取调度锁异常，继续执行: {}", e.getMessage());
            return true;
        }
    }

    private void releaseSchedulerLock() {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(schedulerConfig.getSchedulerLockKey());
        } catch (Exception e) {
            log.warn("[向量化定时] 释放调度锁异常: {}", e.getMessage());
        }
    }
}
