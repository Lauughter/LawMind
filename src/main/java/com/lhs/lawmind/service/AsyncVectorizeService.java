package com.lhs.lawmind.service;

import java.util.concurrent.CompletableFuture;

/**
 * 异步向量化服务接口
 */
public interface AsyncVectorizeService {

    /**
     * 异步向量化
     *
     * @param knowledgeId 知识点ID
     * @return 是否成功
     */
    CompletableFuture<Boolean> vectorizeAsync(Long knowledgeId);

    /**
     * 批量异步向量化
     *
     * @param offset 起始位置
     * @param limit  批量大小
     * @return 成功向量化的记录数
     */
    CompletableFuture<Integer> batchVectorizeAsync(int offset, int limit);
}
