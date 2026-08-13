package com.lhs.lawmind.rag;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.service.HybridSearchService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.service.RerankService;
import com.lhs.lawmind.utils.query.SearchResultDiversifier;
import com.lhs.lawmind.utils.redis.LawKnowledgeRedisUtil;
import com.lhs.lawmind.utils.redis.RedisVectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 检索服务 —— 混合搜索 / 精排 / MMR / 双阈值过滤。
 * 从 RagServiceImpl 拆出。
 */
@Slf4j
@Component
public class RagRetrievalService {

    private final RagConfig ragConfig;
    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final SearchResultDiversifier searchResultDiversifier;
    private final LawKnowledgeRedisUtil lawKnowledgeRedisUtil;
    private final LawKnowledgeService lawKnowledgeService;

    public RagRetrievalService(RagConfig ragConfig,
                               HybridSearchService hybridSearchService,
                               RerankService rerankService,
                               SearchResultDiversifier searchResultDiversifier,
                               LawKnowledgeRedisUtil lawKnowledgeRedisUtil,
                               LawKnowledgeService lawKnowledgeService) {
        this.ragConfig = ragConfig;
        this.hybridSearchService = hybridSearchService;
        this.rerankService = rerankService;
        this.searchResultDiversifier = searchResultDiversifier;
        this.lawKnowledgeRedisUtil = lawKnowledgeRedisUtil;
        this.lawKnowledgeService = lawKnowledgeService;
    }

    /**
     * 混合搜索 + Rerank 精排 + MMR 去重 + 双阈值过滤。
     */
    public List<LawKnowledge> searchLawKnowledgeFiltered(float[] questionVector, String expandedQuery,
                                                         String lawType, int topK) {
        List<LawKnowledge> rawResults;

        if (ragConfig.isHybridSearchEnabled()) {
            rawResults = hybridSearchService.searchHybridFiltered(questionVector, expandedQuery, topK, lawType);
        } else {
            rawResults = pureVectorSearch(questionVector, topK);
        }

        if (ragConfig.isRerankEnabled() && rawResults.size() > 1) {
            long tRerank = System.currentTimeMillis();
            rawResults = rerankService.rerank(
                    expandedQuery, rawResults,
                    ragConfig.getRerankCandidateTopK(),
                    ragConfig.getRerankTopN());
            log.info("[RAG] Rerank精排完成: model={} outputSize={} elapsedMs={}",
                    ragConfig.getRerankModel(), rawResults.size(), System.currentTimeMillis() - tRerank);
        }

        if (ragConfig.isMmrEnabled() && rawResults.size() > 1) {
            rawResults = searchResultDiversifier.diversify(rawResults, expandedQuery, topK, ragConfig.getMmrLambda());
        }

        return filterByThreshold(rawResults);
    }

    private List<LawKnowledge> pureVectorSearch(float[] questionVector, int topK) {
        List<LawKnowledge> result = new ArrayList<>();
        if (questionVector == null || questionVector.length == 0) {
            return result;
        }
        try {
            List<RedisVectorUtil.SearchResult> results = lawKnowledgeRedisUtil.searchLawKnowledge(questionVector, topK);
            for (RedisVectorUtil.SearchResult sr : results) {
                loadKnowledgeToResult(sr, sr.getScore(), result);
            }
        } catch (Exception e) {
            log.error("[RAG] vector search error: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 从向量搜索结果中加载法律知识（Redis 优先，未命中回退 MySQL）。
     */
    private void loadKnowledgeToResult(RedisVectorUtil.SearchResult searchResult, double similarity,
                                       List<LawKnowledge> result) {
        String key = searchResult.getKey();
        String idStr = key.replace(ragConfig.getLawVectorKeyPrefix(), "");
        try {
            Long knowledgeId = Long.parseLong(idStr);
            LawKnowledgeRedisUtil.LawKnowledge redisKnowledge = lawKnowledgeRedisUtil.getLawKnowledge(knowledgeId);
            if (redisKnowledge != null) {
                LawKnowledge knowledge = new LawKnowledge();
                knowledge.setId(redisKnowledge.getId());
                knowledge.setTitle(redisKnowledge.getTitle());
                knowledge.setLawType(redisKnowledge.getLawType());
                knowledge.setContent(redisKnowledge.getContent());
                knowledge.setChapter(redisKnowledge.getChapter());
                knowledge.setSection(redisKnowledge.getSection());
                knowledge.setArticleNumber(redisKnowledge.getArticleNumber());
                knowledge.setScore(similarity);
                result.add(knowledge);
                log.info("法律知识命中: id={}, score={}", knowledgeId, String.format("%.10f", similarity));
            } else {
                LawKnowledge knowledge = lawKnowledgeService.getById(knowledgeId);
                if (knowledge != null) {
                    knowledge.setScore(similarity);
                    result.add(knowledge);
                    log.info("法律知识命中(MySQL): id={}, score={}", knowledgeId, String.format("%.10f", similarity));
                }
            }
        } catch (NumberFormatException e) {
            log.warn("无效的知识ID格式: {}, 跳过该结果", idStr);
        }
    }

    /**
     * 双阈值过滤：高质量（>= lawKnowledge）直接采用，边缘（>= filter 且 < lawKnowledge）作为备选兜底。
     */
    private List<LawKnowledge> filterByThreshold(List<LawKnowledge> candidates) {
        List<LawKnowledge> result = new ArrayList<>();
        List<LawKnowledge> borderline = new ArrayList<>();
        double filterThreshold = ragConfig.getFilterThreshold();
        double lawKnowledgeThreshold = ragConfig.getLawKnowledgeThreshold();

        for (LawKnowledge knowledge : candidates) {
            double score = knowledge.getScore() != null ? knowledge.getScore() : 0.0;
            if (score < filterThreshold) {
                log.debug("过滤低分结果: id={}, score={} < {}", knowledge.getId(), String.format("%.4f", score), filterThreshold);
                continue;
            }
            if (score >= lawKnowledgeThreshold) {
                result.add(knowledge);
                log.info("法律知识命中: id={}, title={}, score={}", knowledge.getId(), knowledge.getTitle(), String.format("%.4f", score));
            } else {
                borderline.add(knowledge);
                log.debug("法律知识备选: id={}, score={}", knowledge.getId(), String.format("%.4f", score));
            }
        }

        if (result.isEmpty() && !borderline.isEmpty()) {
            log.info("无高质量匹配结果，使用备选知识: 备选数量={}", borderline.size());
            result.addAll(borderline);
        }

        log.info("阈值过滤完成: 最终命中={}", result.size());
        return result;
    }
}
