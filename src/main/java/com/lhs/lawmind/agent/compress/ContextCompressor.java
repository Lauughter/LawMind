package com.lhs.lawmind.agent.compress;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 上下文压缩调度器（增强版）。
 * 集成四层递进压缩 + KnowledgeState + 递归加权 + Per-tool 差异化策略。
 */
@Slf4j
public class ContextCompressor {

    private final RuleExtractor ruleExtractor;
    private final SummarizingCompressor summarizingCompressor;
    private final TokenEstimator tokenEstimator;
    private final KnowledgeState knowledgeState;
    private final CompressionConfig config;

    public ContextCompressor(RuleExtractor ruleExtractor,
                             SummarizingCompressor summarizingCompressor,
                             TokenEstimator tokenEstimator,
                             KnowledgeState knowledgeState,
                             CompressionConfig config) {
        this.ruleExtractor = ruleExtractor;
        this.summarizingCompressor = summarizingCompressor;
        this.tokenEstimator = tokenEstimator;
        this.knowledgeState = knowledgeState;
        this.config = config;
    }

    /**
     * 压缩单条工具结果。
     * 始终更新 KnowledgeState，根据策略决定是否压缩返回文本。
     */
    public String compressToolResult(String toolName, String rawResult,
                                      List<ChatMessage> messages, int roundIndex) {
        if (rawResult == null || rawResult.isEmpty()) {
            return rawResult;
        }

        // ★ 始终更新 KnowledgeState（增量式知识积累）
        if (config.knowledgeState().enabled()) {
            int newAtoms = knowledgeState.ingest(toolName, rawResult, roundIndex);
            if (newAtoms > 0) {
                log.debug("[Compress] KnowledgeState 获取 {} 个新知识原子: tool={}, round={}",
                        newAtoms, toolName, roundIndex);
            }
        }

        CompressionConfig.ToolStrategyConfig strategy = config.getStrategy(toolName);

        // 不压缩的工具直接返回原文
        if (!strategy.compress()) {
            return rawResult;
        }

        int estimatedTokens = tokenEstimator.estimate(rawResult);

        // 递归加权：根据轮次决定激进程度
        int effectiveRound = roundIndex;
        if (effectiveRound <= config.recency().keepFullRecent()) {
            // 最近 2 轮保留原文（除非超过单条阈值很多）
            if (estimatedTokens < config.singleResultThreshold() * 2) {
                return rawResult;
            }
            // 如果单条结果太长（> 2x 阈值），至少做 Layer 0 格式优化
            effectiveRound = config.recency().layer1StartRound();
        }

        // 根据策略层和轮次选择压缩方式
        String compressed;
        if (effectiveRound >= config.recency().layer2StartRound()) {
            compressed = applyLayer2(toolName, rawResult, estimatedTokens);
        } else if (effectiveRound >= config.recency().layer1StartRound()
                || estimatedTokens > config.singleResultThreshold()) {
            compressed = applyLayer1(toolName, rawResult);
        } else {
            compressed = applyLayer0(rawResult);
        }

        // If compression produced an empty result, return original
        if (compressed == null || compressed.isEmpty()) {
            return rawResult;
        }

        int compressedTokens = tokenEstimator.estimate(compressed);
        if (compressedTokens < estimatedTokens) {
            log.debug("[Compress] tool={}, round={}, {} -> {} tokens ({}% saved)",
                    toolName, roundIndex, estimatedTokens, compressedTokens,
                    (estimatedTokens - compressedTokens) * 100 / Math.max(estimatedTokens, 1));
        }

        return compressed;
    }

    /**
     * 构建最终精简上下文 —— 用 KnowledgeState 结构化摘要替代散落的工具结果。
     * 在 Agent 达到 maxIterations 时调用。
     */
    public String buildFinalContext(String userQuestion) {
        return knowledgeState.toCompactSummary();
    }

    /**
     * 构建精简版消息列表，用于最终答案生成。
     */
    public List<ChatMessage> buildFinalMessages(String systemPrompt, String userQuestion,
                                                  String knowledgeSummary) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(userQuestion));
        messages.add(UserMessage.from(
                "以下是检索到的关键法条和信息的结构化摘要：\n\n" + knowledgeSummary));
        messages.add(UserMessage.from("请基于以上信息直接给出最终回答，不要再调用工具。"));
        return messages;
    }

    /**
     * 检查是否需要压缩（全局上下文超阈值）。
     */
    public boolean needsCompression(List<ChatMessage> messages) {
        return tokenEstimator.estimate(messages) > config.totalContextThreshold();
    }

    public KnowledgeState getKnowledgeState() {
        return knowledgeState;
    }

    // ---- Private compression methods ----

    private String applyLayer0(String rawResult) {
        // Level 0: Strip decorative text and normalize whitespace
        return rawResult
                .replaceAll("(?m)^---\\s*$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String applyLayer1(String toolName, String rawResult) {
        RuleExtractor.ExtractResult result = ruleExtractor.extract(toolName, rawResult);
        return result.summary();
    }

    private String applyLayer2(String toolName, String rawResult, int estimatedTokens) {
        // Only use Layer 2 if the text is long enough
        if (estimatedTokens < 400) {
            return applyLayer1(toolName, rawResult);
        }
        return summarizingCompressor.summarize(toolName, rawResult);
    }
}
