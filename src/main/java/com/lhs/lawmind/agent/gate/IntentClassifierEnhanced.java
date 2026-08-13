package com.lhs.lawmind.agent.gate;

import com.lhs.lawmind.llm.LLMInvoker;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 意图分类器（统一实现，替代原 {@code utils.IntentClassifier}）。
 *
 * <p>将用户问题分类为 6 种意图类型。规则优先匹配（目标覆盖 80%+），
 * 未命中时走 LLM 轻量分类（~60 tokens prompt）。同时提供 RAG 检索的
 * Top-K 调整与深度检索判断。</p>
 */
@Slf4j
@Component
public class IntentClassifierEnhanced {

    private final IntentGateConfig config;
    private final LLMInvoker llmInvoker;

    /** 意图 → 关键词列表（按优先级从高到低匹配） */
    private final Map<IntentType, List<String>> keywordRules;

    public IntentClassifierEnhanced(IntentGateConfig config, LLMInvoker llmInvoker) {
        this.config = config;
        this.llmInvoker = llmInvoker;
        this.keywordRules = buildKeywordRules();
    }

    private Map<IntentType, List<String>> buildKeywordRules() {
        Map<IntentType, List<String>> rules = new LinkedHashMap<>();
        rules.put(IntentType.ARTICLE_LOOKUP, config.intent().articleLookupKeywords());
        rules.put(IntentType.CALCULATION, config.intent().calculationKeywords());
        rules.put(IntentType.CASE_SEARCH, config.intent().caseSearchKeywords());
        rules.put(IntentType.DOCUMENT_DRAFTING, config.intent().documentDraftingKeywords());
        rules.put(IntentType.LEGAL_KNOWLEDGE, config.intent().legalKnowledgeKeywords());
        return rules;
    }

    /**
     * 分类用户问题的法律意图。
     *
     * @param question 用户问题（假定已通过 DomainGate）
     * @return 意图分类结果
     */
    public IntentResult classify(String question) {
        if (question == null || question.isBlank()) {
            return IntentResult.rule(IntentType.LEGAL_CONSULTATION, 0.3, "agent");
        }

        String trimmed = question.trim();

        // 规则层：按优先级匹配关键词
        for (Map.Entry<IntentType, List<String>> entry : keywordRules.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (trimmed.contains(keyword)) {
                    IntentType intentType = entry.getKey();
                    String route = suggestRoute(intentType);
                    log.info("[IntentClassifier] 规则命中: intent={}, keyword={}, route={}",
                            intentType, keyword, route);
                    return IntentResult.rule(intentType, 0.9, route);
                }
            }
        }

        // LLM 兜底
        if (config.ruleOnly()) {
            log.info("[IntentClassifier] 纯规则模式，未命中默认 LEGAL_CONSULTATION");
            return IntentResult.rule(IntentType.LEGAL_CONSULTATION, 0.5, "agent");
        }

        return llmClassify(trimmed);
    }

    /**
     * 分类并直接返回意图类型（供 RAG 管道 / Agent 工具使用）。
     */
    public IntentType classifyType(String question) {
        return classify(question).intentType();
    }

    /**
     * 根据意图调整 RAG 检索 Top-K（法条查询加量、计算类减量等）。
     */
    public int adjustTopK(IntentType intent, int defaultTopK) {
        return switch (intent) {
            case ARTICLE_LOOKUP -> Math.max(defaultTopK + 5, 15);
            case CASE_SEARCH -> Math.max(defaultTopK + 3, 13);
            case CALCULATION -> Math.max(defaultTopK - 2, 3);
            default -> defaultTopK;
        };
    }

    /**
     * 判断是否启用深度检索（多路召回 + 更多结果）。
     */
    public boolean useDeepRetrieval(IntentType intent) {
        return intent == IntentType.ARTICLE_LOOKUP || intent == IntentType.CASE_SEARCH;
    }

    /**
     * 根据意图类型建议默认路由通道。
     */
    private String suggestRoute(IntentType intentType) {
        List<String> fastIntents = config.route().fastIntents();
        List<String> hybridIntents = config.route().hybridIntents();
        String name = intentType.name();

        if (fastIntents.contains(name)) {
            return "fast";
        }
        if (hybridIntents.contains(name)) {
            return "hybrid";
        }
        return "agent";
    }

    /**
     * LLM 兜底分类（仅对关键词未命中的问题调用）。
     */
    private IntentResult llmClassify(String question) {
        String prompt = config.intent().llmPrompt() + question;
        var messages = List.of(
                SystemMessage.from("你是一个法律问题分类助手。仅输出意图类型名称，不要输出其他任何内容。"),
                UserMessage.from(prompt));

        LLMInvoker.LLMResult result = llmInvoker.invoke(messages);
        if (!result.success()) {
            log.warn("[IntentClassifier] LLM 分类失败，默认 LEGAL_CONSULTATION: {}",
                    result.answer());
            return IntentResult.rule(IntentType.LEGAL_CONSULTATION, 0.4, "agent");
        }

        String answer = result.answer().trim();
        log.info("[IntentClassifier] LLM 分类结果: answer={}", answer);

        IntentType intentType = parseIntentType(answer);
        String route = suggestRoute(intentType);
        return IntentResult.llm(intentType, 0.7, route);
    }

    /**
     * 从 LLM 输出解析意图类型。
     */
    private IntentType parseIntentType(String llmOutput) {
        String upper = llmOutput.toUpperCase().trim();
        for (IntentType type : IntentType.values()) {
            if (upper.contains(type.name())) {
                return type;
            }
        }
        // 中文匹配
        if (upper.contains("法条") || upper.contains("ARTICLE")) return IntentType.ARTICLE_LOOKUP;
        if (upper.contains("咨询") || upper.contains("CONSULTATION")) return IntentType.LEGAL_CONSULTATION;
        if (upper.contains("计算") || upper.contains("CALCULATION")) return IntentType.CALCULATION;
        if (upper.contains("案例") || upper.contains("CASE")) return IntentType.CASE_SEARCH;
        if (upper.contains("文书") || upper.contains("起草") || upper.contains("DRAFTING"))
            return IntentType.DOCUMENT_DRAFTING;
        if (upper.contains("知识") || upper.contains("概念") || upper.contains("KNOWLEDGE"))
            return IntentType.LEGAL_KNOWLEDGE;

        log.warn("[IntentClassifier] 无法解析 LLM 输出，默认 LEGAL_CONSULTATION: output={}", llmOutput);
        return IntentType.LEGAL_CONSULTATION;
    }
}
