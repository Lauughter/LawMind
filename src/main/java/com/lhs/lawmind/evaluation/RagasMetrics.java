package com.lhs.lawmind.evaluation;

/**
 * RAGAS 风格的质量指标
 */
public record RagasMetrics(
        double faithfulness,
        double answerRelevance,
        double contextPrecision,
        double contextRecall
) {
    public static RagasMetrics empty() {
        return new RagasMetrics(0.0, 0.0, 0.0, 0.0);
    }

    public double averageScore() {
        int count = 0;
        double sum = 0;
        if (faithfulness > 0) { sum += faithfulness; count++; }
        if (answerRelevance > 0) { sum += answerRelevance; count++; }
        if (contextPrecision > 0) { sum += contextPrecision; count++; }
        if (contextRecall > 0) { sum += contextRecall; count++; }
        return count > 0 ? sum / count : 0.0;
    }
}
