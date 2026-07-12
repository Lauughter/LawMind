package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.mapper.LawKnowledgeMapper;
import com.lhs.lawmind.service.HybridSearchService;
import com.lhs.lawmind.utils.LawKnowledgeRedisUtil;
import com.lhs.lawmind.utils.RedisVectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 混合搜索服务实现
 *
 * <p>结合向量检索（RedisSearch KNN）与全文检索（MySQL Ngram），
 * 通过 RRF（Reciprocal Rank Fusion）融合排序，提升召回率。</p>
 *
 * <p>当向量化不可用时（向量为空），自动降级为纯全文搜索。</p>
 * <p>当查询词不满足全文搜索条件时，自动降级为纯向量搜索。</p>
 */
@Slf4j
@Service
public class HybridSearchServiceImpl implements HybridSearchService {

    private final LawKnowledgeMapper lawKnowledgeMapper;
    private final LawKnowledgeRedisUtil lawKnowledgeRedisUtil;
    private final com.lhs.lawmind.service.LawKnowledgeService lawKnowledgeService;
    private final RagConfig ragConfig;

    private static final int RRF_K = 60;
    private static final int NGRAM_MIN_LENGTH = 2;
    private static final int NATURAL_LANG_MIN_LENGTH = 15;

    private static final java.util.Set<String> STOP_WORDS = java.util.Set.of(
            "什么", "怎么", "如何", "为什么", "能不能", "可以吗", "是否",
            "怎么办", "怎样", "请问", "我想", "帮我", "告诉我", "解释一下",
            "的", "了", "吗", "呢", "吧", "啊", "哦", "是什么", "是什么意思",
            "有没有", "会不会", "可不可以", "需不需要",
            "应该", "该如何", "我应该", "我想知道", "我想了解");

    // 法律结构标记：第X条、第X章 等
    private static final Pattern LEGAL_PATTERN =
            Pattern.compile("第[^条章节编款项]{1,10}[条章节编款项]");

    public HybridSearchServiceImpl(
            LawKnowledgeMapper lawKnowledgeMapper,
            LawKnowledgeRedisUtil lawKnowledgeRedisUtil,
            com.lhs.lawmind.service.LawKnowledgeService lawKnowledgeService,
            RagConfig ragConfig) {
        this.lawKnowledgeMapper = lawKnowledgeMapper;
        this.lawKnowledgeRedisUtil = lawKnowledgeRedisUtil;
        this.lawKnowledgeService = lawKnowledgeService;
        this.ragConfig = ragConfig;
    }

    @Override
    public List<LawKnowledge> searchHybrid(float[] queryVector, String expandedQuery, int topK) {
        return searchHybridFiltered(queryVector, expandedQuery, topK, null);
    }

    @Override
    public List<LawKnowledge> searchHybridFiltered(float[] queryVector, String expandedQuery, int topK, String lawType) {
        boolean hasVector = queryVector != null && queryVector.length > 0;
        boolean hasFilter = lawType != null && !lawType.isBlank();
        // 向量搜索取更多结果，以便后续过滤
        int fetchSize = Math.max(topK * 3, 40);

        // 1. 向量搜索（带元数据过滤：仅 EFFECTIVE，可选 lawType）
        Map<Long, Integer> vectorRanks = new LinkedHashMap<>();
        if (hasVector) {
            try {
                List<RedisVectorUtil.SearchResult> vectorResults =
                        lawKnowledgeRedisUtil.searchLawKnowledge(queryVector, fetchSize);
                int rank = 1;
                for (RedisVectorUtil.SearchResult vr : vectorResults) {
                    Long id = extractId(vr.getKey());
                    if (id != null) {
                        // 后置元数据过滤：检查 lawType 和 status
                        if (hasFilter) {
                            LawKnowledge k = hydrateKnowledge(id);
                            if (k == null) continue;
                            if (!"EFFECTIVE".equals(k.getStatus())) continue;
                            if (!lawType.equals(k.getLawType())) continue;
                        }
                        vectorRanks.put(id, rank);
                        rank++;
                    }
                }
                log.info("向量搜索完成: 结果数={} lawType={}", vectorRanks.size(), lawType);
            } catch (Exception e) {
                log.warn("向量搜索失败，仅使用全文搜索: {}", e.getMessage());
            }
        }

        // 2. 全文搜索（带元数据过滤：仅搜索 EFFECTIVE 法律，可选限定 law_type）
        //    自然语言查询 → NATURAL LANGUAGE MODE，关键词查询 → BOOLEAN MODE，均失败 → LIKE 兜底
        Map<Long, Integer> fulltextRanks = new LinkedHashMap<>();
        try {
            List<String> terms = splitSearchTerms(expandedQuery);
            if (!terms.isEmpty() && canUseFulltext(terms)) {
                List<LawKnowledge> fulltextResults;

                if (isNaturalLanguageQuery(expandedQuery)) {
                    // 自然语言查询：直接使用 NATURAL LANGUAGE MODE（ngram BOOLEAN MODE 对中文长句几乎永远返回0）
                    fulltextResults = lawKnowledgeMapper.searchFulltextFilteredNatural(
                            expandedQuery, lawType, 0, fetchSize);
                    if (fulltextResults.isEmpty()) {
                        log.info("NATURAL LANGUAGE MODE 返回0，降级为 LIKE 搜索: queryLen={}",
                                expandedQuery.length());
                        fulltextResults = fallbackLikeSearch(expandedQuery, lawType, fetchSize);
                    }
                } else {
                    // 关键词查询：使用 BOOLEAN MODE
                    String booleanQuery = buildBooleanQuery(terms);
                    fulltextResults = lawKnowledgeMapper.searchFulltextFiltered(
                            booleanQuery, lawType, 0, fetchSize);
                    if (fulltextResults.isEmpty()) {
                        log.info("BOOLEAN MODE 返回0，降级为 NATURAL LANGUAGE MODE: queryLen={}",
                                expandedQuery.length());
                        fulltextResults = lawKnowledgeMapper.searchFulltextFilteredNatural(
                                expandedQuery, lawType, 0, fetchSize);
                    }
                    if (fulltextResults.isEmpty()) {
                        log.info("全文检索均返回0，降级为 LIKE 搜索: query={}",
                                expandedQuery.length() > 40 ? expandedQuery.substring(0, 40) + "..." : expandedQuery);
                        fulltextResults = fallbackLikeSearch(expandedQuery, lawType, fetchSize);
                    }
                }

                int rank = 1;
                for (LawKnowledge ft : fulltextResults) {
                    fulltextRanks.put(ft.getId(), rank);
                    rank++;
                }
                log.info("全文搜索完成: mode={} lawType={} 结果数={}",
                        fulltextResults.isEmpty() ? "NONE" : "HIT",
                        lawType, fulltextResults.size());
            } else {
                log.debug("查询词不满足全文搜索条件，尝试 LIKE 降级: terms={}", terms);
                List<LawKnowledge> likeResults = fallbackLikeSearch(expandedQuery, lawType, fetchSize);
                int rank = 1;
                for (LawKnowledge ft : likeResults) {
                    fulltextRanks.put(ft.getId(), rank);
                    rank++;
                }
            }
        } catch (Exception e) {
            log.warn("全文搜索失败: {}", e.getMessage());
        }

        // 3. RRF 融合
        Map<Long, Double> rrfScores = computeRRF(vectorRanks, fulltextRanks);

        // 4. 分数归一化到 0-1（兼容现有双阈值配置）
        normalizeScores(rrfScores);

        // 5. 按分数降序排序，取 Top-K
        List<Map.Entry<Long, Double>> sorted = new ArrayList<>(rrfScores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int limit = Math.min(topK, sorted.size());
        Map<Long, Double> finalScores = new LinkedHashMap<>();
        for (int i = 0; i < limit; i++) {
            finalScores.put(sorted.get(i).getKey(), sorted.get(i).getValue());
        }

        // 6. 填充 LawKnowledge 实体
        List<LawKnowledge> results = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : finalScores.entrySet()) {
            LawKnowledge knowledge = hydrateKnowledge(entry.getKey());
            if (knowledge != null) {
                knowledge.setScore(entry.getValue());
                results.add(knowledge);
            }
        }

        log.info("混合搜索完成: 最终结果数={}", results.size());
        return results;
    }

    /**
     * RRF 融合：score = 1/(k + rank_vector) + 1/(k + rank_fulltext)
     */
    private Map<Long, Double> computeRRF(
            Map<Long, Integer> vectorRanks,
            Map<Long, Integer> fulltextRanks) {
        Map<Long, Double> scores = new LinkedHashMap<>();

        for (Map.Entry<Long, Integer> e : vectorRanks.entrySet()) {
            scores.put(e.getKey(), 1.0 / (RRF_K + e.getValue()));
        }

        for (Map.Entry<Long, Integer> e : fulltextRanks.entrySet()) {
            scores.merge(e.getKey(), 1.0 / (RRF_K + e.getValue()), Double::sum);
        }

        return scores;
    }

    /**
     * Min-Max 归一化到 0-1（兼容双阈值 0.70/0.65）
     */
    private void normalizeScores(Map<Long, Double> scores) {
        if (scores.isEmpty()) return;
        double minScore = scores.values().stream().min(Double::compare).orElse(0.0);
        double maxScore = scores.values().stream().max(Double::compare).orElse(1.0);
        double range = maxScore - minScore;
        if (range > 0) {
            for (Map.Entry<Long, Double> e : scores.entrySet()) {
                scores.put(e.getKey(), (e.getValue() - minScore) / range);
            }
        }
    }

    /**
     * 填充 LawKnowledge 实体：先查 Redis 再回退 MySQL
     */
    private LawKnowledge hydrateKnowledge(Long id) {
        LawKnowledgeRedisUtil.LawKnowledge redisKnowledge =
                lawKnowledgeRedisUtil.getLawKnowledge(id);
        if (redisKnowledge != null) {
            LawKnowledge knowledge = new LawKnowledge();
            knowledge.setId(redisKnowledge.getId());
            knowledge.setTitle(redisKnowledge.getTitle());
            knowledge.setLawType(redisKnowledge.getLawType());
            knowledge.setContent(redisKnowledge.getContent());
            knowledge.setChapter(redisKnowledge.getChapter());
            knowledge.setSection(redisKnowledge.getSection());
            knowledge.setArticleNumber(redisKnowledge.getArticleNumber());
            knowledge.setStatus(redisKnowledge.getStatus() != null ? redisKnowledge.getStatus() : "EFFECTIVE");
            return knowledge;
        }
        return lawKnowledgeService.getById(id);
    }

    private Long extractId(String key) {
        String prefix = ragConfig.getLawVectorKeyPrefix();
        if (key != null && key.startsWith(prefix)) {
            try {
                return Long.parseLong(key.substring(prefix.length()));
            } catch (NumberFormatException e) {
                log.warn("无法从key提取ID: {}", key);
            }
        }
        return null;
    }

    // ──── 全文搜索查询构建辅助方法 ────

    private List<String> splitSearchTerms(String keyword) {
        if (keyword == null || keyword.isBlank()) return List.of();
        List<String> terms = new ArrayList<>();
        for (String part : keyword.trim().split("[\\s,，]+")) {
            if (part.isEmpty()) continue;
            Matcher matcher = LEGAL_PATTERN.matcher(part);
            int lastEnd = 0;
            while (matcher.find()) {
                String before = part.substring(lastEnd, matcher.start()).trim();
                if (!before.isEmpty()) terms.add(before);
                terms.add(matcher.group());
                lastEnd = matcher.end();
            }
            String after = part.substring(lastEnd).trim();
            if (!after.isEmpty()) terms.add(after);
            if (lastEnd == 0) terms.add(part);
        }
        List<String> result = new ArrayList<>();
        for (String term : terms) {
            if (!result.contains(term) && !STOP_WORDS.contains(term) && term.length() >= NGRAM_MIN_LENGTH) {
                result.add(term);
            }
        }
        log.debug("搜索词拆分: keyword={}, terms={}", keyword, result);
        return result;
    }

    private String buildBooleanQuery(List<String> terms) {
        StringBuilder sb = new StringBuilder();
        for (String term : terms) {
            if (!sb.isEmpty()) sb.append(" ");
            String escaped = term.replaceAll("[+\\-><()~*\"@]", "");
            sb.append("+").append(escaped);
        }
        return sb.toString();
    }

    private boolean canUseFulltext(List<String> terms) {
        if (terms.isEmpty()) return false;
        for (String term : terms) {
            if (term.length() < NGRAM_MIN_LENGTH) return false;
        }
        return true;
    }

    private boolean isNaturalLanguageQuery(String query) {
        return query != null && query.length() > NATURAL_LANG_MIN_LENGTH;
    }

    /**
     * LIKE 降级搜索：拆分查询为独立关键词，任一关键词匹配即命中（OR 逻辑）
     */
    private List<LawKnowledge> fallbackLikeSearch(String query, String lawType, int fetchSize) {
        if (query == null || query.isBlank()) return List.of();
        List<String> terms = new ArrayList<>();
        for (String part : query.split("[\\s,，]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && trimmed.length() >= NGRAM_MIN_LENGTH) {
                terms.add(trimmed);
            }
        }
        if (terms.isEmpty()) return List.of();
        if (terms.size() == 1) {
            if (lawType != null && !lawType.isBlank()) {
                return lawKnowledgeMapper.searchByKeywordAndType(terms.get(0), lawType, 0, fetchSize);
            }
            return lawKnowledgeMapper.search(terms.get(0), 0, fetchSize);
        }
        if (lawType != null && !lawType.isBlank()) {
            return lawKnowledgeMapper.searchByKeywordAndTypeOrTerms(terms, lawType, 0, fetchSize);
        }
        return lawKnowledgeMapper.searchOrTerms(terms, 0, fetchSize);
    }
}
