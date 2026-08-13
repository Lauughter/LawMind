package com.lhs.lawmind.rag;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.llm.LLMInvoker;
import com.lhs.lawmind.utils.EmbeddingUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 查询增强器 —— LLM 查询改写（专用 turbo 模型）+ HyDE 假设文档生成。
 * 从 RagServiceImpl 拆出。
 */
@Slf4j
@Component
public class QueryEnhancer {

    private final LLMInvoker llmInvoker;
    private final EmbeddingUtil embeddingUtil;
    private final RagConfig ragConfig;
    private final RagPromptBuilder promptBuilder;
    private final ChatLanguageModel chatModel;

    @Value("${langchain4j.dashscope.chat-model.api-key}")
    private String dashscopeApiKey;

    /** Lazy-initialized qwen-turbo rewrite model (避免 Spring Bean 冲突) */
    private volatile ChatLanguageModel rewriteTurboModel;

    /** LLM 查询改写 LRU 缓存（500条上限，access-order淘汰） */
    private final Map<String, String> rewriteCache = Collections.synchronizedMap(
            new LinkedHashMap<>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 500;
                }
            });

    public QueryEnhancer(LLMInvoker llmInvoker, Optional<EmbeddingUtil> embeddingUtil,
                         RagConfig ragConfig, RagPromptBuilder promptBuilder,
                         ChatLanguageModel chatModel) {
        this.llmInvoker = llmInvoker;
        this.embeddingUtil = embeddingUtil.orElse(null);
        this.ragConfig = ragConfig;
        this.promptBuilder = promptBuilder;
        this.chatModel = chatModel;
    }

    /**
     * 使用 LLM 改写查询以提升检索效果（带 LRU 缓存）。
     */
    public String rewriteQueryWithLLM(String question) {
        String cached = rewriteCache.get(question);
        if (cached != null) {
            log.info("[RAG] LLM query rewrite cache hit: originalLen={}", question.length());
            return cached;
        }

        ChatLanguageModel model = getRewriteModel();
        if (model == null) {
            return null;
        }
        try {
            String rewritePrompt = promptBuilder.getRewritePrompt();
            String fullPrompt = rewritePrompt + "\n\n输入：" + question + "\n输出：";
            Response<AiMessage> response = model.generate(UserMessage.from(fullPrompt));
            String rewritten = response.content().text().trim();
            if (rewritten.isEmpty() || rewritten.equals(question)) {
                return null;
            }
            rewriteCache.put(question, rewritten);
            log.info("[RAG] LLM query rewrite: model={} originalLen={} rewrittenLen={}",
                    rewriteTurboModel != null ? "turbo" : "chat", question.length(), rewritten.length());
            return rewritten;
        } catch (Exception e) {
            log.warn("[RAG] LLM query rewrite failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 合并规则扩展查询与 LLM 改写查询：规则术语为基础，LLM 术语补充。
     */
    public String mergeQueries(String ruleQuery, String llmQuery) {
        java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
        for (String term : ruleQuery.split("\\s+")) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        int ruleTermCount = terms.size();
        for (String term : llmQuery.split("\\s+")) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        String merged = String.join(" ", terms);
        log.debug("[RAG] mergeQueries: ruleTerms={} llmNewTerms={} mergedLen={}",
                ruleTermCount, terms.size() - ruleTermCount, merged.length());
        return merged;
    }

    /**
     * HyDE — 生成假设法律文档用于检索。失败返回 null（调用方降级到查询向量）。
     */
    public String generateHydeDocument(String question) {
        if (!llmInvoker.isAvailable()) {
            return null;
        }
        try {
            String hydePrompt = promptBuilder.getHydePrompt();
            String fullPrompt = hydePrompt + "\n\n用户问题：" + question;
            LLMInvoker.LLMResult hydeResult = llmInvoker.invoke(List.of(UserMessage.from(fullPrompt)));
            String hydeDoc = hydeResult.answer().trim();
            if (hydeDoc.isEmpty()) {
                return null;
            }
            log.info("[RAG] HyDE document generated: questionLen={} hydeLen={}", question.length(), hydeDoc.length());
            return hydeDoc;
        } catch (Exception e) {
            log.warn("[RAG] HyDE generation failed: {}", e.getMessage());
            return null;
        }
    }

    /** Lazy-init qwen-turbo, fall back to main chat model */
    private ChatLanguageModel getRewriteModel() {
        if (rewriteTurboModel != null) {
            return rewriteTurboModel;
        }
        synchronized (this) {
            if (rewriteTurboModel != null) {
                return rewriteTurboModel;
            }
            try {
                rewriteTurboModel = QwenChatModel.builder()
                        .apiKey(dashscopeApiKey)
                        .modelName("qwen-turbo")
                        .temperature(0.1f)
                        .maxTokens(256)
                        .build();
                log.info("Lazy-init qwen-turbo rewrite model successful");
                return rewriteTurboModel;
            } catch (Exception e) {
                log.warn("Failed to init qwen-turbo, falling back to qwen-plus: {}", e.getMessage());
                rewriteTurboModel = chatModel;
                return chatModel;
            }
        }
    }
}
