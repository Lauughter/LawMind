package com.lhs.lawmind.scheduler;

import com.lhs.lawmind.config.VectorizeSchedulerConfig;
import com.lhs.lawmind.mapper.KnowledgeChunkMapper;
import com.lhs.lawmind.mapper.LawKnowledgeMapper;
import com.lhs.lawmind.service.LawVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时全量向量化执行器
 *
 * <p>替代旧的 {@code @Scheduled(initialDelay=..., fixedRate=Long.MAX_VALUE)} hack，
 * 使用 Spring 标准的 {@link ApplicationRunner} 在应用就绪后执行一次全量向量化。</p>
 *
 * <p>执行策略：分批处理所有未向量化的知识和分块，每批之间短暂休眠避免过载。</p>
 */
@Slf4j
@Component
@Order(1) // 在其他 ApplicationRunner 之前执行
public class VectorizeStartupRunner implements ApplicationRunner {

    private final LawVectorService lawVectorService;
    private final VectorizeSchedulerConfig schedulerConfig;
    private final LawKnowledgeMapper lawKnowledgeMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    public VectorizeStartupRunner(LawVectorService lawVectorService,
                                  VectorizeSchedulerConfig schedulerConfig,
                                  LawKnowledgeMapper lawKnowledgeMapper,
                                  KnowledgeChunkMapper knowledgeChunkMapper) {
        this.lawVectorService = lawVectorService;
        this.schedulerConfig = schedulerConfig;
        this.lawKnowledgeMapper = lawKnowledgeMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!schedulerConfig.isEnabled()) {
            log.info("[启动向量化] 已禁用，跳过");
            return;
        }

        long knowledgeCount = lawKnowledgeMapper.countUnvectorized();
        long chunkCount = knowledgeChunkMapper.countUnvectorized();

        if (knowledgeCount == 0 && chunkCount == 0) {
            log.info("[启动向量化] 无待向量化数据，跳过");
            return;
        }

        log.info("[启动向量化] 开始全量向量化: knowledgePending={} chunkPending={} batchSize={}",
                knowledgeCount, chunkCount, schedulerConfig.getBatchSize());

        long startTime = System.currentTimeMillis();
        int totalKnowledge = 0;
        int totalChunks = 0;

        // 先处理知识记录
        if (knowledgeCount > 0) {
            int offset = 0;
            int batchSize = schedulerConfig.getBatchSize();
            while (true) {
                int processed = lawVectorService.batchVectorize(offset, batchSize);
                totalKnowledge += processed;
                if (processed < batchSize) {
                    break;
                }
                offset += batchSize;
                sleepBetweenBatches();
            }
        }

        // 再处理分块
        if (chunkCount > 0) {
            int offset = 0;
            int batchSize = schedulerConfig.getBatchSize();
            while (true) {
                int processed = lawVectorService.batchVectorizeChunks(offset, batchSize);
                totalChunks += processed;
                if (processed < batchSize) {
                    break;
                }
                offset += batchSize;
                sleepBetweenBatches();
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[启动向量化] 全量完成: knowledge={}/{} chunk={}/{} elapsedMs={}",
                totalKnowledge, knowledgeCount, totalChunks, chunkCount, elapsed);
    }

    private void sleepBetweenBatches() {
        try {
            Thread.sleep(200); // 批次间短暂休眠 200ms，避免 API 限流
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
