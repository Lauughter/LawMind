package com.lhs.lawmind.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.File;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden Dataset 评估回归测试。
 * 需要完整 Spring 上下文（Redis + MySQL + LLM），
 * 执行耗时约 2-5 分钟，仅在 -P evaluation 时运行。
 */
@SpringBootTest
@Tag("evaluation")
@DisplayName("Golden Dataset Evaluation")
class GoldenDatasetEvaluatorTest {

    @Autowired
    private GoldenDatasetEvaluator evaluator;

    private static final double MIN_KEYWORD_RECALL = 0.40;
    private static final double MIN_TOTAL_SCORE = 0.40;

    @Test
    @DisplayName("Golden Dataset 全量回归：关键词召回率 ≥ 40%, 综合得分 ≥ 0.4")
    void goldenDatasetRegression() {
        EvaluationReport report = evaluator.evaluate();

        assertThat(report.getTotalCases())
                .as("Golden Dataset 用例数")
                .isGreaterThan(0);

        double keywordRecall = report.getDimensionAverages().getOrDefault("keywordRecall", 0.0);
        double totalScore = report.getDimensionAverages().getOrDefault("totalScore", 0.0);

        System.out.println("=== Golden Dataset 评估报告 ===");
        System.out.printf("总用例: %d  通过: %d  失败: %d%n",
                report.getTotalCases(), report.getPassedCases(), report.getFailedCases());
        System.out.printf("关键词召回率: %.2f  综合得分: %.2f%n", keywordRecall, totalScore);
        System.out.printf("来源匹配率: %.2f  法律类型匹配: %.2f  最低回答长度: %.2f%n",
                report.getDimensionAverages().getOrDefault("sourceMatch", 0.0),
                report.getDimensionAverages().getOrDefault("lawTypeMatch", 0.0),
                report.getDimensionAverages().getOrDefault("answerMinLength", 0.0));

        if (!report.getFailedResults().isEmpty()) {
            System.out.println("\n--- 失败用例详情 ---");
            for (EvalResult r : report.getFailedResults()) {
                System.out.printf("  [%s] %s (得分: %.2f)%n",
                        r.id(), r.question().substring(0, Math.min(40, r.question().length())), r.totalScore());
                System.out.printf("    sourceMatch=%s keywordRecall=%.2f lawTypeMatch=%s answerMinLength=%s%n",
                        r.sourceMatch(), r.keywordRecall(), r.lawTypeMatch(), r.answerMinLength());
            }
        }

        // 保存 JSON 报告
        try {
            ObjectMapper mapper = new ObjectMapper();
            File reportDir = new File("target/evaluation");
            reportDir.mkdirs();
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(reportDir, "golden-eval-report.json"), report);
            System.out.println("\n报告已保存: target/evaluation/golden-eval-report.json");
        } catch (Exception e) {
            System.err.println("保存报告失败: " + e.getMessage());
        }

        assertThat(keywordRecall)
                .as("关键词召回率不应低于 %.0f%%", MIN_KEYWORD_RECALL * 100)
                .isGreaterThanOrEqualTo(MIN_KEYWORD_RECALL);

        assertThat(totalScore)
                .as("综合得分不应低于 %.0f", MIN_TOTAL_SCORE)
                .isGreaterThanOrEqualTo(MIN_TOTAL_SCORE);
    }

    @Test
    @DisplayName("Golden Dataset 基础验证：每条用例格式正确")
    void goldenDatasetSchemaValid() {
        GoldenDatasetLoader loader = new GoldenDatasetLoader();
        var records = loader.load();

        assertThat(records).isNotEmpty();

        for (var r : records) {
            assertThat(r.getId()).as("id 不应为空").isNotBlank();
            assertThat(r.getQuestion()).as("question 不应为空: " + r.getId()).isNotBlank();
            assertThat(r.getExpectedAnswerContains())
                    .as("expected_answer_contains 不应为空: " + r.getId())
                    .isNotEmpty();
        }
    }
}
