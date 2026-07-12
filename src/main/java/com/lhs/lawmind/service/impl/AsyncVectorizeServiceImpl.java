package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.service.AsyncVectorizeService;
import com.lhs.lawmind.service.LawVectorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AsyncVectorizeServiceImpl implements AsyncVectorizeService {

    private final LawVectorService lawVectorService;

    public AsyncVectorizeServiceImpl(LawVectorService lawVectorService) {
        this.lawVectorService = lawVectorService;
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Boolean> vectorizeAsync(Long knowledgeId) {
        try {
            boolean result = lawVectorService.vectorize(knowledgeId);
            if (result) {
                log.info("[异步向量化] 成功: knowledgeId={}", knowledgeId);
            } else {
                log.warn("[异步向量化] 跳过（已存在或获取锁失败）: knowledgeId={}", knowledgeId);
            }
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("[异步向量化] 失败: knowledgeId={}, error={}", knowledgeId, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    @Async("taskExecutor")
    public CompletableFuture<Integer> batchVectorizeAsync(int offset, int limit) {
        try {
            int result = lawVectorService.batchVectorize(offset, limit);
            log.info("[异步批量向量化] 完成: 成功 {} 条", result);
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            log.error("[异步批量向量化] 失败: offset={}, limit={}, error={}", offset, limit, e.getMessage(), e);
            return CompletableFuture.failedFuture(e);
        }
    }
}
