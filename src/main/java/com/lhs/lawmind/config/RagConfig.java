package com.lhs.lawmind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * RAG知识库检索配置类
 * 包含所有与RAG流程相关的配置参数，避免硬编码
 * 该类负责配置 RAG 知识库检索流程的参数，包括 Redis 索引、键前缀、相似度阈值、热点缓存、向量维度、检索数量等。
 * 它与 RAG 知识库检索服务类 RagService 交互，用于配置 RAG 知识库检索流程的参数。
 * 
 * */
@Configuration
public class RagConfig {

    // ========== Redis索引配置 ==========
    @Value("${rag.redis.index.law-vector:idx:law_knowledge}")
    private String lawVectorIndex;

    @Value("${rag.redis.index.similar-question:idx:similar_question}")
    private String similarQuestionIndex;

    // ========== Redis键前缀配置 ==========
    @Value("${rag.redis.key-prefix.hot-question:hot:question:}")
    private String hotQuestionKeyPrefix;

    @Value("${rag.redis.key-prefix.law-vector:law:vector:}")
    private String lawVectorKeyPrefix;

    @Value("${rag.redis.key-prefix.similar-question:similar:question:}")
    private String similarQuestionKeyPrefix;

    // ========== 相似度阈值配置 ==========
    @Value("${rag.threshold.similar-question:0.85}")
    private double similarQuestionThreshold;

    @Value("${rag.threshold.law-knowledge:0.75}")
    private double lawKnowledgeThreshold;

    @Value("${rag.threshold.filter:0.70}")
    private double filterThreshold;

    // ========== 热点缓存配置 ==========
    @Value("${rag.hot-cache.initial-ttl-days:30}")
    private int hotCacheInitialTtlDays;

    @Value("${rag.hot-cache.eviction-days:7}")
    private int hotCacheEvictionDays;

    // ========== 热点阈值配置 ==========
    @Value("${rag.hot-threshold.5-minutes:3}")
    private int hotThreshold5Minutes;

    @Value("${rag.hot-threshold.1-hour:10}")
    private int hotThreshold1Hour;

    @Value("${rag.hot-threshold.1-day:30}")
    private int hotThreshold1Day;

    // ========== 向量维度配置 ==========
    @Value("${rag.vector.dimension:1536}")
    private int vectorDimension;

    // ========== 检索数量配置 ==========
    @Value("${rag.search.top-k:5}")
    private int searchTopK;

    // ========== 多轮对话配置 ==========
    // 多轮对话中历史消息的最大数量，超过后会丢弃最早的消息，默认 10 条
    @Value("${rag.conversation.max-history-messages:10}")
    private int maxHistoryMessages;

    // ========== 混合搜索配置 ==========
    // 是否启用混合搜索（向量搜索 + 关键词搜索）
    @Value("${rag.search.hybrid.enabled:true}")
    private boolean hybridSearchEnabled;
    // 是否启用 MMR（最大边际相关性）重排序
    @Value("${rag.search.mmr.enabled:true}")
    private boolean mmrEnabled;
    // MMR 重排序中相关性与多样性的权重，范围 [0,1]，默认 0.7
    @Value("${rag.search.mmr.lambda:0.7}")
    private double mmrLambda;

    // ========== Rerank 精排配置 ==========
    // 是否启用 Rerank 精排
    @Value("${rag.search.rerank.enabled:false}")
    private boolean rerankEnabled;
    // 精排 API 密钥（百炼 DashScope）
    @Value("${rag.search.rerank.api-key:}")
    private String rerankApiKey;
    // 精排模型名称（百炼 DashScope）
    @Value("${rag.search.rerank.model:qwen3-rerank}")
    private String rerankModel;
    // 精排后保留的文档数量
    @Value("${rag.search.rerank.top-n:5}")
    private int rerankTopN;
    // 送入精排的候选文档数量（从 RRF 融合结果中取 top-k）
    @Value("${rag.search.rerank.candidate-top-k:20}")
    private int rerankCandidateTopK;

    // ========== HyDE 检索配置 ==========
    // 是否启用 HyDE（假设文档嵌入）检索模式
    @Value("${rag.search.hyde.enabled:false}")
    private boolean hydeEnabled;

    // ========== 知识去重配置 ==========
    // 是否启用入库前去重检查
    @Value("${rag.dedup.enabled:true}")
    private boolean dedupEnabled;
    // 去重相似度阈值（高于此值视为重复，默认0.92）
    @Value("${rag.dedup.threshold:0.92}")
    private double dedupThreshold;

    // ========== 引用验证配置 ==========
    // 是否启用回答中法律引用来源验证
    @Value("${rag.citation.verification.enabled:true}")
    private boolean citationVerificationEnabled;

    // ========== Getter方法 ==========

    public String getLawVectorIndex() {
        return lawVectorIndex;
    }

    public String getSimilarQuestionIndex() {
        return similarQuestionIndex;
    }

    public String getHotQuestionKeyPrefix() {
        return hotQuestionKeyPrefix;
    }

    public String getLawVectorKeyPrefix() {
        return lawVectorKeyPrefix;
    }

    public String getSimilarQuestionKeyPrefix() {
        return similarQuestionKeyPrefix;
    }

    public double getSimilarQuestionThreshold() {
        return similarQuestionThreshold;
    }

    public double getLawKnowledgeThreshold() {
        return lawKnowledgeThreshold;
    }

    public double getFilterThreshold() {
        return filterThreshold;
    }

    public int getHotCacheInitialTtlDays() {
        return hotCacheInitialTtlDays;
    }

    public int getHotCacheEvictionDays() {
        return hotCacheEvictionDays;
    }

    public int getHotThreshold5Minutes() {
        return hotThreshold5Minutes;
    }

    public int getHotThreshold1Hour() {
        return hotThreshold1Hour;
    }

    public int getHotThreshold1Day() {
        return hotThreshold1Day;
    }

    public int getVectorDimension() {
        return vectorDimension;
    }

    public int getSearchTopK() {
        return searchTopK;
    }

    public int getMaxHistoryMessages() {
        return maxHistoryMessages;
    }

    public boolean isHybridSearchEnabled() {
        return hybridSearchEnabled;
    }

    public boolean isMmrEnabled() {
        return mmrEnabled;
    }

    public double getMmrLambda() {
        return mmrLambda;
    }

    public boolean isRerankEnabled() {
        return rerankEnabled;
    }

    public String getRerankApiKey() {
        return rerankApiKey;
    }

    public String getRerankModel() {
        return rerankModel;
    }

    public int getRerankTopN() {
        return rerankTopN;
    }

    public int getRerankCandidateTopK() {
        return rerankCandidateTopK;
    }

    public boolean isHydeEnabled() {
        return hydeEnabled;
    }

    public boolean isDedupEnabled() {
        return dedupEnabled;
    }

    public double getDedupThreshold() {
        return dedupThreshold;
    }

    public boolean isCitationVerificationEnabled() {
        return citationVerificationEnabled;
    }
}
