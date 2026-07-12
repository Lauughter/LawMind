package com.lhs.lawmind.evaluation;

import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Golden Dataset 评估执行器。
 * 逐条调用 RAG 管道，按多维度评分，生成评估报告。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoldenDatasetEvaluator {

    private final RagService ragService;
    private final EvalReportPersistenceService persistenceService;
    private final RagasEvaluationService ragasService;

    private static final int MIN_ANSWER_LENGTH = 50;
    private static final String REJECTION_PREFIX = "抱歉，我是一个法律咨询助手";

    /**
     * 对指定路径的 Golden Dataset 执行完整评估
     */
    public EvaluationReport evaluate(String datasetPath) {
        GoldenDatasetLoader loader = new GoldenDatasetLoader();
        List<GoldenDatasetRecord> records = loader.load(datasetPath);
        EvaluationReport report = evaluateRecords(records);
        persistenceService.saveReport(report, datasetPath);
        return report;
    }

    /**
     * 使用默认路径执行评估
     */
    public EvaluationReport evaluate() {
        return evaluate("docs/golden-dataset-rag-evaluation.json");
    }

    /**
     * 对内存中的记录列表执行评估（供测试使用）
     */
    public EvaluationReport evaluateRecords(List<GoldenDatasetRecord> records) {
        if (records.isEmpty()) {
            log.warn("Golden Dataset 为空");
            return new EvaluationReport(List.of());
        }

        List<EvalResult> results = new ArrayList<>();
        int total = records.size();
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < total; i++) {
            GoldenDatasetRecord record = records.get(i);
            log.info("[评估 {}/{}] {}", i + 1, total, record.getId());
            results.add(evaluateOne(record));
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("评估完成: {} 条用例, 耗时 {}ms", total, elapsed);

        return new EvaluationReport(results);
    }

    // ──────────── 内部方法 ────────────

    private EvalResult evaluateOne(GoldenDatasetRecord record) {
        AIChatResponse response;
        try {
            response = ragService.processQuestion(1L, record.getQuestion(), null);
        } catch (Exception e) {
            log.error("评估执行异常: id={}, error={}", record.getId(), e.getMessage());
            return new EvalResult(record.getId(), record.getQuestion(), "ERROR: " + e.getMessage(),
                    false, 0.0, false, false, Map.of("error", e.getMessage()));
        }

        String answer = response.getAnswer() != null ? response.getAnswer() : "";
        List<?> knowledge = response.getRelatedKnowledge() != null
                ? response.getRelatedKnowledge() : List.of();

        boolean sourceMatch = evaluateSourceMatch(record.getSourceRequirement(), answer, knowledge);
        double keywordRecall = evaluateKeywordRecall(record.getExpectedAnswerContains(), answer);
        boolean lawTypeMatch = evaluateLawTypeMatch(record.getExpectedLawType(), knowledge);
        boolean answerMinLength = answer.length() >= MIN_ANSWER_LENGTH;
        boolean forbiddenContentClean = evaluateForbiddenContent(record.getForbiddenContent(), answer);
        boolean minRetrievalOk = evaluateMinRetrievalCount(record.getMinRetrievalCount(), knowledge);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sourceMatch", sourceMatch);
        details.put("keywordRecall", keywordRecall);
        details.put("lawTypeMatch", lawTypeMatch);
        details.put("answerMinLength", answerMinLength);
        details.put("forbiddenContentClean", forbiddenContentClean);
        details.put("minRetrievalOk", minRetrievalOk);
        details.put("answerLength", answer.length());
        details.put("knowledgeCount", knowledge.size());
        details.put("answerPreview", answer.length() > 200 ? answer.substring(0, 200) + "..." : answer);

        // RAGAS 双维度评估
        try {
            List<String> contexts = knowledge.stream()
                    .map(Object::toString)
                    .toList();
            RagasMetrics ragas = ragasService.evaluateSingle(record.getQuestion(), answer, contexts);
            details.put("faithfulness", ragas.faithfulness());
            details.put("answerRelevance", ragas.answerRelevance());
            log.debug("[评估 RAGAS] id={} faithfulness={:.2f} relevance={:.2f}",
                    record.getId(), ragas.faithfulness(), ragas.answerRelevance());
        } catch (Exception e) {
            log.warn("[评估 RAGAS] 失败 id={}: {}", record.getId(), e.getMessage());
            details.put("faithfulness", 0.0);
            details.put("answerRelevance", 0.0);
        }

        return new EvalResult(record.getId(), record.getQuestion(), answer,
                sourceMatch, keywordRecall, lawTypeMatch, answerMinLength, details);
    }

    /** 判断实际来源是否符合期望 */
    private boolean evaluateSourceMatch(String expected, String answer, List<?> knowledge) {
        if (expected == null) return true;
        boolean hasKnowledge = !knowledge.isEmpty();
        boolean isRejection = answer.startsWith(REJECTION_PREFIX);

        return switch (expected) {
            case "non_legal_reject" -> isRejection;
            case "llm_direct" -> !isRejection && !hasKnowledge;
            case "law_knowledge", "similar_question", "hot_cache" -> hasKnowledge;
            default -> true;
        };
    }

    /** 计算期望关键词在回答中的命中率 */
    private double evaluateKeywordRecall(List<String> expectedKeywords, String answer) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) return 1.0;
        String lowerAnswer = answer.toLowerCase();
        long hits = expectedKeywords.stream()
                .filter(kw -> lowerAnswer.contains(kw.toLowerCase()))
                .count();
        return (double) hits / expectedKeywords.size();
    }

    /** 检查期望的法律类型是否出现在检索结果中 */
    @SuppressWarnings("unchecked")
    private boolean evaluateLawTypeMatch(String expectedLawType, List<?> knowledge) {
        if (expectedLawType == null || knowledge.isEmpty()) return true;
        for (Object item : knowledge) {
            if (item instanceof Map<?, ?> m) {
                Object title = m.get("title");
                if (title != null && title.toString().contains(expectedLawType)) return true;
            }
        }
        return false;
    }

    /** 检查回答是否不包含禁止内容（敏感话题/误导信息等） */
    private boolean evaluateForbiddenContent(String forbiddenContent, String answer) {
        if (forbiddenContent == null) return true;
        String lowerAnswer = answer.toLowerCase();
        for (String term : forbiddenContent.split(",")) {
            if (lowerAnswer.contains(term.trim().toLowerCase())) return false;
        }
        return true;
    }

    /** 检查检索结果数量是否满足最低要求 */
    private boolean evaluateMinRetrievalCount(Integer minCount, List<?> knowledge) {
        if (minCount == null) return true;
        return knowledge.size() >= minCount;
    }
}
