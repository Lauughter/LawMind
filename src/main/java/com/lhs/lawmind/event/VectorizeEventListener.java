package com.lhs.lawmind.event;

import com.lhs.lawmind.mapper.KnowledgeChunkMapper;
import com.lhs.lawmind.mapper.LawKnowledgeMapper;
import com.lhs.lawmind.service.LawVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 知识创建事件监听器 — 事务提交后异步触发向量化
 *
 * <p>关键设计：
 * <ul>
 *   <li>{@code @TransactionalEventListener(phase = AFTER_COMMIT)} — 仅当事务成功提交后才执行，
 *       确保 DB 中已有完整数据，避免向量化时查不到记录</li>
 *   <li>{@code @Async} — 不阻塞上传请求的响应，向量化在后台线程池中执行</li>
 * </ul>
 */
@Slf4j
@Component
public class VectorizeEventListener {

    private final LawVectorService lawVectorService;
    private final LawKnowledgeMapper lawKnowledgeMapper;
    private final KnowledgeChunkMapper knowledgeChunkMapper;

    public VectorizeEventListener(LawVectorService lawVectorService,
                                   LawKnowledgeMapper lawKnowledgeMapper,
                                   KnowledgeChunkMapper knowledgeChunkMapper) {
        this.lawVectorService = lawVectorService;
        this.lawKnowledgeMapper = lawKnowledgeMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
    }

    @Async("vectorizeTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKnowledgeCreated(KnowledgeCreatedEvent event) {
        log.info("[事件驱动] 收到知识创建事件，触发向量化: source={}", event.getSource());

        long knowledgePending = lawKnowledgeMapper.countUnvectorized();
        long chunkPending = knowledgeChunkMapper.countUnvectorized();

        if (knowledgePending == 0 && chunkPending == 0) {
            log.info("[事件驱动] 无待向量化数据，跳过");
            return;
        }

        log.info("[事件驱动] 开始向量化: knowledgePending={} chunkPending={}", knowledgePending, chunkPending);

        int knowledgeOk = 0;
        int chunkOk = 0;

        // 分批处理知识记录
        if (knowledgePending > 0) {
            int offset = 0;
            int batchSize = 100;
            while (true) {
                int n = lawVectorService.batchVectorize(offset, batchSize);
                knowledgeOk += n;
                if (n < batchSize) break;
                offset += batchSize;
            }
        }

        // 分批处理分块
        if (chunkPending > 0) {
            int offset = 0;
            int batchSize = 100;
            while (true) {
                int n = lawVectorService.batchVectorizeChunks(offset, batchSize);
                chunkOk += n;
                if (n < batchSize) break;
                offset += batchSize;
            }
        }

        log.info("[事件驱动] 向量化完成: knowledge={}/{} chunk={}/{}",
                knowledgeOk, knowledgePending, chunkOk, chunkPending);
    }
}
