package com.lhs.lawmind.evaluation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 完整的评估报告，汇总所有用例的评估结果
 */
public class EvaluationReport {

    private final String generatedAt = LocalDateTime.now().toString();
    private final int totalCases;
    private final int passedCases;
    private final int failedCases;
    private final List<EvalResult> results;
    private final Map<String, Double> dimensionAverages;

    public EvaluationReport(List<EvalResult> results) {
        this.results = results;
        this.totalCases = results.size();

        int passed = 0;
        int failed = 0;
        for (EvalResult r : results) {
            if (r.totalScore() >= 0.5) passed++;
            else failed++;
        }
        this.passedCases = passed;
        this.failedCases = failed;

        this.dimensionAverages = new java.util.LinkedHashMap<>();
        dimensionAverages.put("sourceMatch", avgOf(r -> r.sourceMatch() ? 1.0 : 0.0));
        dimensionAverages.put("keywordRecall", avgOf(EvalResult::keywordRecall));
        dimensionAverages.put("lawTypeMatch", avgOf(r -> r.lawTypeMatch() ? 1.0 : 0.0));
        dimensionAverages.put("answerMinLength", avgOf(r -> r.answerMinLength() ? 1.0 : 0.0));
        dimensionAverages.put("forbiddenContentClean", avgOfDetails("forbiddenContentClean"));
        dimensionAverages.put("minRetrievalOk", avgOfDetails("minRetrievalOk"));
        dimensionAverages.put("faithfulness", avgOfDetails("faithfulness"));
        dimensionAverages.put("answerRelevance", avgOfDetails("answerRelevance"));
        dimensionAverages.put("totalScore", avgOf(EvalResult::totalScore));
    }

    public String getGeneratedAt() { return generatedAt; }
    public int getTotalCases() { return totalCases; }
    public int getPassedCases() { return passedCases; }
    public int getFailedCases() { return failedCases; }
    public List<EvalResult> getResults() { return results; }
    public Map<String, Double> getDimensionAverages() { return dimensionAverages; }

    /** 过滤出失败的用例 */
    public List<EvalResult> getFailedResults() {
        return results.stream().filter(r -> r.totalScore() < 0.5).toList();
    }

    private double avgOf(java.util.function.ToDoubleFunction<EvalResult> fn) {
        return results.stream().mapToDouble(fn).average().orElse(0.0);
    }

    private double avgOfDetails(String key) {
        return results.stream()
                .mapToDouble(r -> {
                    Object v = r.details() != null ? r.details().get(key) : null;
                    if (v instanceof Boolean b) return b ? 1.0 : 0.0;
                    if (v instanceof Number n) return n.doubleValue();
                    return 0.0;
                })
                .average().orElse(0.0);
    }
}
