package com.lhs.lawmind.service;

/**
 * 法律知识库向量服务接口
 */
public interface LawVectorService {

    boolean vectorize(Long knowledgeId);

    int batchVectorize();

    int batchVectorize(int offset, int limit);

    boolean vectorizeChunk(Long chunkId);

    int batchVectorizeChunks(int offset, int limit);
}
