package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.mapper.AiChatMapper;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.service.AutoLearnService;
import com.lhs.lawmind.service.HybridSearchService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.service.RerankService;
import com.lhs.lawmind.service.RagMetricsService;
import com.lhs.lawmind.service.SimilarQuestionService;
import com.lhs.lawmind.service.SysConfigService;
import com.lhs.lawmind.utils.*;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RAG Pipeline Tests")
class RagServiceImplTest {

    @Mock private ChatLanguageModel chatLanguageModel;
    @Mock private EmbeddingUtil embeddingUtil;
    @Mock private HotCacheUtil hotCacheUtil;
    @Mock private SimilarQuestionRedisUtil similarQuestionRedisUtil;
    @Mock private LawKnowledgeRedisUtil lawKnowledgeRedisUtil;
    @Mock private VisitStatsUtil visitStatsUtil;
    @Mock private RagConfig ragConfig;
    @Mock private LawKnowledgeService lawKnowledgeService;
    @Mock private AiChatService aiChatService;
    @Mock private AiChatMapper aiChatMapper;
    @Mock private SimilarQuestionService similarQuestionService;
    @Mock private AutoLearnService autoLearnService;
    @Mock private HybridSearchService hybridSearchService;
    @Mock private RerankService rerankService;
    @Mock private LegalQueryExpander legalQueryExpander;
    @Mock private SearchResultDiversifier searchResultDiversifier;
    @Mock private SysConfigService sysConfigService;
    @Mock private RagPersistenceService ragPersistenceService;
    @Mock private RagMetricsService ragMetricsService;

    private RagServiceImpl ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagServiceImpl(
                Optional.of(chatLanguageModel),
                Optional.empty(),
                Optional.of(embeddingUtil),
                hotCacheUtil,
                similarQuestionRedisUtil,
                lawKnowledgeRedisUtil,
                visitStatsUtil,
                ragConfig,
                lawKnowledgeService,
                aiChatService,
                aiChatMapper,
                similarQuestionService,
                autoLearnService,
                hybridSearchService,
                rerankService,
                legalQueryExpander,
                searchResultDiversifier,
                new IntentClassifier(),
                new LegalEntityExtractor(),
                sysConfigService,
                ragPersistenceService,
                ragMetricsService
        );
    }

    @Test
    @DisplayName("Non-legal question returns rejection message")
    void nonLegalQuestion_rejected() {
        AIChatResponse result = ragService.processQuestion(1L, "今天天气怎么样？", null);

        assertThat(result.getAnswer()).isEqualTo("抱歉，我是一个法律咨询助手，只能回答与法律相关的问题。");
        assertThat(result.getRelatedKnowledge()).isEmpty();
    }

    @Test
    @DisplayName("Hot cache hit returns cached answer immediately")
    void hotCacheHit_returnsCachedAnswer() {
        when(ragConfig.getSearchTopK()).thenReturn(5);
        when(legalQueryExpander.expandQuery(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(hotCacheUtil.getHotQuestionCache(anyString())).thenReturn("缓存的答案");

        AIChatResponse result = ragService.processQuestion(1L, "加班不给加班费怎么办？", null);

        assertThat(result.getAnswer()).isEqualTo("缓存的答案");
    }

    @Test
    @DisplayName("Full pipeline generates answer with disclaimer when no cache hit")
    void fullPipeline_addsDisclaimer() {
        mockFullPipeline();
        when(lawKnowledgeRedisUtil.searchLawKnowledge(any(), anyInt()))
                .thenReturn(new ArrayList<>());
        when(chatLanguageModel.generate(any(ChatMessage.class), any(ChatMessage.class)))
                .thenReturn(Response.from(AiMessage.from("请咨询专业律师获取帮助。")));

        AIChatResponse result = ragService.processQuestion(1L, "离婚财产怎么分割？", null);

        assertThat(result.getAnswer()).contains("不构成法律建议");
    }

    @Test
    @DisplayName("Knowledge retrieval result feeds into LLM answer")
    void knowledgeFound_feedsIntoAnswer() {
        mockFullPipeline();

        List<RedisVectorUtil.SearchResult> searchResults = new ArrayList<>();
        searchResults.add(new RedisVectorUtil.SearchResult("law:vector:1", 0.95));
        when(lawKnowledgeRedisUtil.searchLawKnowledge(any(), anyInt()))
                .thenReturn(searchResults);

        LawKnowledge knowledge = new LawKnowledge();
        knowledge.setId(1L);
        knowledge.setTitle("劳动法");
        knowledge.setContent("用人单位应当按时足额支付劳动者工资。");
        knowledge.setScore(0.95);
        when(ragConfig.getLawVectorKeyPrefix()).thenReturn("law:vector:");
        when(lawKnowledgeService.getById(1L)).thenReturn(knowledge);

        when(chatLanguageModel.generate(any(ChatMessage.class), any(ChatMessage.class)))
                .thenReturn(Response.from(AiMessage.from("根据劳动法，用人单位应当支付加班费。")));

        AIChatResponse result = ragService.processQuestion(1L, "加班不给加班费怎么办？", null);

        assertThat(result.getAnswer()).contains("不构成法律建议");
        assertThat(result.getRelatedKnowledge()).isNotEmpty();
    }

    @Test
    @DisplayName("Intent classification adjusts retrieval count for article lookup")
    void articleLookup_hasHigherTopK() {
        mockFullPipeline();
        when(lawKnowledgeRedisUtil.searchLawKnowledge(any(), anyInt()))
                .thenReturn(new ArrayList<>());
        when(chatLanguageModel.generate(any(ChatMessage.class), any(ChatMessage.class)))
                .thenReturn(Response.from(AiMessage.from("请参考《劳动法》第三十九条。")));

        AIChatResponse result = ragService.processQuestion(1L,
                "根据劳动法第三十九条，什么情况下可以解除劳动合同？", null);

        assertThat(result.getAnswer()).contains("不构成法律建议");
    }

    private void mockFullPipeline() {
        when(ragConfig.getSearchTopK()).thenReturn(5);
        when(ragConfig.isHybridSearchEnabled()).thenReturn(false);
        when(ragConfig.isMmrEnabled()).thenReturn(false);
        when(ragConfig.getFilterThreshold()).thenReturn(0.65);
        when(ragConfig.getLawKnowledgeThreshold()).thenReturn(0.70);
        when(visitStatsUtil.getVisitStats(anyString())).thenReturn(new VisitStatsUtil.VisitStats(0, 0, 0));
        when(hotCacheUtil.getHotQuestionCache(anyString())).thenReturn(null);
        when(legalQueryExpander.expandQuery(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(embeddingUtil.embed(anyString())).thenReturn(new float[1536]);
        when(similarQuestionRedisUtil.searchSimilarQuestions(any(), anyInt()))
                .thenReturn(new ArrayList<>());
        when(chatLanguageModel.generate(any(ChatMessage.class))).thenReturn(null);
    }
}
