package com.lhs.lawmind.evaluation;

import java.util.Map;

/**
 * 单条 Golden Dataset 用例的评估结果
 */
public record EvalResult(
        String id,
        String question,
        String actualAnswer,
        boolean sourceMatch,
        double keywordRecall,
        boolean lawTypeMatch,
        boolean answerMinLength,
        Map<String, Object> details
) {
    /** 综合得分（各项取平均） */
    public double totalScore() {
        double sum = 0;
        int count = 0;
        if (details.containsKey("sourceMatch")) { sum += sourceMatch ? 1.0 : 0.0; count++; }
        if (details.containsKey("keywordRecall")) { sum += keywordRecall; count++; }
        if (details.containsKey("lawTypeMatch")) { sum += lawTypeMatch ? 1.0 : 0.0; count++; }
        if (details.containsKey("answerMinLength")) { sum += answerMinLength ? 1.0 : 0.0; count++; }
        return count > 0 ? sum / count : 0.0;
    }
}
