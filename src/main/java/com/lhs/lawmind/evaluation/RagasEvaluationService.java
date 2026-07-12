package com.lhs.lawmind.evaluation;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import com.lhs.lawmind.utils.EmbeddingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * RAGAS 风格质量评估服务。
 * 对回答执行 Faithfulness + Answer Relevance 双维度 LLM 评分。
 */
@Slf4j
@Service
public class RagasEvaluationService {

    private final FaithfulnessEvaluator faithfulnessEvaluator;
    private final AnswerRelevanceEvaluator relevanceEvaluator;

    public RagasEvaluationService(Optional<ChatLanguageModel> chatModel,
                                   Optional<StreamingChatLanguageModel> streamingModel,
                                   Optional<EmbeddingUtil> embeddingUtil) {
        this.faithfulnessEvaluator = new FaithfulnessEvaluator(chatModel);
        this.relevanceEvaluator = new AnswerRelevanceEvaluator(chatModel, embeddingUtil);
    }

    /**
     * 评估单条回答的质量
     * @param question  原始问题
     * @param answer    LLM 回答
     * @param contexts  检索到的知识内容列表
     */
    public RagasMetrics evaluateSingle(String question, String answer, List<String> contexts) {
        double faithfulness = faithfulnessEvaluator.evaluate(answer, contexts);
        double relevance = relevanceEvaluator.evaluate(question, answer);
        log.debug("RAGAS 评估: faithfulness={:.2f}, relevance={:.2f}, question={}",
                faithfulness, relevance, question.substring(0, Math.min(30, question.length())));
        return new RagasMetrics(faithfulness, relevance, 0.0, 0.0);
    }

    /**
     * 批量评估多组问答对
     */
    public List<RagasMetrics> evaluateBatch(List<QaPair> pairs) {
        List<RagasMetrics> results = new ArrayList<>();
        for (int i = 0; i < pairs.size(); i++) {
            QaPair pair = pairs.get(i);
            log.info("[RAGAS {}/{}] {}", i + 1, pairs.size(),
                    pair.question().substring(0, Math.min(40, pair.question().length())));
            results.add(evaluateSingle(pair.question(), pair.answer(), pair.contexts()));
        }
        return results;
    }

    /** 问答对 + 上下文 */
    public record QaPair(String question, String answer, List<String> contexts) {}
}
