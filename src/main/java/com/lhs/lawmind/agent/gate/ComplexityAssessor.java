package com.lhs.lawmind.agent.gate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Layer 3 — 复杂度评估器。
 *
 * <p>多因子加权评估用户问题的复杂度：
 * <ul>
 *   <li>涉及法律数量（40%）— 命中的复杂法律关键词越多，越复杂</li>
 *   <li>是否需要计算（20%）— 含金额数字则偏高</li>
 *   <li>问题分句数（20%）— 句子越多，场景越复杂</li>
 *   <li>是否涉及程序（20%）— 含程序关键词则偏高</li>
 * </ul>
 *
 * <p>输出 {@link ComplexityLevel}：SIMPLE / MEDIUM / COMPLEX。</p>
 */
@Slf4j
@Component
public class ComplexityAssessor {

    private final IntentGateConfig config;

    public ComplexityAssessor(IntentGateConfig config) {
        this.config = config;
    }

    /**
     * 评估问题复杂度。
     *
     * @param question 用户问题
     * @return 复杂度等级
     */
    public ComplexityLevel assess(String question) {
        if (question == null || question.isBlank()) {
            return ComplexityLevel.SIMPLE;
        }

        String trimmed = question.trim();

        // 因子 1：涉及法律数量（0.0~1.0）
        double lawScore = computeLawScore(trimmed);

        // 因子 2：是否需要计算（0.0 或 1.0）
        double calcScore = computeCalcScore(trimmed);

        // 因子 3：问题分句数（0.0~1.0）
        double clauseScore = computeClauseScore(trimmed);

        // 因子 4：是否涉及程序（0.0~1.0）
        double procedureScore = computeProcedureScore(trimmed);

        var weights = config.complexity();
        double total = lawScore * weights.involvedLawsWeight()
                + calcScore * weights.calculationNeededWeight()
                + clauseScore * weights.questionClausesWeight()
                + procedureScore * weights.procedureInvolvedWeight();

        ComplexityLevel level;
        if (total <= weights.simpleThreshold()) {
            level = ComplexityLevel.SIMPLE;
        } else if (total >= weights.complexThreshold()) {
            level = ComplexityLevel.COMPLEX;
        } else {
            level = ComplexityLevel.MEDIUM;
        }

        log.info("[Complexity] 评估结果: score={:.3f}, level={}, laws={:.2f}, calc={:.2f}, clauses={:.2f}, proc={:.2f}",
                total, level, lawScore, calcScore, clauseScore, procedureScore);
        return level;
    }

    private double computeLawScore(String question) {
        List<String> keywords = config.complexity().complexLawKeywords();
        if (keywords.isEmpty()) return 0.0;

        int hits = 0;
        for (String keyword : keywords) {
            if (question.contains(keyword)) hits++;
        }
        // 命中 1 个 = 0.3，每多一个加 0.2，上限 1.0
        if (hits == 0) return 0.0;
        return Math.min(1.0, 0.3 + (hits - 1) * 0.2);
    }

    private double computeCalcScore(String question) {
        // 检测金额数字模式
        boolean hasAmount = java.util.regex.Pattern.compile("\\d+[\\d,]*\\.?\\d*\\s*[元万元]")
                .matcher(question).find();
        // 检测计算关键词
        Set<String> calcWords = Set.of("赔偿", "补偿", "抚养费", "赡养费", "加班费", "工资",
                "利息", "罚金", "违约金", "定金");
        boolean hasCalcKeyword = calcWords.stream().anyMatch(question::contains);

        if (hasAmount && hasCalcKeyword) return 1.0;
        if (hasAmount || hasCalcKeyword) return 0.6;
        return 0.0;
    }

    private double computeClauseScore(String question) {
        // 按中英文标点分句
        String[] clauses = question.split("[。！？；\\n.！?;]+");
        int count = 0;
        for (String clause : clauses) {
            if (!clause.trim().isEmpty()) count++;
        }
        if (count <= 1) return 0.0;
        if (count == 2) return 0.3;
        if (count == 3) return 0.6;
        return 1.0;
    }

    private double computeProcedureScore(String question) {
        List<String> keywords = config.complexity().procedureKeywords();
        if (keywords.isEmpty()) return 0.0;

        int hits = 0;
        for (String keyword : keywords) {
            if (question.contains(keyword)) hits++;
        }
        if (hits == 0) return 0.0;
        return Math.min(1.0, hits * 0.33);
    }

    public enum ComplexityLevel {
        SIMPLE, MEDIUM, COMPLEX
    }
}
