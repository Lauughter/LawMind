package com.lhs.lawmind.evaluation;

import com.lhs.lawmind.utils.EmbeddingUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Answer Relevance（回答相关性）评估器。
 * 基于回答反向生成问题，计算生成问题与原问题的余弦相似度。
 *
 * <p>方法：
 * <ol>
 *   <li>LLM 根据回答生成 2-3 个该回答能够解答的问题</li>
 *   <li>对每个生成问题计算与原问题的余弦相似度</li>
 *   <li>取平均相似度作为相关性得分</li>
 * </ol>
 */
@Slf4j
public class AnswerRelevanceEvaluator {

    private final ChatLanguageModel model;
    private final EmbeddingUtil embeddingUtil;

    private static final String SYSTEM_PROMPT = """
            你是一个问题生成器。给定一段 AI 回答，请生成 2-3 个该回答能够解答的问题。
            问题应该涵盖回答中的核心法律知识点。

            只输出问题列表，每行一个，以 "- " 开头。
            不要输出其他内容。""";

    public AnswerRelevanceEvaluator(Optional<ChatLanguageModel> model, Optional<EmbeddingUtil> embeddingUtil) {
        this.model = model.orElse(null);
        this.embeddingUtil = embeddingUtil.orElse(null);
    }

    /**
     * 评估回答与原始问题的相关性
     * @param originalQuestion 原始用户问题
     * @param answer           LLM 生成的回答
     * @return 相关性得分 0.0-1.0
     */
    public double evaluate(String originalQuestion, String answer) {
        if (model == null || embeddingUtil == null
                || originalQuestion == null || originalQuestion.isBlank()
                || answer == null || answer.isBlank()) {
            return 0.0;
        }

        List<String> generatedQuestions = generateReverseQuestions(answer);
        if (generatedQuestions.isEmpty()) return 0.0;

        float[] qVec = embeddingUtil.embed(originalQuestion);
        if (qVec == null || qVec.length == 0) return 0.0;

        double totalSimilarity = 0;
        int count = 0;
        for (String gq : generatedQuestions) {
            float[] gqVec = embeddingUtil.embed(gq);
            if (gqVec != null && gqVec.length > 0) {
                totalSimilarity += cosineSimilarity(qVec, gqVec);
                count++;
            }
        }
        return count > 0 ? Math.min(1.0, totalSimilarity / count) : 0.0;
    }

    // ──────────── 内部方法 ────────────

    private List<String> generateReverseQuestions(String answer) {
        List<String> questions = new ArrayList<>();
        try {
            Response<AiMessage> response = model.generate(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from("回答：\n" + answer)
            );
            String text = response.content().text();
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("- ")) {
                    questions.add(trimmed.substring(2).trim());
                }
            }
        } catch (Exception e) {
            log.warn("反向问题生成失败: {}", e.getMessage());
        }
        return questions;
    }

    static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
