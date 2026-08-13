package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.aop.annotation.NoLog;
import com.lhs.lawmind.agent.gate.IntentClassifierEnhanced;
import com.lhs.lawmind.agent.gate.IntentType;
import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.llm.LLMInvoker;
import com.lhs.lawmind.rag.CitationVerifier;
import com.lhs.lawmind.rag.LegalQuestionClassifier;
import com.lhs.lawmind.rag.QueryEnhancer;
import com.lhs.lawmind.rag.RagPromptBuilder;
import com.lhs.lawmind.rag.RagRetrievalService;
import com.lhs.lawmind.rag.SseStreamHelper;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.service.RagMetricsService;
import com.lhs.lawmind.service.RagService;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.JsonUtil;
import com.lhs.lawmind.utils.query.LegalEntityExtractor;
import com.lhs.lawmind.utils.query.LegalQueryExpander;
import com.lhs.lawmind.utils.query.TextPreprocessUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RAG 知识库检索服务 —— 管道编排器。
 *
 * <p>只保留主流程编排（processQuestion / processQuestionStream）与生成方法；
 * 检索、prompt 构建、查询增强、引用验证已拆到 {@code rag/} 子包：
 * {@link RagRetrievalService} / {@link RagPromptBuilder} / {@link QueryEnhancer} /
 * {@link CitationVerifier} / {@link LegalQuestionClassifier} / {@link SseStreamHelper}。</p>
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final EmbeddingUtil embeddingUtil;
    private final RagConfig ragConfig;
    private final AiChatService aiChatService;
    private final IntentClassifierEnhanced intentClassifierEnhanced;
    private final LegalEntityExtractor legalEntityExtractor;
    private final LegalQueryExpander legalQueryExpander;
    private final LLMInvoker llmInvoker;
    private final RagPersistenceService ragPersistenceService;
    private final RagMetricsService ragMetricsService;
    private final RagPromptBuilder promptBuilder;
    private final RagRetrievalService retrievalService;
    private final QueryEnhancer queryEnhancer;
    private final CitationVerifier citationVerifier;

    public RagServiceImpl(
            Optional<ChatLanguageModel> chatLanguageModel,
            Optional<StreamingChatLanguageModel> streamingChatLanguageModel,
            Optional<EmbeddingUtil> embeddingUtil,
            RagConfig ragConfig,
            @Lazy AiChatService aiChatService,
            IntentClassifierEnhanced intentClassifierEnhanced,
            LegalEntityExtractor legalEntityExtractor,
            LegalQueryExpander legalQueryExpander,
            LLMInvoker llmInvoker,
            RagPersistenceService ragPersistenceService,
            RagMetricsService ragMetricsService,
            RagPromptBuilder promptBuilder,
            RagRetrievalService retrievalService,
            QueryEnhancer queryEnhancer,
            CitationVerifier citationVerifier) {
        this.chatLanguageModel = chatLanguageModel.orElse(null);
        this.streamingChatLanguageModel = streamingChatLanguageModel.orElse(null);
        this.embeddingUtil = embeddingUtil.orElse(null);
        this.ragConfig = ragConfig;
        this.aiChatService = aiChatService;
        this.intentClassifierEnhanced = intentClassifierEnhanced;
        this.legalEntityExtractor = legalEntityExtractor;
        this.legalQueryExpander = legalQueryExpander;
        this.llmInvoker = llmInvoker;
        this.ragPersistenceService = ragPersistenceService;
        this.ragMetricsService = ragMetricsService;
        this.promptBuilder = promptBuilder;
        this.retrievalService = retrievalService;
        this.queryEnhancer = queryEnhancer;
        this.citationVerifier = citationVerifier;
    }

    @Override
    @NoLog
    public AIChatResponse processQuestion(Long userId, String question, Long conversationId) {
        long t0 = System.currentTimeMillis();
        long tPre, tEmbed = 0, tHyde = 0, tKnow = 0, tGen = 0;
        question = sanitizeUserInput(question);
        log.info("[RAG] q=\"{}\" userId={}", question.length() > 60 ? question.substring(0, 60) + "..." : question, userId);
        if (com.lhs.lawmind.security.PiiUtil.hasPii(question)) {
            log.warn("[RAG-PII] 检测到用户问题包含个人信息，已做日志脱敏 userId={}", userId);
        }

        // 敏感话题过滤（安全护栏，优先于法律相关性判断）
        var filterResult = com.lhs.lawmind.security.SensitiveTopicFilter.filter(question);
        if (filterResult.blocked()) {
            log.warn("[RAG-GUARD] 敏感话题拦截: category={} userId={}", filterResult.category(), userId);
            AIChatResponse response = new AIChatResponse();
            response.setAnswer(filterResult.reason());
            response.setRelatedKnowledge(new ArrayList<>());
            response.setConversationId(conversationId);
            ragPersistenceService.saveChatRecord(userId, question, response.getAnswer(), "[]", "guard_blocked", conversationId, 0, 0);
            return response;
        }

        // 检查问题是否与法律相关
        if (!LegalQuestionClassifier.isLegalRelatedQuestion(question)) {
            AIChatResponse response = new AIChatResponse();
            response.setAnswer("抱歉，我是一个法律咨询助手，只能回答与法律相关的问题。");
            response.setRelatedKnowledge(new ArrayList<>());
            response.setConversationId(conversationId);
            ragPersistenceService.saveChatRecord(userId, question, response.getAnswer(), "[]", "non_legal_reject", conversationId, 0, 0);
            long nonLegalTotal = System.currentTimeMillis() - t0;
            ragMetricsService.recordRequest("non_legal_reject", 0, 0, 0, 0, nonLegalTotal, 0, 0.0, false, null);
            log.info("[RAG-SUMMARY] source=non_legal_reject totalMs={}", nonLegalTotal);
            return response;
        }

        AIChatResponse response = new AIChatResponse();
        String answer = null;
        String source = "llm_direct";
        String knowledgeMatch = "[]";
        List<LawKnowledge> relatedKnowledge = new ArrayList<>();
        IntentType intent = null;
        String entityLawType = null;

        try {
            // Step 1: 文本预处理 + 意图分类 + 实体抽取（轻量级，不含 LLM 调用）
            TextPreprocessUtil.PreprocessResult preprocessResult = TextPreprocessUtil.preprocessAndGenerateMD5(question);
            String processedQuestion = preprocessResult.getProcessedText();
            String md5 = preprocessResult.getMd5();
            String ruleExpandedQuery = legalQueryExpander.expandQuery(processedQuestion);
            intent = intentClassifierEnhanced.classifyType(processedQuestion);
            LegalEntityExtractor.LegalEntities entities = legalEntityExtractor.extract(processedQuestion);
            entityLawType = entities.getLawType();
            // Entity-aware expansion: inject extracted law type + article reference into query
            if (entityLawType != null && !entityLawType.isBlank()) {
                StringBuilder eb = new StringBuilder(ruleExpandedQuery);
                eb.append(" ").append(entityLawType);
                if (entities.getArticleReference() != null && !entities.getArticleReference().isBlank()) {
                    eb.append(" ").append(entities.getArticleReference());
                }
                ruleExpandedQuery = eb.toString();
            }
            int adjustedTopK = intentClassifierEnhanced.adjustTopK(intent, ragConfig.getSearchTopK());
            tPre = System.currentTimeMillis();
            log.info("[RAG] preprocess md5={} intent={} lawType={} topK={} expandLen={} preMs={}",
                    md5, intent, entityLawType, adjustedTopK,
                    ruleExpandedQuery.length() - processedQuestion.length(), tPre - t0);

            // Step 3: 查询扩展 + 向量化（LLM 改写基于规则扩展结果，合并而非替换）
            String llmRewrittenQuery = queryEnhancer.rewriteQueryWithLLM(ruleExpandedQuery);
            String expandedQuery;
            if (llmRewrittenQuery != null) {
                expandedQuery = queryEnhancer.mergeQueries(ruleExpandedQuery, llmRewrittenQuery);
                log.info("[RAG] query rewrite: llm=true mergeLen={}", expandedQuery.length());
            } else {
                expandedQuery = ruleExpandedQuery;
                log.info("[RAG] query rewrite: llm=false ruleLen={}", expandedQuery.length());
            }

            float[] questionVector = new float[0];
            if (embeddingUtil != null) {
                try {
                    questionVector = embeddingUtil.embed(expandedQuery);
                } catch (Exception e) {
                    log.error("[RAG] embed failed: {}", e.getMessage());
                }
            }
            tEmbed = System.currentTimeMillis();

            // Step 3.5: HyDE 假设文档向量（仅当启用时，用于知识库检索）
            float[] knowledgeVector = questionVector;
            if (ragConfig.isHydeEnabled() && embeddingUtil != null) {
                String hydeDoc = queryEnhancer.generateHydeDocument(processedQuestion);
                if (hydeDoc != null && !hydeDoc.isEmpty()) {
                    try {
                        knowledgeVector = embeddingUtil.embed(hydeDoc);
                        tHyde = System.currentTimeMillis();
                        log.info("[RAG] HyDE vector generated: dim={}", knowledgeVector.length);
                    } catch (Exception e) {
                        log.warn("[RAG] HyDE embed failed, fallback to query vector: {}", e.getMessage());
                    }
                }
            }
            if (tHyde == 0) {
                tHyde = tEmbed;
            }

            // Step 5: 混合搜索 + MMR + 阈值过滤（使用 HyDE 向量或查询向量）
            relatedKnowledge = retrievalService.searchLawKnowledgeFiltered(knowledgeVector, expandedQuery, entityLawType, adjustedTopK);
            tKnow = System.currentTimeMillis();
            knowledgeMatch = JsonUtil.buildKnowledgeMatchJson(relatedKnowledge);
            String historyContext = promptBuilder.buildConversationHistory(conversationId);

            // Step 6: LLM 生成
            int tokenInput = 0;
            int tokenOutput = 0;
            if (!relatedKnowledge.isEmpty()) {
                GenerationResult genResult = generateAnswerWithTokens(question, relatedKnowledge, historyContext);
                answer = genResult.answer();
                tokenInput = genResult.inputTokens();
                tokenOutput = genResult.outputTokens();
                source = "law_knowledge";
            } else {
                GenerationResult genResult = generateDirectAnswerWithTokens(question, historyContext);
                answer = genResult.answer();
                tokenInput = genResult.inputTokens();
                tokenOutput = genResult.outputTokens();
                source = "llm_direct";
            }
            tGen = System.currentTimeMillis();
            if (!relatedKnowledge.isEmpty() && !citationVerifier.verifyCitations(answer, relatedKnowledge)) {
                answer += UNVERIFIED_CITATION_WARNING;
            }
            answer = promptBuilder.appendComplianceDisclaimer(answer, source);

            response.setAnswer(answer);
            response.setRelatedKnowledge((List<Object>) (List<?>) relatedKnowledge);
            response.setConversationId(conversationId);

            // Step 7: 异步后续处理
            ragPersistenceService.asyncLogVisit(userId, question, answer, knowledgeMatch, source, conversationId, tokenInput, tokenOutput);

            // 结构化汇总日志
            double topScore = relatedKnowledge.isEmpty() ? 0.0 :
                    relatedKnowledge.stream().mapToDouble(k -> k.getScore() != null ? k.getScore() : 0.0).max().orElse(0.0);
            long totalMs = System.currentTimeMillis() - t0;
            ragMetricsService.recordRequest(source,
                    tPre - t0, tEmbed - tPre, tKnow - tEmbed, tGen - tKnow, totalMs,
                    relatedKnowledge.size(), topScore, ragConfig.isHydeEnabled(), null);
            log.info("[RAG-SUMMARY] source={} intent={} lawType={} retrieved={} topScore={} hyde={} preMs={} embedMs={} hydeMs={} knowMs={} genMs={} totalMs={}",
                    source, intent, entityLawType, relatedKnowledge.size(), String.format("%.4f", topScore),
                    ragConfig.isHydeEnabled() && knowledgeVector != questionVector ? "Y" : "N",
                    tPre - t0, tEmbed - tPre, tHyde - tEmbed, tKnow - tHyde, tGen - tKnow, System.currentTimeMillis() - t0);

        } catch (Exception e) {
            log.error("[RAG] process error: {}", e.getMessage(), e);
            answer = "抱歉，系统处理出现问题，请稍后再试。";
            response.setAnswer(answer);
            response.setRelatedKnowledge(new ArrayList<>());
            response.setConversationId(conversationId);
            ragPersistenceService.saveChatRecord(userId, question, answer, knowledgeMatch, source, conversationId, 0, 0);
        }

        return response;
    }

    @Override
    public List<LawKnowledge> searchLawKnowledge(float[] questionVector, String expandedQuery) {
        return retrievalService.searchLawKnowledgeFiltered(questionVector, expandedQuery, null, ragConfig.getSearchTopK());
    }

    @Override
    public String generateAnswer(String question, List<LawKnowledge> relatedKnowledge, String historyContext) {
        return generateAnswerWithTokens(question, relatedKnowledge, historyContext).answer();
    }

    private GenerationResult generateAnswerWithTokens(String question, List<LawKnowledge> relatedKnowledge, String historyContext) {
        if (!llmInvoker.isAvailable()) {
            log.warn("ChatLanguageModel未初始化，返回默认回答");
            return new GenerationResult("抱歉，AI服务暂时不可用，请稍后再试。", 0, 0);
        }

        try {
            String userPrompt = promptBuilder.buildKnowledgeUserPrompt(question, relatedKnowledge, historyContext);
            log.info("结合法律知识库调用大模型回答，历史上下文长度: {}", historyContext != null ? historyContext.length() : 0);

            LLMInvoker.LLMResult result = llmInvoker.invoke(
                    List.of(promptBuilder.buildSystemPrompt(), UserMessage.from(userPrompt)));
            String answer = result.answer();
            int inputTokens = result.inputTokens();
            int outputTokens = result.outputTokens();
            log.info("基于法律知识库生成回答成功: inputTokens={} outputTokens={}", inputTokens, outputTokens);
            return new GenerationResult(answer, inputTokens, outputTokens);

        } catch (Exception e) {
            log.error("生成回答失败: {}", e.getMessage(), e);
            return new GenerationResult(buildFallbackMessage(e), 0, 0);
        }
    }

    private String generateDirectAnswer(String question, String historyContext) {
        return generateDirectAnswerWithTokens(question, historyContext).answer();
    }

    private GenerationResult generateDirectAnswerWithTokens(String question, String historyContext) {
        if (!llmInvoker.isAvailable()) {
            log.warn("ChatLanguageModel未初始化，返回默认回答");
            return new GenerationResult("抱歉，AI服务暂时不可用，请稍后再试。", 0, 0);
        }

        try {
            String userPrompt = promptBuilder.buildDirectUserPrompt(question, historyContext);
            LLMInvoker.LLMResult result = llmInvoker.invoke(
                    List.of(promptBuilder.buildSystemPrompt(), UserMessage.from(userPrompt)));
            String answer = result.answer();
            int inputTokens = result.inputTokens();
            int outputTokens = result.outputTokens();
            log.info("大模型直接回答成功: inputTokens={} outputTokens={}", inputTokens, outputTokens);
            return new GenerationResult(answer, inputTokens, outputTokens);

        } catch (Exception e) {
            log.error("大模型直接回答失败: {}", e.getMessage(), e);
            return new GenerationResult(buildFallbackMessage(e), 0, 0);
        }
    }

    private String buildFallbackMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("InvalidApiKey") || msg.contains("Invalid API-key") || msg.contains("401"))) {
            return "AI服务未配置有效的API密钥，请联系管理员设置DASHSCOPE_API_KEY环境变量。";
        }
        return "抱歉，AI回答生成失败，请稍后再试。";
    }

    @Override
    public Long asyncLogVisit(Long userId, String question, String answer, String knowledgeMatch, String source, Long conversationId) {
        return ragPersistenceService.asyncLogVisit(userId, question, answer, knowledgeMatch, source, conversationId);
    }

    public Long asyncLogVisit(Long userId, String question, String answer, String knowledgeMatch, String source, Long conversationId, int tokenInput, int tokenOutput) {
        return ragPersistenceService.asyncLogVisit(userId, question, answer, knowledgeMatch, source, conversationId, tokenInput, tokenOutput);
    }

    @Override
    public void asyncUpdateKnowledgeToChatRecord(Long chatId, String knowledgeIds) {
        ragPersistenceService.asyncUpdateKnowledgeToChatRecord(chatId, knowledgeIds);
    }

    @Override
    @NoLog
    public void processQuestionStream(Long userId, String question, Long conversationId, SseEmitter emitter) {
        AtomicBoolean completed = new AtomicBoolean(false);
        long t0 = System.currentTimeMillis();
        question = sanitizeUserInput(question);
        log.info("[RAG-SSE] q=\"{}\" userId={}", question.length() > 60 ? question.substring(0, 60) + "..." : question, userId);
        if (com.lhs.lawmind.security.PiiUtil.hasPii(question)) {
            log.warn("[RAG-PII] SSE流式问题包含个人信息 userId={}", userId);
        }

        var filterResult = com.lhs.lawmind.security.SensitiveTopicFilter.filter(question);
        if (filterResult.blocked()) {
            log.warn("[RAG-GUARD] 敏感话题拦截: category={} userId={}", filterResult.category(), userId);
            try {
                emitter.send(SseEmitter.event().name("message").data(filterResult.reason()));
                emitter.complete();
            } catch (Exception ignored) {}
            ragMetricsService.recordRequest("guard_blocked", 0, 0, 0, 0, System.currentTimeMillis() - t0, 0, 0.0, false, null);
            return;
        }

        try {
            if (!LegalQuestionClassifier.isLegalRelatedQuestion(question)) {
                String rejectAnswer = "抱歉，我是一个法律咨询助手，只能回答与法律相关的问题。";
                Long chatId = ragPersistenceService.saveChatRecord(userId, question, rejectAnswer, "[]", "non_legal_reject", conversationId, 0, 0);
                SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + SseStreamHelper.escapeJson(rejectAnswer) + "\"}"));
                SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("done").data("{\"conversationId\":" + conversationId + ",\"chatId\":" + chatId + "}"));
                SseStreamHelper.safeComplete(emitter, completed);
                ragMetricsService.recordRequest("non_legal_reject", 0, 0, 0, 0, System.currentTimeMillis() - t0, 0, 0.0, false, null);
                return;
            }

            // Step 1: 预处理 + 意图 + 实体（轻量级，不含 LLM 调用）
            TextPreprocessUtil.PreprocessResult preprocessResult = TextPreprocessUtil.preprocessAndGenerateMD5(question);
            String processedQuestion = preprocessResult.getProcessedText();
            String ruleExpandedQuery = legalQueryExpander.expandQuery(processedQuestion);
            IntentType intent = intentClassifierEnhanced.classifyType(processedQuestion);
            LegalEntityExtractor.LegalEntities entities = legalEntityExtractor.extract(processedQuestion);
            String entityLawType = entities.getLawType();
            // Entity-aware expansion: inject extracted law type + article reference into query
            if (entityLawType != null && !entityLawType.isBlank()) {
                StringBuilder eb = new StringBuilder(ruleExpandedQuery);
                eb.append(" ").append(entityLawType);
                if (entities.getArticleReference() != null && !entities.getArticleReference().isBlank()) {
                    eb.append(" ").append(entities.getArticleReference());
                }
                ruleExpandedQuery = eb.toString();
            }
            int adjustedTopK = intentClassifierEnhanced.adjustTopK(intent, ragConfig.getSearchTopK());
            long tPre = System.currentTimeMillis();
            log.info("[RAG-TIME] 1-preprocess: {}ms | intent={} entity={}", tPre - t0, intent.name(), entityLawType);

            // Step 3: 查询扩展 + 向量化（LLM 改写基于规则扩展结果，合并而非替换）
            String llmRewrittenQuery = queryEnhancer.rewriteQueryWithLLM(ruleExpandedQuery);
            String expandedQuery;
            if (llmRewrittenQuery != null) {
                expandedQuery = queryEnhancer.mergeQueries(ruleExpandedQuery, llmRewrittenQuery);
            } else {
                expandedQuery = ruleExpandedQuery;
            }
            long tRewrite = System.currentTimeMillis();
            log.info("[RAG-TIME] 2-rewrite: {}ms | llm={} finalLen={}", tRewrite - tPre, llmRewrittenQuery != null, expandedQuery.length());

            float[] questionVector = new float[0];
            if (embeddingUtil != null) {
                try {
                    questionVector = embeddingUtil.embed(expandedQuery);
                } catch (Exception e) {
                    log.error("[RAG-SSE] embed failed: {}", e.getMessage());
                }
            }
            long tEmbed = System.currentTimeMillis();
            log.info("[RAG-TIME] 3-embed: {}ms", tEmbed - tRewrite);

            // Step 3.5: HyDE 假设文档向量（仅当启用时）
            float[] knowledgeVector = questionVector;
            if (ragConfig.isHydeEnabled() && embeddingUtil != null) {
                String hydeDoc = queryEnhancer.generateHydeDocument(processedQuestion);
                if (hydeDoc != null && !hydeDoc.isEmpty()) {
                    try {
                        knowledgeVector = embeddingUtil.embed(hydeDoc);
                        log.info("[RAG-SSE] HyDE vector generated: dim={}", knowledgeVector.length);
                    } catch (Exception e) {
                        log.warn("[RAG-SSE] HyDE embed failed, fallback to query vector: {}", e.getMessage());
                    }
                }
            }
            long tHyde = System.currentTimeMillis();
            if (ragConfig.isHydeEnabled()) {
                log.info("[RAG-TIME] 3.5-hyde: {}ms", tHyde - tEmbed);
            }

            // Step 5: 混合搜索 + MMR（使用 HyDE 向量或查询向量）
            List<LawKnowledge> relatedKnowledge = retrievalService.searchLawKnowledgeFiltered(knowledgeVector, expandedQuery, entityLawType, adjustedTopK);
            long tKnow = System.currentTimeMillis();
            log.info("[RAG-TIME] 5-knowledgeSearch: {}ms | results={}", tKnow - tHyde, relatedKnowledge.size());
            String knowledgeMatch = JsonUtil.buildKnowledgeMatchJson(relatedKnowledge);

            // 发送 knowledge 事件
            if (!relatedKnowledge.isEmpty()) {
                SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("knowledge").data("{\"relatedKnowledge\":" + knowledgeMatch + "}"));
            }

            // 构建多轮对话历史上下文
            String historyContext = promptBuilder.buildConversationHistory(conversationId);
            long tHistory = System.currentTimeMillis();
            log.info("[RAG-TIME] 5.1-historyContext: {}ms", tHistory - tKnow);

            // Step 6: 流式生成 AI 回答
            String source;
            String userPrompt;
            if (!relatedKnowledge.isEmpty()) {
                source = "law_knowledge";
                userPrompt = promptBuilder.buildKnowledgeUserPrompt(question, relatedKnowledge, historyContext);
            } else {
                source = "llm_direct";
                userPrompt = promptBuilder.buildDirectUserPrompt(question, historyContext);
            }

            // 检查 StreamingChatLanguageModel 是否可用
            if (streamingChatLanguageModel == null) {
                log.warn("[SSE] StreamingChatLanguageModel 未初始化，降级为同步回答");
                int fbTokenInput = 0, fbTokenOutput = 0;
                String fallbackAnswer;
                if (chatLanguageModel != null) {
                    LLMInvoker.LLMResult fbResult = llmInvoker.invoke(
                            List.of(promptBuilder.buildSystemPrompt(), UserMessage.from(userPrompt)));
                    fallbackAnswer = fbResult.answer();
                    fbTokenInput = fbResult.inputTokens();
                    fbTokenOutput = fbResult.outputTokens();
                } else {
                    fallbackAnswer = "抱歉，AI服务暂时不可用。";
                }
                SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + SseStreamHelper.escapeJson(fallbackAnswer) + "\"}"));
                Long chatId = ragPersistenceService.saveChatRecord(userId, question, fallbackAnswer, knowledgeMatch, source, conversationId, fbTokenInput, fbTokenOutput);
                SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("done").data("{\"conversationId\":" + conversationId + ",\"chatId\":" + chatId + "}"));
                SseStreamHelper.safeComplete(emitter, completed);
                long fbTotal = System.currentTimeMillis() - t0;
                double fbTopScore = relatedKnowledge.isEmpty() ? 0.0 :
                        relatedKnowledge.stream().mapToDouble(k -> k.getScore() != null ? k.getScore() : 0.0).max().orElse(0.0);
                ragMetricsService.recordRequest(source, tPre - t0, tEmbed - tPre, tKnow - tEmbed, fbTotal - tKnow, fbTotal,
                        relatedKnowledge.size(), fbTopScore, false, null);
                return;
            }

            // 使用 StreamingChatLanguageModel 流式生成
            StringBuilder answerBuilder = new StringBuilder();
            final java.util.concurrent.atomic.AtomicLong firstTokenAt = new java.util.concurrent.atomic.AtomicLong(0);
            final String finalQuestion = question;
            final String finalSource = source;
            final String finalKnowledgeMatch = knowledgeMatch;
            final List<LawKnowledge> finalRelatedKnowledge = relatedKnowledge;
            final long finalTPre = tPre;
            final long finalTRewrite = tRewrite;
            final long finalTEmbed = tEmbed;
            final long finalTHyde = tHyde;
            final long finalTKnow = tKnow;
            final long finalTHistory = tHistory;
            final IntentType finalIntent = intent;
            final String finalEntityLawType = entityLawType;

            llmInvoker.invokeStreaming(List.of(promptBuilder.buildSystemPrompt(), UserMessage.from(userPrompt)), new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    if (firstTokenAt.compareAndSet(0, System.currentTimeMillis())) {
                        long ttft = firstTokenAt.get() - t0;
                        log.info("[RAG-TIME] 6-firstToken: {}ms (total from start)", ttft);
                    }
                    answerBuilder.append(token);
                    SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + SseStreamHelper.escapeJson(token) + "\"}"));
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    String fullAnswer = answerBuilder.toString();
                    if (!finalRelatedKnowledge.isEmpty() && !citationVerifier.verifyCitations(fullAnswer, finalRelatedKnowledge)) {
                        fullAnswer += UNVERIFIED_CITATION_WARNING;
                    }
                    String disclaimer = promptBuilder.buildComplianceDisclaimer();
                    fullAnswer = fullAnswer + disclaimer;
                    long tGen = System.currentTimeMillis();
                    int sseTokenInput = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
                    int sseTokenOutput = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;

                    SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + SseStreamHelper.escapeJson(disclaimer) + "\"}"));
                    Long chatId = ragPersistenceService.saveChatRecord(userId, finalQuestion, fullAnswer, finalKnowledgeMatch, finalSource, conversationId, sseTokenInput, sseTokenOutput);
                    SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("done").data("{\"conversationId\":" + conversationId + ",\"chatId\":" + chatId + "}"));
                    SseStreamHelper.safeComplete(emitter, completed);

                    double topScore = finalRelatedKnowledge.isEmpty() ? 0.0 :
                            finalRelatedKnowledge.stream().mapToDouble(k -> k.getScore() != null ? k.getScore() : 0.0).max().orElse(0.0);
                    long totalMs = tGen - t0;
                    long preMs = finalTPre - t0;
                    long rewriteMs = finalTRewrite - finalTPre;
                    long embedMs = finalTEmbed - finalTRewrite;
                    long hydeMs = finalTHyde - finalTEmbed;
                    long searchMs = finalTKnow - finalTHyde;
                    long historyMs = finalTHistory - finalTKnow;
                    long genMs = tGen - finalTHistory;
                    long ttftMs = firstTokenAt.get() > 0 ? firstTokenAt.get() - t0 : 0;
                    ragMetricsService.recordRequest(finalSource, preMs, embedMs, searchMs, genMs, totalMs,
                            finalRelatedKnowledge.size(), topScore, ragConfig.isHydeEnabled(), null);
                    log.info("[RAG-TIME-SUMMARY] pre={}ms rewrite={}ms embed={}ms hyde={}ms search={}ms history={}ms ttft={}ms gen={}ms => total={}ms | source={} intent={} lawType={} retrieved={} topScore={} answerLen={}",
                            preMs, rewriteMs, embedMs, hydeMs, searchMs, historyMs, ttftMs, genMs, totalMs,
                            finalSource, finalIntent.name(), finalEntityLawType,
                            finalRelatedKnowledge.size(), String.format("%.4f", topScore), fullAnswer.length());
                }

                @Override
                public void onError(Throwable error) {
                    log.error("[RAG-SSE] stream gen error: {}", error.getMessage());
                    String errMsg = error.getMessage();
                    String fallback;
                    if (errMsg != null && (errMsg.contains("InvalidApiKey") || errMsg.contains("Invalid API-key") || errMsg.contains("401"))) {
                        fallback = "AI服务未配置有效的API密钥，请联系管理员设置DASHSCOPE_API_KEY环境变量。";
                    } else {
                        fallback = !answerBuilder.isEmpty() ? answerBuilder.toString() : "AI回答生成失败，请稍后再试";
                    }
                    ragPersistenceService.saveChatRecord(userId, finalQuestion, fallback, finalKnowledgeMatch, finalSource, conversationId, 0, 0);
                    SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("error").data("{\"message\":\"" + SseStreamHelper.escapeJson(fallback) + "\"}"));
                    SseStreamHelper.safeComplete(emitter, completed);
                    ragMetricsService.recordRequest(finalSource + "_error", tPre - t0, finalTEmbed - tPre, finalTKnow - finalTEmbed, 0,
                            System.currentTimeMillis() - t0, 0, 0.0, false, null);
                }
            });

        } catch (Exception e) {
            log.error("[RAG-SSE] process error: {}", e.getMessage());
            SseStreamHelper.safeSend(emitter, completed, SseEmitter.event().name("error").data("{\"message\":\"系统处理出现问题，请稍后再试\"}"));
            SseStreamHelper.safeComplete(emitter, completed);
            ragMetricsService.recordRequest("process_error", 0, 0, 0, 0, System.currentTimeMillis() - t0, 0, 0.0, false, null);
        }
    }

    private static final String UNVERIFIED_CITATION_WARNING =
            "\n\n---\n> ⚠️ 以上回答中部分法律引用未能与知识库检索结果完全匹配，可能包含不准确的信息，请核实后参考。";

    private record GenerationResult(String answer, int inputTokens, int outputTokens) {}

    /**
     * 清洗用户输入，防止提示词注入攻击。
     */
    static String sanitizeUserInput(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String sanitized = input;

        sanitized = sanitized.replaceAll("```[\\s\\S]*?```", "[blocked]");

        String[] injectionPatterns = {
            "(?i)ignore\\s+(all\\s+)?(previous|above|prior)\\s+instructions?",
            "(?i)forget\\s+(all\\s+)?(previous|above|prior)\\s+instructions?",
            "(?i)you\\s+are\\s+now\\s+(a\\s+)?(dan|gpt|claude|chatgpt|assistant|bot|llm|language\\s+model|ai|artificial)\\b",
            "(?i)system\\s*:\\s*",
            "(?i)override\\s+(all\\s+)?system\\s+(prompt|instructions?)",
            "(?i)disregard\\s+(all\\s+)?(previous|above|prior)\\s+instructions?",
        };
        for (String pattern : injectionPatterns) {
            sanitized = sanitized.replaceAll(pattern, "[filtered]");
        }

        if (sanitized.length() > 2000) {
            sanitized = sanitized.substring(0, 2000);
        }

        return sanitized.trim();
    }
}
