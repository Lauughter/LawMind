package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.aop.annotation.NoLog;
import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.entity.SimilarQuestion;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.service.AutoLearnService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.service.RagService;
import com.lhs.lawmind.service.RagMetricsService;
import com.lhs.lawmind.service.SimilarQuestionService;
import com.lhs.lawmind.service.HybridSearchService;
import com.lhs.lawmind.service.RerankService;
import com.lhs.lawmind.service.SysConfigService;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.HotCacheUtil;
import com.lhs.lawmind.utils.IntentClassifier;
import com.lhs.lawmind.utils.LegalEntityExtractor;
import com.lhs.lawmind.utils.LegalQueryExpander;
import com.lhs.lawmind.utils.SearchResultDiversifier;
import com.lhs.lawmind.utils.SimilarQuestionRedisUtil;
import com.lhs.lawmind.utils.LawKnowledgeRedisUtil;
import com.lhs.lawmind.utils.VisitStatsUtil;
import com.lhs.lawmind.utils.JsonUtil;
import com.lhs.lawmind.utils.TextPreprocessUtil;
import com.lhs.lawmind.utils.RedisVectorUtil;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.dashscope.QwenChatModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * RAG知识库检索服务实现类
 * 实现完整的RAG流程：热点缓存 -> 相似问题 -> 法律知识库 -> 大模型兜底
 * 包含的方法有：
 * 1. 判断问题是否与法律相关
 * 2. 从缓存中获取热点问题
 * 3. 从Redis中获取相似问题
 * 4. 从Redis中获取法律知识
 * 5. 调用大模型生成回答
 */
@Slf4j
@Service
public class RagServiceImpl implements RagService {

    private final ChatLanguageModel chatLanguageModel;
    private final StreamingChatLanguageModel streamingChatLanguageModel;
    private final EmbeddingUtil embeddingUtil;
    private final HotCacheUtil hotCacheUtil;
    private final SimilarQuestionRedisUtil similarQuestionRedisUtil;
    private final LawKnowledgeRedisUtil lawKnowledgeRedisUtil;
    private final VisitStatsUtil visitStatsUtil;
    private final RagConfig ragConfig;
    private final LawKnowledgeService lawKnowledgeService;
    private final AiChatService aiChatService;
    private final com.lhs.lawmind.mapper.AiChatMapper aiChatMapper;
    private final SimilarQuestionService similarQuestionService;
    private final AutoLearnService autoLearnService;
    private final HybridSearchService hybridSearchService;
    private final RerankService rerankService;
    private final LegalQueryExpander legalQueryExpander;
    private final SearchResultDiversifier searchResultDiversifier;
    private final IntentClassifier intentClassifier;
    private final LegalEntityExtractor legalEntityExtractor;
    private final SysConfigService sysConfigService;
    private final RagPersistenceService ragPersistenceService;
    private final RagMetricsService ragMetricsService;

    @Value("${langchain4j.dashscope.chat-model.api-key}")
    private String dashscopeApiKey;

    /** Lazy-initialized qwen-turbo rewrite model (避免 Spring Bean 冲突) */
    private volatile ChatLanguageModel rewriteTurboModel;

    private volatile String cachedSystemPrompt;
    private volatile String cachedRewritePrompt;
    private volatile String cachedHydePrompt;

    /** LLM 查询改写 LRU 缓存（500条上限，access-order淘汰） */
    private final Map<String, String> rewriteCache = Collections.synchronizedMap(
            new LinkedHashMap<>(100, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > 500;
                }
            });

    public RagServiceImpl(
            Optional<ChatLanguageModel> chatLanguageModel,
            Optional<StreamingChatLanguageModel> streamingChatLanguageModel,
            Optional<EmbeddingUtil> embeddingUtil,
            HotCacheUtil hotCacheUtil,
            SimilarQuestionRedisUtil similarQuestionRedisUtil,
            LawKnowledgeRedisUtil lawKnowledgeRedisUtil,
            VisitStatsUtil visitStatsUtil,
            RagConfig ragConfig,
            LawKnowledgeService lawKnowledgeService,
            @Lazy AiChatService aiChatService,
            com.lhs.lawmind.mapper.AiChatMapper aiChatMapper,
            SimilarQuestionService similarQuestionService,
            AutoLearnService autoLearnService,
            HybridSearchService hybridSearchService,
            RerankService rerankService,
            LegalQueryExpander legalQueryExpander,
            SearchResultDiversifier searchResultDiversifier,
            IntentClassifier intentClassifier,
            LegalEntityExtractor legalEntityExtractor,
            SysConfigService sysConfigService,
            RagPersistenceService ragPersistenceService,
            RagMetricsService ragMetricsService) {
        this.chatLanguageModel = chatLanguageModel.orElse(null);
        this.streamingChatLanguageModel = streamingChatLanguageModel.orElse(null);
        this.embeddingUtil = embeddingUtil.orElse(null);
        this.hotCacheUtil = hotCacheUtil;
        this.similarQuestionRedisUtil = similarQuestionRedisUtil;
        this.lawKnowledgeRedisUtil = lawKnowledgeRedisUtil;
        this.visitStatsUtil = visitStatsUtil;
        this.ragConfig = ragConfig;
        this.lawKnowledgeService = lawKnowledgeService;
        this.aiChatService = aiChatService;
        this.aiChatMapper = aiChatMapper;
        this.similarQuestionService = similarQuestionService;
        this.autoLearnService = autoLearnService;
        this.hybridSearchService = hybridSearchService;
        this.rerankService = rerankService;
        this.legalQueryExpander = legalQueryExpander;
        this.searchResultDiversifier = searchResultDiversifier;
        this.intentClassifier = intentClassifier;
        this.legalEntityExtractor = legalEntityExtractor;
        this.sysConfigService = sysConfigService;
        this.ragPersistenceService = ragPersistenceService;
        this.ragMetricsService = ragMetricsService;
    }

    // 问题类型关键词映射
    private static final String[][] QUESTION_TYPES = {
        {"wage", "试用期", "工资", "薪资", "薪酬", "加班费", "工资条", "工资发放"},
        {"work_injury", "工伤", "受伤", "事故", "伤残", "工伤认定", "工伤保险"},
        {"social_security", "社保", "社会保险", "五险一金", "社保缴纳", "医保"},
        {"other", "其他", "法律", "咨询"}
    };

    /**
     * 判断问题是否与法律相关
     * 
     * @param question 用户问题
     * @return 是否与法律相关
     */
    private boolean isLegalRelatedQuestion(String question) {
        String[] legalKeywords = {
            // 核心法律术语
            "法律", "法规", "法条", "法院", "诉讼", "起诉", "辩护",
            "合同", "劳动合同", "工资", "加班", "工伤", "社保",
            "赔偿", "侵权", "纠纷", "权利", "义务", "法律责任",
            "违法", "合法", "维权", "责任",
            "仲裁", "调解", "判决", "裁定", "执行",
            "婚姻", "离婚", "出轨", "重婚", "继承", "财产", "债务", "房产", "知识产权",
            "交通", "医疗", "消费", "劳动", "就业", "失业",
            "个人信息", "身份证", "隐私", "信息泄露", "个人数据", "数据保护",
            // 刑事相关
            "诈骗", "假货", "索赔", "举报", "投诉", "拘留",
            "逮捕", "判刑", "罪名", "犯罪", "刑事", "民事", "行政",
            "罚款", "搜查", "扣押", "证据",
            "报警", "报案", "立案", "侦查", "公诉",
            "认罪", "自首", "缓刑", "假释", "减刑",
            "正当防卫", "紧急避险", "防卫过当",
            "打人", "伤人", "行凶", "轻伤", "重伤", "伤情",
            // 劳动相关
            "欠薪", "拖欠", "辞退", "裁员", "试用期",
            "工伤认定", "伤残", "职业病", "安全事故",
            "劳动仲裁", "劳动监察",
            // 消费相关
            "退一赔三", "消费者", "商家", "消费纠纷",
            "食品安全", "食物中毒", "退货", "退款",
            "网购", "下单", "购买", "买到", "假一赔三",
            "三包", "质量", "商品", "买到假",
            // 知识产权
            "商标", "专利", "著作权", "版权", "商业秘密",
            // 婚姻家庭
            "抚养权", "赡养", "遗产", "赠与", "买卖",
            // 房产租赁
            "租赁", "借贷", "担保", "抵押", "质押",
            "租房", "房东", "房租", "押金", "漏水",
            // 行政
            "行政复议", "行政诉讼", "国家赔偿",
            // 名誉/人身
            "人身伤害", "名誉权", "肖像权", "名誉",
            // 环境/土地
            "环保", "环境污染", "土地", "拆迁", "征收",
            // 公司/商业
            "公司", "股东", "股权", "法人", "破产",
            "合伙", "入股", "分红", "卷钱",
            // 日常生活法律
            "被打", "被撞", "被偷", "被抢", "被骗",
            "被开除", "被辞", "被裁",
            "打架", "斗殴", "受伤",
            "肇事", "逃逸", "碰瓷",
            "虚假宣传", "欺诈",
            "高利贷", "利滚利",
            "骚扰", "威胁", "恐吓",
            "吸毒", "贩毒", "赌博",
            "泄露", "倒卖",
        };
        for (String keyword : legalKeywords) {
            if (question.contains(keyword)) {
                return true;
            }
        }

        // 进一步通过问题模式判断，覆盖一些可能未包含核心关键词但仍与法律相关的问题
        String[] legalQuestionPatterns = {
            "是否合法", "是否违法", "法律规定", "法律责任", "如何处理",
            "怎么维权", "赔偿标准", "法律依据", "诉讼流程", "法律后果",
            "应该怎么办", "如何起诉", "需要什么证据", "能得到什么赔偿",
            "怎么离婚", "如何离婚", "离婚流程", "离婚手续", "离婚财产分割",
            "可以要求", "怎么索", "怎么投", "怎么举", "怎么退", "会不会判",
            "构成什么", "怎么算", "怎么认定", "如何认定",
            "能不能", "有没有责任", "谁负责", "谁来赔",
            "有没有赔偿", "赔偿多少", "赔多少",
            "怎么办", "怎么处理", "怎么解决", "如何维权",
            "报警有用吗", "能报警吗", "可以报警吗",
            "算不算", "是不是", "犯法吗", "违法吗", "合法吗",
            "能不能告", "可以告吗", "怎么告", "去哪告",
            "能判多久", "会判多久", "判几年", "能判几年",
            "能赔多少", "可以赔多少", "赔多少钱",
            "能起诉吗", "可以起诉吗", "值得起诉吗",
            "不交", "不给", "不管", "不承认", "不履行",
            "食物中毒", "中毒了",
            "卷钱跑路", "卷款跑路",
        };
        for (String pattern : legalQuestionPatterns) {
            if (question.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 处理用户问题的完整RAG流程
     *
     * @param userId         用户ID
     * @param question       用户问题
     * @param conversationId 会话ID（可为null）
     * @return
     */
    @Override
    @NoLog
    public AIChatResponse processQuestion(Long userId, String question, Long conversationId) {
        long t0 = System.currentTimeMillis();
        long tPre, tCache = 0, tEmbed = 0, tHyde = 0, tSim = 0, tKnow = 0, tGen = 0;
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
        if (!isLegalRelatedQuestion(question)) {
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
        IntentClassifier.Intent intent = null;
        String entityLawType = null;

        try {
            // Step 1: 文本预处理 + 意图分类 + 实体抽取（轻量级，不含 LLM 调用）
            TextPreprocessUtil.PreprocessResult preprocessResult = TextPreprocessUtil.preprocessAndGenerateMD5(question);
            String processedQuestion = preprocessResult.getProcessedText();
            String md5 = preprocessResult.getMd5();
            String ruleExpandedQuery = legalQueryExpander.expandQuery(processedQuestion);
            intent = intentClassifier.classify(processedQuestion);
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
            int adjustedTopK = intentClassifier.adjustTopK(intent, ragConfig.getSearchTopK());
            tPre = System.currentTimeMillis();
            log.info("[RAG] preprocess md5={} intent={} lawType={} topK={} expandLen={} preMs={}",
                    md5, intent, entityLawType, adjustedTopK,
                    ruleExpandedQuery.length() - processedQuestion.length(), tPre - t0);

            // Step 2: 热点缓存（在 LLM 改写前检查，避免浪费 API 调用）
            String hotCacheAnswer = queryHotCache(md5);
            tCache = System.currentTimeMillis();
            if (hotCacheAnswer != null) {
                answer = hotCacheAnswer;
                source = "hot_cache";
                response.setAnswer(answer);
                response.setRelatedKnowledge(new ArrayList<>());
                response.setConversationId(conversationId);
                ragPersistenceService.asyncLogVisit(userId, question, answer, knowledgeMatch, source, conversationId);
                ragPersistenceService.asyncUpdateSimilarQuestion(question, answer, "", new float[0]);
                long hotTotal = System.currentTimeMillis() - t0;
                ragMetricsService.recordRequest("hot_cache", tPre - t0, 0, 0, 0, hotTotal, 0, 0.0, false, null);
                log.info("[RAG-SUMMARY] source=hot_cache preMs={} cacheMs={} totalMs={}",
                        tPre - t0, tCache - tPre, hotTotal);
                return response;
            }

            // Step 3: 查询扩展 + 向量化（LLM 改写基于规则扩展结果，合并而非替换）
            String llmRewrittenQuery = rewriteQueryWithLLM(ruleExpandedQuery);
            String expandedQuery;
            if (llmRewrittenQuery != null) {
                expandedQuery = mergeQueries(ruleExpandedQuery, llmRewrittenQuery);
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
                String hydeDoc = generateHydeDocument(processedQuestion);
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

            // Step 4: 相似问题搜索（始终使用查询向量，问题-问题匹配）
            SimilarQuestion similarQuestion = searchSimilarQuestion(question, questionVector);
            tSim = System.currentTimeMillis();
            if (similarQuestion != null) {
                answer = similarQuestion.getAnswer();
                source = "similar_question";
                String knowledgeIds = similarQuestion.getKnowledgeIds();
                response.setAnswer(answer);
                response.setRelatedKnowledge(new ArrayList<>());
                response.setConversationId(conversationId);
                ragPersistenceService.asyncInsertQuestionAndKnowledge(userId, question, answer, knowledgeIds, conversationId);
                checkAndUpgradeHotCache(md5, answer);
                ragPersistenceService.asyncUpdateSimilarQuestion(question, answer, knowledgeIds, questionVector);
                long simTotal = System.currentTimeMillis() - t0;
                ragMetricsService.recordRequest("similar_question", tPre - t0, tEmbed - tCache, 0, 0, simTotal, 0, 0.0, false, null);
                log.info("[RAG-SUMMARY] source=similar_question preMs={} embedMs={} simMs={} totalMs={}",
                        tPre - t0, tEmbed - tCache, tSim - tEmbed, simTotal);
                return response;
            }

            // Step 5: 混合搜索 + MMR + 阈值过滤（使用 HyDE 向量或查询向量）
            relatedKnowledge = searchLawKnowledgeFiltered(knowledgeVector, expandedQuery, entityLawType, adjustedTopK);
            tKnow = System.currentTimeMillis();
            knowledgeMatch = buildKnowledgeMatchJson(relatedKnowledge);
            String historyContext = buildConversationHistory(conversationId);

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
            if (!relatedKnowledge.isEmpty() && !verifyCitations(answer, relatedKnowledge)) {
                answer += UNVERIFIED_CITATION_WARNING;
            }
            answer = appendComplianceDisclaimer(answer, source);

            response.setAnswer(answer);
            response.setRelatedKnowledge((List<Object>) (List<?>) relatedKnowledge);
            response.setConversationId(conversationId);

            // Step 7: 异步后续处理
            ragPersistenceService.asyncLogVisit(userId, question, answer, knowledgeMatch, source, conversationId, tokenInput, tokenOutput);
            checkAndUpgradeHotCache(md5, answer);
            String knowledgeIds = extractKnowledgeIds(relatedKnowledge);
            ragPersistenceService.asyncUpdateSimilarQuestion(question, answer, knowledgeIds, questionVector);

            // 结构化汇总日志
            double topScore = relatedKnowledge.isEmpty() ? 0.0 :
                    relatedKnowledge.stream().mapToDouble(k -> k.getScore() != null ? k.getScore() : 0.0).max().orElse(0.0);
            long totalMs = System.currentTimeMillis() - t0;
            ragMetricsService.recordRequest(source,
                    tPre - t0, tEmbed - tCache, tKnow - tSim, tGen - tKnow, totalMs,
                    relatedKnowledge.size(), topScore, ragConfig.isHydeEnabled(), null);
            log.info("[RAG-SUMMARY] source={} intent={} lawType={} retrieved={} topScore={} hyde={} preMs={} embedMs={} hydeMs={} simMs={} knowMs={} genMs={} totalMs={}",
                    source, intent, entityLawType, relatedKnowledge.size(), String.format("%.4f", topScore),
                    ragConfig.isHydeEnabled() && knowledgeVector != questionVector ? "Y" : "N",
                    tPre - t0, tEmbed - tCache, tHyde - tEmbed, tSim - tHyde, tKnow - tSim, tGen - tKnow, System.currentTimeMillis() - t0);

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
    public String queryHotCache(String md5) {
        return hotCacheUtil.getHotQuestionCache(md5);
    }

    /**
     * 搜索相似问题
     * @param originalQuestion 原始问题
     * @param questionVector 问题向量
     * @return
     */
    @Override
    public SimilarQuestion searchSimilarQuestion(String originalQuestion, float[] questionVector) {
        if (questionVector == null || questionVector.length == 0) {
            log.warn("问题向量为空，无法搜索相似问题");
            return null;
        }

        try {
            List<RedisVectorUtil.SearchResult> results = similarQuestionRedisUtil.searchSimilarQuestions(questionVector, ragConfig.getSearchTopK());

            if (results.isEmpty()) {
                log.info("未找到相似问题");
                return null;
            }

            // 找到相似度最高且类型匹配的结果
            RedisVectorUtil.SearchResult bestResult = null;
            double highestSimilarity = -1.0;
            List<RedisVectorUtil.SearchResult> candidates = new ArrayList<>();

            String queryType = classifyQuestionType(originalQuestion);
            
            for (RedisVectorUtil.SearchResult result : results) {
                double similarity = result.getScore();
                String key = result.getKey();
                String idStr = key.replace(ragConfig.getSimilarQuestionKeyPrefix(), "");
                try {
                    Long id = Long.parseLong(idStr);
                    SimilarQuestion similarQuestion = similarQuestionRedisUtil.getSimilarQuestion(id);
                    String candidateType = similarQuestion != null ? classifyQuestionType(similarQuestion.getQuestion()) : null;

                    if (similarity >= ragConfig.getSimilarQuestionThreshold()) {
                        if (similarQuestion != null && isSameType(originalQuestion, similarQuestion.getQuestion())) {
                            if (similarity > highestSimilarity) {
                                highestSimilarity = similarity;
                                bestResult = result;
                                candidates.clear();
                                candidates.add(result);
                            } else if (similarity == highestSimilarity) {
                                candidates.add(result);
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("Invalid similar-question id: {}", idStr);
                }
            }

            if (bestResult == null) {
                return null;
            }

            if (candidates.size() > 1) {
                int randomIndex = (int) (Math.random() * candidates.size());
                bestResult = candidates.get(randomIndex);
            }

            String key = bestResult.getKey();
            String idStr = key.replace(ragConfig.getSimilarQuestionKeyPrefix(), "");
            Long id = Long.parseLong(idStr);
            SimilarQuestion question = similarQuestionRedisUtil.getSimilarQuestion(id);
            if (question != null) {
                log.info("[RAG] similarQuestion hit: id={} score={}", id, String.format("%.4f", bestResult.getScore()));
                return question;
            }
            return null;

        } catch (Exception e) {
            log.error("搜索相似问题失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public List<LawKnowledge> searchLawKnowledge(float[] questionVector, String expandedQuery) {
        return searchLawKnowledgeFiltered(questionVector, expandedQuery, null, ragConfig.getSearchTopK());
    }

    private List<LawKnowledge> searchLawKnowledgeFiltered(float[] questionVector, String expandedQuery, String lawType, int topK) {
        List<LawKnowledge> rawResults;

        if (ragConfig.isHybridSearchEnabled()) {
            rawResults = hybridSearchService.searchHybridFiltered(questionVector, expandedQuery, topK, lawType);
        } else {
            rawResults = pureVectorSearch(questionVector, topK);
        }

        if (ragConfig.isRerankEnabled() && rawResults.size() > 1) {
            long tRerank = System.currentTimeMillis();
            rawResults = rerankService.rerank(
                    expandedQuery, rawResults,
                    ragConfig.getRerankCandidateTopK(),
                    ragConfig.getRerankTopN());
            log.info("[RAG] Rerank精排完成: model={} outputSize={} elapsedMs={}",
                    ragConfig.getRerankModel(), rawResults.size(), System.currentTimeMillis() - tRerank);
        }

        if (ragConfig.isMmrEnabled() && rawResults.size() > 1) {
            rawResults = searchResultDiversifier.diversify(rawResults, expandedQuery, topK, ragConfig.getMmrLambda());
        }

        return filterByThreshold(rawResults);
    }

    private List<LawKnowledge> pureVectorSearch(float[] questionVector, int topK) {
        List<LawKnowledge> result = new ArrayList<>();
        if (questionVector == null || questionVector.length == 0) {
            return result;
        }
        try {
            List<RedisVectorUtil.SearchResult> results = lawKnowledgeRedisUtil.searchLawKnowledge(questionVector, topK);
            for (RedisVectorUtil.SearchResult sr : results) {
                loadKnowledgeToResult(sr, sr.getScore(), result);
            }
        } catch (Exception e) {
            log.error("[RAG] vector search error: {}", e.getMessage());
        }
        return result;
    }

    /**
     * 双阈值过滤：高质量（>= lawKnowledge）直接采用，边缘（>= filter 且 < lawKnowledge）作为备选兜底
     * @param candidates 原始搜索结果列表（需已设置score）
     * @return 过滤后的结果列表
     */
    private List<LawKnowledge> filterByThreshold(List<LawKnowledge> candidates) {
        List<LawKnowledge> result = new ArrayList<>();
        // 边缘结果列表，满足 filterThreshold 但未达到 lawKnowledgeThreshold 的知识点
        List<LawKnowledge> borderline = new ArrayList<>();
        double filterThreshold = ragConfig.getFilterThreshold();
        double lawKnowledgeThreshold = ragConfig.getLawKnowledgeThreshold();

        for (LawKnowledge knowledge : candidates) {
            double score = knowledge.getScore() != null ? knowledge.getScore() : 0.0;
            if (score < filterThreshold) {
                log.debug("过滤低分结果: id={}, score={} < {}", knowledge.getId(), String.format("%.4f", score), filterThreshold);
                continue;
            }
            if (score >= lawKnowledgeThreshold) {
                result.add(knowledge);
                log.info("法律知识命中: id={}, title={}, score={}", knowledge.getId(), knowledge.getTitle(), String.format("%.4f", score));
            } else {
                borderline.add(knowledge);
                log.debug("法律知识备选: id={}, score={}", knowledge.getId(), String.format("%.4f", score));
            }
        }

        if (result.isEmpty() && !borderline.isEmpty()) {
            log.info("无高质量匹配结果，使用备选知识: 备选数量={}", borderline.size());
            result.addAll(borderline);
        }

        log.info("阈值过滤完成: 最终命中={}", result.size());
        return result;
    }

    @Override
    public String generateAnswer(String question, List<LawKnowledge> relatedKnowledge, String historyContext) {
        return generateAnswerWithTokens(question, relatedKnowledge, historyContext).answer();
    }

    private GenerationResult generateAnswerWithTokens(String question, List<LawKnowledge> relatedKnowledge, String historyContext) {
        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel未初始化，返回默认回答");
            return new GenerationResult("抱歉，AI服务暂时不可用，请稍后再试。", 0, 0);
        }

        try {
            String userPrompt = buildKnowledgeUserPrompt(question, relatedKnowledge, historyContext);
            log.info("结合法律知识库调用大模型回答，历史上下文长度: {}", historyContext != null ? historyContext.length() : 0);

            Response<AiMessage> response = chatLanguageModel.generate(buildSystemPrompt(), UserMessage.from(userPrompt));
            String answer = response.content().text();
            int inputTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
            int outputTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;
            log.info("基于法律知识库生成回答成功: inputTokens={} outputTokens={}", inputTokens, outputTokens);
            return new GenerationResult(answer, inputTokens, outputTokens);

        } catch (Exception e) {
            log.error("生成回答失败: {}", e.getMessage(), e);
            return new GenerationResult(buildFallbackMessage(e), 0, 0);
        }
    }

    /**
     * 从向量搜索结果中加载法律知识到结果列表
     * <p>先从Redis查询，未命中则回退到MySQL</p>
     * @param searchResult 向量搜索结果
     * @param similarity 搜索结果的相似度分数
     * @param result 结果列表，符合条件的法律知识将被添加到该列表中
     */
    private void loadKnowledgeToResult(RedisVectorUtil.SearchResult searchResult, double similarity, List<LawKnowledge> result) {
        String key = searchResult.getKey();
        String idStr = key.replace(ragConfig.getLawVectorKeyPrefix(), "");
        try {
            Long knowledgeId = Long.parseLong(idStr);
            LawKnowledgeRedisUtil.LawKnowledge redisKnowledge = lawKnowledgeRedisUtil.getLawKnowledge(knowledgeId);
            if (redisKnowledge != null) {
                LawKnowledge knowledge = new LawKnowledge();
                knowledge.setId(redisKnowledge.getId());
                knowledge.setTitle(redisKnowledge.getTitle());
                knowledge.setLawType(redisKnowledge.getLawType());
                knowledge.setContent(redisKnowledge.getContent());
                knowledge.setChapter(redisKnowledge.getChapter());
                knowledge.setSection(redisKnowledge.getSection());
                knowledge.setArticleNumber(redisKnowledge.getArticleNumber());
                knowledge.setScore(similarity);
                result.add(knowledge);
                log.info("法律知识命中: id={}, score={}", knowledgeId, String.format("%.10f", similarity));
            } else {
                LawKnowledge knowledge = lawKnowledgeService.getById(knowledgeId);
                if (knowledge != null) {
                    knowledge.setScore(similarity);
                    result.add(knowledge);
                    log.info("法律知识命中(MySQL): id={}, score={}", knowledgeId, String.format("%.10f", similarity));
                }
            }
        } catch (NumberFormatException e) {
            log.warn("无效的知识ID格式: {}, 跳过该结果", idStr);
        }
    }

    private String generateDirectAnswer(String question, String historyContext) {
        return generateDirectAnswerWithTokens(question, historyContext).answer();
    }

    private GenerationResult generateDirectAnswerWithTokens(String question, String historyContext) {
        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel未初始化，返回默认回答");
            return new GenerationResult("抱歉，AI服务暂时不可用，请稍后再试。", 0, 0);
        }

        try {
            String userPrompt = buildDirectUserPrompt(question, historyContext);
            Response<AiMessage> response = chatLanguageModel.generate(buildSystemPrompt(), UserMessage.from(userPrompt));
            String answer = response.content().text();
            int inputTokens = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
            int outputTokens = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;
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
    public void asyncUpdateSimilarQuestion(String question, String answer, String knowledgeIds, float[] questionVector) {
        ragPersistenceService.asyncUpdateSimilarQuestion(question, answer, knowledgeIds, questionVector);
    }

    @Override
    public void asyncUpdateKnowledgeToChatRecord(Long chatId, String knowledgeIds) {
        ragPersistenceService.asyncUpdateKnowledgeToChatRecord(chatId, knowledgeIds);
    }

    @Override
    public void asyncInsertQuestionAndKnowledge(Long userId, String question, String answer, String knowledgeIds, Long conversationId) {
        ragPersistenceService.asyncInsertQuestionAndKnowledge(userId, question, answer, knowledgeIds, conversationId);
    }

    /**
     * 检查并升级热点缓存
     * 
     * @param md5 问题的MD5值
     * @param answer AI回答
     * 
     */
    @Override
    public void checkAndUpgradeHotCache(String md5, String answer) {
        try {
            VisitStatsUtil.VisitStats stats = visitStatsUtil.getVisitStats(md5);
            log.debug("访问统计: 5分钟={}, 1小时={}, 1天={}",
                    stats.getCount5Minutes(), stats.getCount1Hour(), stats.getCount1Day());

            if (visitStatsUtil.isHotQuestion(stats)) {
                hotCacheUtil.setHotQuestionCache(md5, answer);
                log.info("问题达到热点阈值，已存入热点缓存: {}", md5);
            }
        } catch (Exception e) {
            log.error("检查热点缓存失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 构建多轮对话历史上下文
     * 从数据库中查询最近的对话记录，拼接为上下文字符串供大模型参考
     *
     * @param conversationId 会话ID（为null时返回空字符串）
     * @return 对话历史上下文字符串
     */
    private String buildConversationHistory(Long conversationId) {
        if (conversationId == null) {
            log.debug("会话ID为空，跳过构建对话历史");
            return "";
        }

        try {
            int maxMessages = ragConfig.getMaxHistoryMessages();
            List<AiChat> historyMessages = aiChatMapper.selectRecentByConversationId(conversationId, maxMessages);

            if (historyMessages == null || historyMessages.isEmpty()) {
                log.debug("会话 {} 无历史消息", conversationId);
                return "";
            }

            StringBuilder history = new StringBuilder();
            for (AiChat chat : historyMessages) {
                history.append("用户: ").append(chat.getUserQuestion()).append("\n");
                // 截取回答前200个字符，避免上下文过长
                String aiAnswer = chat.getAiAnswer();
                if (aiAnswer != null && aiAnswer.length() > 500) {
                    aiAnswer = aiAnswer.substring(0, 500) + "...";
                }
                history.append("助手: ").append(aiAnswer).append("\n\n");
            }

            log.info("构建对话历史上下文完成，会话ID: {}, 历史消息数: {}, 上下文长度: {}",
                    conversationId, historyMessages.size(), history.length());
            return history.toString();

        } catch (Exception e) {
            log.error("构建对话历史上下文失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * 构建knowledge_match JSON
     */
    private String buildKnowledgeMatchJson(List<LawKnowledge> relatedKnowledge) {
        return JsonUtil.buildKnowledgeMatchJson(relatedKnowledge);
    }

    /**
     * 提取知识点ID列表
     */
    private String extractKnowledgeIds(List<LawKnowledge> relatedKnowledge) {
        if (relatedKnowledge == null || relatedKnowledge.isEmpty()) {
            return "";
        }

        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < relatedKnowledge.size(); i++) {
            ids.append(relatedKnowledge.get(i).getId());
            if (i < relatedKnowledge.size() - 1) {
                ids.append(",");
            }
        }
        return ids.toString();
    }

    /**
     * 分类问题类型
     * 根据关键词判断问题属于哪个类别
     *
     * @param question 问题文本
     * @return 问题类型
     */
    private String classifyQuestionType(String question) {
        for (String[] type : QUESTION_TYPES) {
            String typeName = type[0];
            for (int i = 1; i < type.length; i++) {
                if (question.contains(type[i])) {
                    return typeName;
                }
            }
        }
        return "other";
    }

    /**
     * 检查两个问题是否属于同一类型
     *
     * @param question1 第一个问题
     * @param question2 第二个问题
     * @return 是否属于同一类型
     */
    private boolean isSameType(String question1, String question2) {
        String type1 = classifyQuestionType(question1);
        String type2 = classifyQuestionType(question2);
        return type1.equals(type2);
    }

    // ==================== SSE 流式处理 ====================

    /**
     * 安全地向 SseEmitter 发送事件，防止 complete 后再 send
     */
    private void safeSend(SseEmitter emitter, AtomicBoolean completed, SseEmitter.SseEventBuilder event) {
        if (completed.get()) return;
        try {
            emitter.send(event);
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletResponse response = attrs.getResponse();
                if (response != null) {
                    response.flushBuffer();
                }
            }
        } catch (IOException e) {
            log.warn("SSE 发送失败（客户端可能已断开）: {}", e.getMessage());
            safeComplete(emitter, completed);
        }
    }

    /**
     * 安全地完成 SseEmitter
     */
    private void safeComplete(SseEmitter emitter, AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                log.debug("SseEmitter complete 异常（可忽略）: {}", e.getMessage());
            }
        }
    }

    /**
     * 安全地以错误完成 SseEmitter
     */
    private void safeCompleteWithError(SseEmitter emitter, AtomicBoolean completed, Throwable ex) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.completeWithError(ex);
            } catch (Exception e) {
                log.debug("SseEmitter completeWithError 异常（可忽略）: {}", e.getMessage());
            }
        }
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
            if (!isLegalRelatedQuestion(question)) {
                String rejectAnswer = "抱歉，我是一个法律咨询助手，只能回答与法律相关的问题。";
                Long chatId = ragPersistenceService.saveChatRecord(userId, question, rejectAnswer, "[]", "non_legal_reject", conversationId, 0, 0);
                safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + escapeJson(rejectAnswer) + "\"}"));
                safeSend(emitter, completed, SseEmitter.event().name("done").data("{\"conversationId\":" + conversationId + ",\"chatId\":" + chatId + "}"));
                safeComplete(emitter, completed);
                ragMetricsService.recordRequest("non_legal_reject", 0, 0, 0, 0, System.currentTimeMillis() - t0, 0, 0.0, false, null);
                return;
            }

            // Step 1: 预处理 + 意图 + 实体（轻量级，不含 LLM 调用）
            TextPreprocessUtil.PreprocessResult preprocessResult = TextPreprocessUtil.preprocessAndGenerateMD5(question);
            String processedQuestion = preprocessResult.getProcessedText();
            String md5 = preprocessResult.getMd5();
            String ruleExpandedQuery = legalQueryExpander.expandQuery(processedQuestion);
            IntentClassifier.Intent intent = intentClassifier.classify(processedQuestion);
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
            int adjustedTopK = intentClassifier.adjustTopK(intent, ragConfig.getSearchTopK());
            long tPre = System.currentTimeMillis();
            log.info("[RAG-TIME] 1-preprocess: {}ms | intent={} entity={}", tPre - t0, intent.name(), entityLawType);

            // Step 2: 热点缓存（在 LLM 改写前检查，避免浪费 API 调用）
            String hotCacheAnswer = queryHotCache(md5);
            if (hotCacheAnswer != null) {
                String answer = appendComplianceDisclaimer(hotCacheAnswer, "hot_cache");
                safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + escapeJson(answer) + "\"}"));
                Long chatId = ragPersistenceService.saveChatRecord(userId, question, answer, "[]", "hot_cache", conversationId, 0, 0);
                safeSend(emitter, completed, SseEmitter.event().name("done").data("{\"conversationId\":" + conversationId + ",\"chatId\":" + chatId + "}"));
                safeComplete(emitter, completed);
                ragPersistenceService.asyncUpdateSimilarQuestion(question, hotCacheAnswer, "", new float[0]);
                ragMetricsService.recordRequest("hot_cache", tPre - t0, 0, 0, 0, System.currentTimeMillis() - t0, 0, 0.0, false, null);
                return;
            }

            // Step 3: 查询扩展 + 向量化（LLM 改写基于规则扩展结果，合并而非替换）
            String llmRewrittenQuery = rewriteQueryWithLLM(ruleExpandedQuery);
            String expandedQuery;
            if (llmRewrittenQuery != null) {
                expandedQuery = mergeQueries(ruleExpandedQuery, llmRewrittenQuery);
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
                String hydeDoc = generateHydeDocument(processedQuestion);
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

            // Step 4: 相似问题（始终使用查询向量）
            SimilarQuestion similarQuestion = searchSimilarQuestion(question, questionVector);
            if (similarQuestion != null) {
                String answerRaw = similarQuestion.getAnswer();
                String answer = appendComplianceDisclaimer(answerRaw, "similar_question");
                String knowledgeIds = similarQuestion.getKnowledgeIds();
                safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + escapeJson(answer) + "\"}"));
                Long chatId = ragPersistenceService.saveChatRecord(userId, question, answer, "[]", "similar_question", conversationId, 0, 0);
                safeSend(emitter, completed, SseEmitter.event().name("done").data("{\"conversationId\":" + conversationId + ",\"chatId\":" + chatId + "}"));
                safeComplete(emitter, completed);
                ragPersistenceService.asyncInsertQuestionAndKnowledge(userId, question, answer, knowledgeIds, conversationId);
                checkAndUpgradeHotCache(md5, answer);
                ragPersistenceService.asyncUpdateSimilarQuestion(question, answer, knowledgeIds, questionVector);
                long simTotal = System.currentTimeMillis() - t0;
                ragMetricsService.recordRequest("similar_question", tPre - t0, tEmbed - tPre, 0, 0, simTotal, 0, 0.0, false, null);
                log.info("[RAG-SSE-SUMMARY] source=similar_question embedMs={} simMs={} totalMs={}",
                        tEmbed - t0, System.currentTimeMillis() - tEmbed, simTotal);
                return;
            }

            long tSimilar = System.currentTimeMillis();
            if (similarQuestion == null) {
                log.info("[RAG-TIME] 4-similarSearch: {}ms (miss)", tSimilar - tHyde);
            }

            // Step 5: 混合搜索 + MMR（使用 HyDE 向量或查询向量）
            List<LawKnowledge> relatedKnowledge = searchLawKnowledgeFiltered(knowledgeVector, expandedQuery, entityLawType, adjustedTopK);
            long tKnow = System.currentTimeMillis();
            log.info("[RAG-TIME] 5-knowledgeSearch: {}ms | results={}", tKnow - tSimilar, relatedKnowledge.size());
            String knowledgeMatch = buildKnowledgeMatchJson(relatedKnowledge);

            // 发送 knowledge 事件
            if (!relatedKnowledge.isEmpty()) {
                safeSend(emitter, completed, SseEmitter.event().name("knowledge").data("{\"relatedKnowledge\":" + knowledgeMatch + "}"));
            }

            // 构建多轮对话历史上下文
            String historyContext = buildConversationHistory(conversationId);
            long tHistory = System.currentTimeMillis();
            log.info("[RAG-TIME] 5.1-historyContext: {}ms", tHistory - tKnow);

            // Step 6: 流式生成 AI 回答
            String source;
            String userPrompt;
            if (!relatedKnowledge.isEmpty()) {
                source = "law_knowledge";
                userPrompt = buildKnowledgeUserPrompt(question, relatedKnowledge, historyContext);
            } else {
                source = "llm_direct";
                userPrompt = buildDirectUserPrompt(question, historyContext);
            }

            // 检查 StreamingChatLanguageModel 是否可用
            if (streamingChatLanguageModel == null) {
                log.warn("[SSE] StreamingChatLanguageModel 未初始化，降级为同步回答");
                int fbTokenInput = 0, fbTokenOutput = 0;
                String fallbackAnswer;
                if (chatLanguageModel != null) {
                    Response<AiMessage> fbResponse = chatLanguageModel.generate(buildSystemPrompt(), UserMessage.from(userPrompt));
                    fallbackAnswer = fbResponse.content().text();
                    fbTokenInput = fbResponse.tokenUsage() != null ? fbResponse.tokenUsage().inputTokenCount() : 0;
                    fbTokenOutput = fbResponse.tokenUsage() != null ? fbResponse.tokenUsage().outputTokenCount() : 0;
                } else {
                    fallbackAnswer = "抱歉，AI服务暂时不可用。";
                }
                safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + escapeJson(fallbackAnswer) + "\"}"));
                Long chatId = ragPersistenceService.saveChatRecord(userId, question, fallbackAnswer, knowledgeMatch, source, conversationId, fbTokenInput, fbTokenOutput);
                safeSend(emitter, completed, SseEmitter.event().name("done").data("{\"conversationId\":" + conversationId + ",\"chatId\":" + chatId + "}"));
                safeComplete(emitter, completed);
                checkAndUpgradeHotCache(md5, fallbackAnswer);
                String knowledgeIds = extractKnowledgeIds(relatedKnowledge);
                ragPersistenceService.asyncUpdateSimilarQuestion(question, fallbackAnswer, knowledgeIds, questionVector);
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
            final String finalMd5 = md5;
            final float[] finalQuestionVector = questionVector;
            final List<LawKnowledge> finalRelatedKnowledge = relatedKnowledge;
            final long finalTPre = tPre;
            final long finalTRewrite = tRewrite;
            final long finalTEmbed = tEmbed;
            final long finalTHyde = tHyde;
            final long finalTSimilar = tSimilar;
            final long finalTKnow = tKnow;
            final long finalTHistory = tHistory;
            final IntentClassifier.Intent finalIntent = intent;
            final String finalEntityLawType = entityLawType;

            streamingChatLanguageModel.generate(List.of(buildSystemPrompt(), UserMessage.from(userPrompt)), new StreamingResponseHandler<AiMessage>() {
                @Override
                public void onNext(String token) {
                    if (firstTokenAt.compareAndSet(0, System.currentTimeMillis())) {
                        long ttft = firstTokenAt.get() - t0;
                        log.info("[RAG-TIME] 6-firstToken: {}ms (total from start)", ttft);
                    }
                    answerBuilder.append(token);
                    safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + escapeJson(token) + "\"}"));
                }

                @Override
                public void onComplete(Response<AiMessage> response) {
                    String fullAnswer = answerBuilder.toString();
                    if (!finalRelatedKnowledge.isEmpty() && !verifyCitations(fullAnswer, finalRelatedKnowledge)) {
                        fullAnswer += UNVERIFIED_CITATION_WARNING;
                    }
                    String disclaimer = buildComplianceDisclaimer();
                    fullAnswer = fullAnswer + disclaimer;
                    long tGen = System.currentTimeMillis();
                    int sseTokenInput = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
                    int sseTokenOutput = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;

                    safeSend(emitter, completed, SseEmitter.event().name("token").data("{\"content\":\"" + escapeJson(disclaimer) + "\"}"));
                    Long chatId = ragPersistenceService.saveChatRecord(userId, finalQuestion, fullAnswer, finalKnowledgeMatch, finalSource, conversationId, sseTokenInput, sseTokenOutput);
                    safeSend(emitter, completed, SseEmitter.event().name("done").data("{\"conversationId\":" + conversationId + ",\"chatId\":" + chatId + "}"));
                    safeComplete(emitter, completed);

                    checkAndUpgradeHotCache(finalMd5, fullAnswer);
                    String knowledgeIds = extractKnowledgeIds(finalRelatedKnowledge);
                    ragPersistenceService.asyncUpdateSimilarQuestion(finalQuestion, fullAnswer, knowledgeIds, finalQuestionVector);

                    double topScore = finalRelatedKnowledge.isEmpty() ? 0.0 :
                            finalRelatedKnowledge.stream().mapToDouble(k -> k.getScore() != null ? k.getScore() : 0.0).max().orElse(0.0);
                    long totalMs = tGen - t0;
                    long preMs = finalTPre - t0;
                    long rewriteMs = finalTRewrite - finalTPre;
                    long embedMs = finalTEmbed - finalTRewrite;
                    long hydeMs = finalTHyde - finalTEmbed;
                    long simMs = finalTSimilar - finalTHyde;
                    long searchMs = finalTKnow - finalTSimilar;
                    long historyMs = finalTHistory - finalTKnow;
                    long genMs = tGen - finalTHistory;
                    long ttftMs = firstTokenAt.get() > 0 ? firstTokenAt.get() - t0 : 0;
                    ragMetricsService.recordRequest(finalSource, preMs, embedMs, searchMs, genMs, totalMs,
                            finalRelatedKnowledge.size(), topScore, ragConfig.isHydeEnabled(), null);
                    log.info("[RAG-TIME-SUMMARY] pre={}ms rewrite={}ms embed={}ms hyde={}ms sim={}ms search={}ms history={}ms ttft={}ms gen={}ms => total={}ms | source={} intent={} lawType={} retrieved={} topScore={} answerLen={}",
                            preMs, rewriteMs, embedMs, hydeMs, simMs, searchMs, historyMs, ttftMs, genMs, totalMs,
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
                    safeSend(emitter, completed, SseEmitter.event().name("error").data("{\"message\":\"" + escapeJson(fallback) + "\"}"));
                    safeComplete(emitter, completed);
                    ragMetricsService.recordRequest(finalSource + "_error", tPre - t0, finalTEmbed - tPre, finalTKnow - finalTEmbed, 0,
                            System.currentTimeMillis() - t0, 0, 0.0, false, null);
                }
            });

        } catch (Exception e) {
            log.error("[RAG-SSE] process error: {}", e.getMessage());
            safeSend(emitter, completed, SseEmitter.event().name("error").data("{\"message\":\"系统处理出现问题，请稍后再试\"}"));
            safeComplete(emitter, completed);
            ragMetricsService.recordRequest("process_error", 0, 0, 0, 0, System.currentTimeMillis() - t0, 0, 0.0, false, null);
        }
    }

    /**
     * 构建基于法律知识库的用户提示词（供流式方法复用，不含系统角色定义）
     */
    private String buildKnowledgeUserPrompt(String question, List<LawKnowledge> relatedKnowledge, String historyContext) {
        StringBuilder prompt = new StringBuilder();
        if (historyContext != null && !historyContext.isEmpty()) {
            prompt.append("=== 对话历史 ===\n").append(historyContext).append("\n\n");
        }
        prompt.append("=== 法律知识库内容 ===\n");
        for (LawKnowledge knowledge : relatedKnowledge) {
            prompt.append("【").append(knowledge.getTitle()).append("】\n");
            prompt.append(knowledge.getContent()).append("\n\n");
        }
        prompt.append("=== 用户问题 ===\n").append(question).append("\n");
        return prompt.toString();
    }

    /**
     * 构建大模型直接回答的用户提示词（供流式方法复用，不含系统角色定义）
     */
    private String buildDirectUserPrompt(String question, String historyContext) {
        StringBuilder prompt = new StringBuilder();
        if (historyContext != null && !historyContext.isEmpty()) {
            prompt.append("=== 对话历史 ===\n").append(historyContext).append("\n\n");
        }
        prompt.append("问题：").append(question).append("\n");
        return prompt.toString();
    }

    private static final String PROMPT_CONFIG_KEY = "rag.system_prompt";
    private static final String REWRITE_PROMPT_CONFIG_KEY = "rag.query_rewrite_prompt";

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个专业的中国法律咨询助手，由法律专家团队训练，精通中国法律法规体系。\n\n" +
            "你的核心职责：\n" +
            "- 基于提供的法律知识库内容，为用户提供准确、专业、易懂的法律解答\n" +
            "- 帮助用户理解复杂的法律概念、程序和权利义务\n" +
            "- 指导用户依法维权，告知可行的法律途径\n\n" +
            "行为准则（必须严格遵守）：\n" +
            "- 回答必须基于中国现行法律法规，不得凭空编造或臆测\n" +
            "- 重要法律依据必须明确引用，格式为：《法律名称》第XX条\n" +
            "- 如果你对某个法律问题不确定，必须明确说\"根据现有知识库，我无法确定\"，不得猜测\n" +
            "- 不得提供违法、违规或不道德的建议\n" +
            "- 涉及诉讼、仲裁等程序性问题时，提醒用户咨询专业律师\n" +
            "- 回答语言要专业但通俗易懂，让没有法律背景的普通用户也能理解\n\n" +
            "回答格式要求：\n" +
            "- 开头先给出简洁的结论或核心观点（2-3句话）\n" +
            "- 中间展开详细的法律分析和法条依据\n" +
            "- 结尾给出具体的操作建议或维权步骤\n" +
            "- 使用自然的段落和小标题组织内容，保持排版整洁\n" +
            "- 对于复杂问题，使用分步骤的方式说明，每一步标注序号";

    private String getSystemPrompt() {
        if (cachedSystemPrompt != null) {
            return cachedSystemPrompt;
        }
        synchronized (this) {
            if (cachedSystemPrompt != null) {
                return cachedSystemPrompt;
            }
            try {
                com.lhs.lawmind.entity.SysConfig config = sysConfigService.selectByKey(PROMPT_CONFIG_KEY);
                if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
                    cachedSystemPrompt = config.getConfigValue();
                    log.info("System prompt loaded from sys_config");
                }
            } catch (Exception e) {
                log.warn("Failed to load system prompt from sys_config, using default: {}", e.getMessage());
            }
            if (cachedSystemPrompt == null) {
                cachedSystemPrompt = DEFAULT_SYSTEM_PROMPT;
            }
            return cachedSystemPrompt;
        }
    }

    void refreshSystemPrompt() {
        cachedSystemPrompt = null;
        log.info("System prompt cache cleared, will reload on next request");
    }

    private String getRewritePrompt() {
        if (cachedRewritePrompt != null) {
            return cachedRewritePrompt;
        }
        synchronized (this) {
            if (cachedRewritePrompt != null) {
                return cachedRewritePrompt;
            }
            try {
                com.lhs.lawmind.entity.SysConfig config = sysConfigService.selectByKey(REWRITE_PROMPT_CONFIG_KEY);
                if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
                    cachedRewritePrompt = config.getConfigValue();
                    log.info("Rewrite prompt loaded from sys_config");
                }
            } catch (Exception e) {
                log.warn("Failed to load rewrite prompt from sys_config: {}", e.getMessage());
            }
            if (cachedRewritePrompt == null) {
                cachedRewritePrompt = "你是一个法律检索专家。请将用户的口语化法律问题改写为适合法律知识库检索的查询语句。\n\n"
                    + "规则：\n1. 保留用户原始问题的核心诉求\n2. 补充相关的正式法律术语和法条关键词\n"
                    + "3. 输出仅一行纯文本，不要有任何解释、标点或换行\n4. 如果用户问题已经是正式法律表述，原样输出";
            }
            return cachedRewritePrompt;
        }
    }

    /**
     * 使用 LLM 改写查询以提升检索效果
     * @param question
     * @return
     */
    private String rewriteQueryWithLLM(String question) {
        // 1. LRU cache check
        String cached = rewriteCache.get(question);
        if (cached != null) {
            log.info("[RAG] LLM query rewrite cache hit: originalLen={}", question.length());
            return cached;
        }

        // 2. Lazy init turbo model or fall back to chat model
        ChatLanguageModel model = getRewriteModel();
        if (model == null) {
            return null;
        }
        try {
            String rewritePrompt = getRewritePrompt();
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
     * 合并规则扩展查询与 LLM 改写查询：规则术语为基础，LLM 术语补充
     * 确保规则扩展中的关键法律术语不被 LLM 改写丢弃
     */
    private String mergeQueries(String ruleQuery, String llmQuery) {
        java.util.LinkedHashSet<String> terms = new java.util.LinkedHashSet<>();
        for (String term : ruleQuery.split("\\s+")) {
            if (!term.isBlank()) {
                terms.add(term);
            }
        }
        int ruleTermCount = terms.size();
        for (String term : llmQuery.split("\\s+")) {
            if (!term.isBlank() && terms.add(term)) {
                // term was new, added to set
            }
        }
        String merged = String.join(" ", terms);
        log.debug("[RAG] mergeQueries: ruleTerms={} llmNewTerms={} mergedLen={}",
                ruleTermCount, terms.size() - ruleTermCount, merged.length());
        return merged;
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
                rewriteTurboModel = chatLanguageModel;
                return chatLanguageModel;
            }
        }
    }

    private static final String HYDE_PROMPT_CONFIG_KEY = "rag.hyde_prompt";

    private String getHydePrompt() {
        if (cachedHydePrompt != null) {
            return cachedHydePrompt;
        }
        synchronized (this) {
            if (cachedHydePrompt != null) {
                return cachedHydePrompt;
            }
            try {
                com.lhs.lawmind.entity.SysConfig config = sysConfigService.selectByKey(HYDE_PROMPT_CONFIG_KEY);
                if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
                    cachedHydePrompt = config.getConfigValue();
                    log.info("HyDE prompt loaded from sys_config");
                }
            } catch (Exception e) {
                log.warn("Failed to load HyDE prompt from sys_config: {}", e.getMessage());
            }
            if (cachedHydePrompt == null) {
                cachedHydePrompt = "你是一位资深中国法律专家。请根据用户的法律问题，写一段假设性的法律分析回答（约200-400字）。\n\n"
                    + "要求：\n1. 引用相关的中国法律法规条文（如《劳动合同法》《民法典》等）\n"
                    + "2. 使用正式的法律文书语言风格\n3. 涵盖问题的核心法律要点和可能的处理方式\n"
                    + "4. 不要使用\"假设\"、\"如果\"等推测性措辞，直接以法律专家身份给出分析";
            }
            return cachedHydePrompt;
        }
    }

    /**
     * HyDE (Hypothetical Document Embeddings) — 生成假设法律文档
     * 让 LLM 先写一段假设性的法律回答，再将这个"假设文档"向量化用于知识库检索。
     * 假设回答比短查询更接近真实法律文档的语义空间，从而提升召回率。
     *
     * @param question 用户问题（已预处理）
     * @return 假设文档文本，失败返回 null（调用方降级到查询向量）
     */
    private String generateHydeDocument(String question) {
        if (chatLanguageModel == null) {
            return null;
        }
        try {
            String hydePrompt = getHydePrompt();
            String fullPrompt = hydePrompt + "\n\n用户问题：" + question;
            Response<AiMessage> response = chatLanguageModel.generate(UserMessage.from(fullPrompt));
            String hydeDoc = response.content().text().trim();
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

    private SystemMessage buildSystemPrompt() {
        return SystemMessage.from(getSystemPrompt());
    }

    /**
     * 合规性声明（附加到所有来源为 law_knowledge 或 llm_direct 的回答末尾）
     */
    private static final String COMPLIANCE_DISCLAIMER =
            "\n\n---\n> ⚠️ 以上内容由 AI 生成，仅供参考，不构成法律建议。如涉及具体法律事务，请咨询专业律师。";

    /**
     * 引用来源未验证警告
     */
    private static final String UNVERIFIED_CITATION_WARNING =
            "\n\n---\n> ⚠️ 以上回答中部分法律引用未能与知识库检索结果完全匹配，可能包含不准确的信息，请核实后参考。";

    /**
     * 中文数字到阿拉伯数字的映射
     */
    private static final java.util.Map<Character, Integer> CN_NUM_MAP = java.util.Map.ofEntries(
            java.util.Map.entry('零', 0), java.util.Map.entry('〇', 0),
            java.util.Map.entry('一', 1), java.util.Map.entry('二', 2),
            java.util.Map.entry('三', 3), java.util.Map.entry('四', 4),
            java.util.Map.entry('五', 5), java.util.Map.entry('六', 6),
            java.util.Map.entry('七', 7), java.util.Map.entry('八', 8),
            java.util.Map.entry('九', 9), java.util.Map.entry('十', 10),
            java.util.Map.entry('百', 100), java.util.Map.entry('千', 1000),
            java.util.Map.entry('万', 10000)
    );

    /**
     * 将中文数字（如"第八十七条"、"第一千一百七十九条"）转换为阿拉伯数字
     */
    private int chineseNumToArabic(String cnNum) {
        if (cnNum == null || cnNum.isEmpty()) return -1;
        // 先尝试直接解析为数字
        try { return Integer.parseInt(cnNum); } catch (NumberFormatException ignored) {}
        int result = 0;
        int temp = 0;
        int lastUnit = 1;
        for (int i = 0; i < cnNum.length(); i++) {
            char c = cnNum.charAt(i);
            Integer val = CN_NUM_MAP.get(c);
            if (val == null) return -1;
            if (val >= 10) {
                if (temp == 0) temp = 1;
                if (val == 10000) {
                    result = (result + temp) * val;
                    temp = 0;
                } else {
                    temp *= val;
                    if (val > lastUnit) lastUnit = val;
                }
            } else {
                temp = temp * 10 + val;
            }
        }
        result += temp;
        return result;
    }

    /**
     * 从回答文本中提取法律引用列表
     * 匹配格式：《法律名称》第X条 或 第X条
     */
    private java.util.List<String> extractCitations(String answer) {
        java.util.List<String> citations = new java.util.ArrayList<>();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:《([^》]{1,30})》)?\\s*第([一二三四五六七八九十百千万零〇\\d]+)条");
        java.util.regex.Matcher m = p.matcher(answer);
        while (m.find()) {
            citations.add(m.group());
        }
        return citations;
    }

    /**
     * 验证回答中的法律引用是否在检索到的知识库结果中有依据
     *
     * @param answer  AI 生成的回答
     * @param relatedKnowledge 检索到的知识列表
     * @return true=所有引用均可验证, false=存在未验证引用
     */
    private boolean verifyCitations(String answer, List<LawKnowledge> relatedKnowledge) {
        if (!ragConfig.isCitationVerificationEnabled() || relatedKnowledge == null || relatedKnowledge.isEmpty()) {
            return true;
        }
        java.util.List<String> citations = extractCitations(answer);
        if (citations.isEmpty()) {
            return true;
        }
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?:《([^》]{1,30})》)?\\s*第([一二三四五六七八九十百千万零〇\\d]+)条");
        boolean allVerified = true;
        for (String citation : citations) {
            java.util.regex.Matcher m = p.matcher(citation);
            if (!m.find()) continue;
            String lawName = m.group(1);
            String articleNumStr = m.group(2);
            int articleNum = chineseNumToArabic(articleNumStr);
            boolean verified = false;
            for (LawKnowledge k : relatedKnowledge) {
                boolean lawMatch = lawName == null
                        || (k.getTitle() != null && k.getTitle().contains(lawName))
                        || (k.getLawType() != null && k.getLawType().contains(lawName));
                boolean articleMatch = articleNum < 0
                        || (k.getArticleNumber() != null && k.getArticleNumber() == articleNum)
                        || (k.getTitle() != null && k.getTitle().contains("第" + articleNumStr + "条"))
                        || (k.getContent() != null && k.getContent().contains("第" + articleNumStr + "条"));
                if (lawMatch && articleMatch) {
                    verified = true;
                    break;
                }
            }
            if (!verified) {
                log.warn("[RAG] 引用未验证: citation=\"{}\" articleNum={}", citation.trim(), articleNum);
                allVerified = false;
            }
        }
        if (!allVerified) {
            log.warn("[RAG] 回答中存在{}条未验证的法律引用（共{}条引用）",
                    citations.stream().filter(c -> {
                        java.util.regex.Matcher cm = p.matcher(c);
                        if (!cm.find()) return false;
                        String lawNameCheck = cm.group(1);
                        String numCheck = cm.group(2);
                        int an = chineseNumToArabic(numCheck);
                        return relatedKnowledge.stream().noneMatch(k -> {
                            boolean lm = lawNameCheck == null
                                    || (k.getTitle() != null && k.getTitle().contains(lawNameCheck))
                                    || (k.getLawType() != null && k.getLawType().contains(lawNameCheck));
                            boolean am = an < 0
                                    || (k.getArticleNumber() != null && k.getArticleNumber() == an)
                                    || (k.getTitle() != null && k.getTitle().contains("第" + numCheck + "条"))
                                    || (k.getContent() != null && k.getContent().contains("第" + numCheck + "条"));
                            return lm && am;
                        });
                    }).count(), citations.size());
        }
        return allVerified;
    }

    private record GenerationResult(String answer, int inputTokens, int outputTokens) {}

    /**
     * 为 AI 回答附加合规性声明
     */
    private String appendComplianceDisclaimer(String answer, String source) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        if ("hot_cache".equals(source) || "similar_question".equals(source) || "non_legal_reject".equals(source)) {
            return answer;
        }
        if (answer.endsWith(COMPLIANCE_DISCLAIMER)) {
            return answer;
        }
        return answer + COMPLIANCE_DISCLAIMER;
    }

    /**
     * 构建合规性声明文本
     */
    private String buildComplianceDisclaimer() {
        return COMPLIANCE_DISCLAIMER;
    }

    /**
     * 清洗用户输入，防止提示词注入攻击。
     * 移除常见的注入模式（角色覆盖、分隔符注入、指令劫持等）。
     */
    static String sanitizeUserInput(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String sanitized = input;

        // 移除 markdown 代码块边界（防止通过 ``` 注入系统指令）
        sanitized = sanitized.replaceAll("```[\\s\\S]*?```", "[blocked]");

        // 移除常见的注入指令模式（大小写不敏感）
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

        // 长度限制：拒绝超过 2000 字符的问题
        if (sanitized.length() > 2000) {
            sanitized = sanitized.substring(0, 2000);
        }

        return sanitized.trim();
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     */
    private String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
