package com.lhs.lawmind.agent.gate;

import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.llm.LLMInvoker;
import com.lhs.lawmind.rag.RagPromptBuilder;
import com.lhs.lawmind.rag.RagRetrievalService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.query.LegalQueryExpander;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 快速通道处理器。
 *
 * <p>用于简单问题的快速处理：复用 RAG 检索管道（RagRetrievalService）与 prompt 构建
 * （RagPromptBuilder），单次 LLM 生成，不走 Agent 循环。</p>
 *
 * <p>P1-7：检索与系统提示词复用 RAG 管道，消除与 RagServiceImpl 的平行实现。</p>
 */
@Slf4j
@Component
public class FastChannelHandler {

    private final LLMInvoker llmInvoker;
    private final RagPromptBuilder promptBuilder;
    private final RagRetrievalService retrievalService;
    private final EmbeddingUtil embeddingUtil;
    private final LegalQueryExpander legalQueryExpander;
    private final LawKnowledgeService lawKnowledgeService;

    public FastChannelHandler(LLMInvoker llmInvoker,
                              RagPromptBuilder promptBuilder,
                              RagRetrievalService retrievalService,
                              Optional<EmbeddingUtil> embeddingUtil,
                              LegalQueryExpander legalQueryExpander,
                              LawKnowledgeService lawKnowledgeService) {
        this.llmInvoker = llmInvoker;
        this.promptBuilder = promptBuilder;
        this.retrievalService = retrievalService;
        this.embeddingUtil = embeddingUtil.orElse(null);
        this.legalQueryExpander = legalQueryExpander;
        this.lawKnowledgeService = lawKnowledgeService;
    }

    /**
     * 快速处理法律问题。
     *
     * @param question   用户问题
     * @param intentType 意图类型（用于调整检索策略）
     * @return AI 生成的回答文本
     */
    public String handle(String question, IntentType intentType) {
        long startTime = System.currentTimeMillis();
        int topK = topKFor(intentType);

        // 1. 查询扩展 + 向量化 + 混合检索（复用 RAG 检索管道）
        String expandedQuery = legalQueryExpander.expandQuery(question);
        float[] vector = embed(expandedQuery);
        List<LawKnowledge> knowledgeList;
        if (vector != null && vector.length > 0) {
            knowledgeList = retrievalService.searchLawKnowledgeFiltered(vector, expandedQuery, null, topK);
        } else {
            // 向量不可用时回退关键词检索
            knowledgeList = lawKnowledgeService.search(question, 1, topK);
        }

        log.info("[FastChannel] 检索完成: results={}, elapsed={}ms",
                knowledgeList.size(), System.currentTimeMillis() - startTime);

        // 2. 构建消息（系统提示词复用 RAG，策略提示保留 Fast 定制）
        String knowledgeContext = buildKnowledgeContext(knowledgeList);
        var messages = List.of(
                promptBuilder.buildSystemPrompt(),
                UserMessage.from(buildUserPrompt(question, knowledgeContext, intentType)));

        // 3. 单次 LLM 生成
        LLMInvoker.LLMResult result = llmInvoker.invoke(messages);
        if (!result.success()) {
            log.error("[FastChannel] LLM 生成失败: {}", result.answer());
            return "抱歉，回答生成失败：" + result.answer();
        }

        String answer = result.answer();
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[FastChannel] 回答生成完成: answerLen={}, elapsed={}ms",
                answer != null ? answer.length() : 0, elapsed);

        return answer != null && !answer.isBlank() ? answer : "抱歉，生成回答时出现问题，请稍后重试。";
    }

    private int topKFor(IntentType intentType) {
        return switch (intentType) {
            case ARTICLE_LOOKUP -> 10;
            case CASE_SEARCH -> 8;
            default -> 5;
        };
    }

    private float[] embed(String expandedQuery) {
        if (embeddingUtil == null) {
            return null;
        }
        try {
            return embeddingUtil.embed(expandedQuery);
        } catch (Exception e) {
            log.warn("[FastChannel] 向量化失败，回退关键词检索: {}", e.getMessage());
            return null;
        }
    }

    private String buildKnowledgeContext(List<LawKnowledge> knowledgeList) {
        if (knowledgeList.isEmpty()) {
            return "（知识库中暂无直接相关内容）";
        }
        return knowledgeList.stream()
                .map(k -> String.format("[%s] %s: %s",
                        k.getLawType() != null ? k.getLawType() : "法律知识",
                        k.getTitle() != null ? k.getTitle() : "",
                        truncate(k.getContent(), 300)))
                .collect(Collectors.joining("\n"));
    }

    private String buildUserPrompt(String question, String knowledgeContext, IntentType intentType) {
        String strategyHint = switch (intentType) {
            case ARTICLE_LOOKUP -> "用户正在查询具体法条，请直接提供法条原文内容并简要解释。";
            case CALCULATION -> "用户需要计算赔偿/金额，请逐步列出公式和计算过程。";
            case CASE_SEARCH -> "用户想了解类似案例，请参考知识库中的案例进行说明。";
            case DOCUMENT_DRAFTING -> "用户需要法律文书模板，请提供文书结构和关键条款示例。";
            case LEGAL_KNOWLEDGE -> "用户想了解法律概念，请用通俗易懂的语言解释。";
            default -> "用户在进行法律咨询，请根据知识库内容给出专业建议。";
        };

        return String.format("""
                %s

                ## 知识库检索结果
                %s

                ## 用户问题
                %s
                """, strategyHint, knowledgeContext, question);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
