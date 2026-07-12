package com.lhs.lawmind.agent.compress;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Layer 2：LLM 语义摘要器 —— 调用 LLM 对长工具结果做语义压缩。
 * 保守触发，仅当净节省 > 2× 压缩成本时才执行。
 */
@Slf4j
public class SummarizingCompressor {

    private static final String COMPRESSION_PROMPT = """
            你是法律文本精简专家。将以下检索结果压缩为关键事实摘要。
            必须保留：法条编号、适用条件、法律后果、金额公式中的变量。
            可以删除：重复论述、"根据XX法规定"等引导语、案例背景故事。
            输出要求：每条信息一行，不改写法条原文措辞。总字数 ≤ 原文40%。
            """;

    private final ChatLanguageModel chatLanguageModel;
    private final TokenEstimator tokenEstimator;
    private final double minSavingsRatio;

    public SummarizingCompressor(ChatLanguageModel chatLanguageModel,
                                  TokenEstimator tokenEstimator,
                                  double minSavingsRatio) {
        this.chatLanguageModel = chatLanguageModel;
        this.tokenEstimator = tokenEstimator;
        this.minSavingsRatio = minSavingsRatio;
    }

    /**
     * 对工具结果进行语义压缩。
     * 返回压缩后的文本，如果没必要压缩则返回原文。
     */
    public String summarize(String toolName, String rawResult) {
        int originalTokens = tokenEstimator.estimate(rawResult);

        // Don't compress short results
        if (originalTokens < 400) {
            return rawResult;
        }

        int compressionCost = estimateCost(rawResult);

        // Conservative trigger: only compress if net savings is significant
        // We estimate compressed size will be ~40% of original
        int estimatedCompressedTokens = (int) (originalTokens * 0.4);
        int estimatedSavings = originalTokens - estimatedCompressedTokens;

        if (estimatedSavings < compressionCost * minSavingsRatio) {
            log.debug("[Compress] 跳过 Layer 2: tool={}, original={}, cost={}, savings={}",
                    toolName, originalTokens, compressionCost, estimatedSavings);
            return rawResult;
        }

        try {
            String result = callLLM(rawResult);
            int compressedTokens = tokenEstimator.estimate(result);

            if (compressedTokens >= originalTokens) {
                log.warn("[Compress] Layer 2 压缩无效: tool={}, original={}, compressed={}",
                        toolName, originalTokens, compressedTokens);
                return rawResult;
            }

            log.info("[Compress] Layer 2 压缩完成: tool={}, {} -> {} tokens (节省 {}%)",
                    toolName, originalTokens, compressedTokens,
                    (originalTokens - compressedTokens) * 100 / originalTokens);
            return result;

        } catch (Exception e) {
            log.error("[Compress] Layer 2 压缩失败: tool={}, error={}", toolName, e.getMessage());
            return rawResult; // Fallback to original
        }
    }

    /**
     * 估算压缩调用的 token 成本。
     */
    public int estimateCost(String rawResult) {
        int promptTokens = tokenEstimator.estimate(COMPRESSION_PROMPT);
        int inputTokens = tokenEstimator.estimate(rawResult);
        // Output is estimated at 40% of input
        int outputTokens = (int) (inputTokens * 0.4);
        return promptTokens + inputTokens + outputTokens;
    }

    private String callLLM(String rawResult) {
        Response<AiMessage> response = chatLanguageModel.generate(List.of(
                SystemMessage.from(COMPRESSION_PROMPT),
                UserMessage.from(rawResult)
        ));
        return response.content().text();
    }
}
