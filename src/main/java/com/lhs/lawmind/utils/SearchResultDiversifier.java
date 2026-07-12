package com.lhs.lawmind.utils;

import com.lhs.lawmind.entity.LawKnowledge;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索结果多样化器
 *
 * <p>使用 MMR（Maximum Marginal Relevance，最大边际相关性）算法对搜索结果重排序，
 * 避免 Top-K 结果聚集在同一法律的同一章节，提升结果覆盖广度。</p>
 *
 * <p>MMR 公式: λ * relevance(doc) - (1-λ) * max_similarity(doc, already_selected)</p>
 * <p>λ 越大 -> 更偏向相关性</p>
 * <p>λ 越小 -> 更偏向多样性</p>
 *
 * <p>文档相似度基于 lawType + chapter 二维度：</p>
 * <ul>
 *   <li>相同法律 + 相同章节 → 1.0（高度相似）</li>
 *   <li>相同法律 + 不同章节 → 0.5（部分相似）</li>
 *   <li>不同法律 → 0.0（不相似）</li>
 * </ul>
 */
@Slf4j
@Component
public class SearchResultDiversifier {

    /**
     * 对搜索结果进行 MMR 多样化重排序
     *
     * @param results   原始排序结果（需已设置 score）
     * @param queryText 查询文本（保留用于未来扩展）
     * @param topK      返回数量
     * @param lambda    权衡参数（0-1），越高越注重相关性，越低越注重多样性
     * @return 多样化后的结果列表
     */
    public List<LawKnowledge> diversify(
            List<LawKnowledge> results,
            String queryText,
            int topK,
            double lambda) {

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        if (results.size() <= 1) {
            return new ArrayList<>(results);
        }

        // 归一化相关性分数到 0-1
        double maxScore = results.stream()
                .mapToDouble(k -> k.getScore() != null ? k.getScore() : 0.0)
                .max().orElse(1.0);
        if (maxScore <= 0) {
            // 避免除以零，所有分数都为0时默认最大分数为1
            maxScore = 1.0;
        }

        // 剩余待选的结果列表
        List<LawKnowledge> remaining = new ArrayList<>(results);
        // 已选择的结果列表
        List<LawKnowledge> selected = new ArrayList<>();

        // 目标数量不能超过原始结果数量
        int targetSize = Math.min(topK, results.size());

        // 迭代选择 MMR 最高的文档加入已选列表，直到达到目标数量或没有剩余文档
        for (int i = 0; i < targetSize && !remaining.isEmpty(); i++) {
            // 每轮选择 MMR 评分最高的文档, bestMnr是当前轮次的最高 MMR 分数，bestIdx 是对应的索引
            double bestMmr = Double.NEGATIVE_INFINITY;
            // 记录当前轮次 MMR 最高的文档索引，初始为 -1 表示未找到
            int bestIdx = -1;

            // 遍历剩余文档，计算每个文档的 MMR 分数,选择 MMR 最高的文档加入已选列表
            for (int j = 0; j < remaining.size(); j++) {
                // candidate 是当前轮次的候选文档，relevance 是其归一化相关性分数，diversityPenalty 是其与已选文档的最大相似度惩罚，mmr 是最终的 MMR 评分
                LawKnowledge candidate = remaining.get(j);
                double relevance = (candidate.getScore() != null ? candidate.getScore() : 0.0) / maxScore;
                // 计算候选文档与已选文档的最大相似度惩罚，越相似惩罚越大，降低 MMR 评分，促进多样性，选择 MMR 最高的文档加入已选列表
                // 如果已选列表为空，则多样性惩罚为0，完全根据相关性评分选择第一个文档加入已选列表
                // diversityPenalty是候选文档与已选文档的最大相似度惩罚，越相似惩罚越大，降低 MMR 评分，促进多样性，选择 MMR 最高的文档加入已选列表
                // 如果已选列表为空，则多样性惩罚为0，完全根据相关性评分选择第一个文档加入已选列表
                double diversityPenalty = 0.0;

                if (!selected.isEmpty()) {
                    for (LawKnowledge sel : selected) {
                        double docSim = computeDocumentSimilarity(candidate, sel);
                        if (docSim > diversityPenalty) {
                            diversityPenalty = docSim;
                        }
                    }
                }

                // MMR 评分 = λ * 相关性 - (1-λ) * 最大相似度惩罚，选择 MMR 最大边际相关性最高的文档加入已选列表
                double mmr = lambda * relevance - (1.0 - lambda) * diversityPenalty;

                if (mmr > bestMmr) {
                    bestMmr = mmr;
                    bestIdx = j;
                }
            }

            if (bestIdx >= 0) {
                LawKnowledge chosen = remaining.remove(bestIdx);
                selected.add(chosen);
                log.debug("MMR选择: id={}, title={}, lawType={}, chapter={}, score={}",
                        chosen.getId(), chosen.getTitle(), chosen.getLawType(),
                        chosen.getChapter(), String.format("%.4f", bestMmr));
            }
        }

        log.info("MMR多样化完成: 输入={}, 输出={}, lambda={}", results.size(), selected.size(), lambda);
        return selected;
    }

    /**
     * 计算文档间相似度（基于法律类型和章节）
     */
    private double computeDocumentSimilarity(LawKnowledge a, LawKnowledge b) {
        if (a == null || b == null) {
            return 0.0;
        }

        String lawTypeA = a.getLawType();
        String lawTypeB = b.getLawType();

        if (lawTypeA == null || lawTypeB == null || !lawTypeA.equals(lawTypeB)) {
            return 0.0;
        }

        String chapterA = a.getChapter();
        String chapterB = b.getChapter();

        if (chapterA != null && chapterB != null && chapterA.equals(chapterB)) {
            return 1.0;
        }

        if (chapterA == null && chapterB == null) {
            return 1.0;
        }

        return 0.5;
    }
}
