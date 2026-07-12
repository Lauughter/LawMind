package com.lhs.lawmind.agent.gate;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("IntentClassifierEnhanced — 意图分类测试")
class IntentClassifierEnhancedTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    private IntentClassifierEnhanced classifier;

    @BeforeEach
    void setUp() {
        var config = new IntentGateConfig(
                true, // ruleOnly
                null,
                new IntentGateConfig.IntentConfig(
                        List.of("第几条规定", "第几条", "法条原文", "法条内容", "哪一条法律",
                                "法律条文", "查法条", "哪个法条", "什么法条", "法条是",
                                "法律规定是什么", "法律如何规定", "条文内容", "法条查询"),
                        List.of("赔偿多少钱", "赔偿多少", "赔多少钱", "赔偿金", "赔偿标准",
                                "赔偿金额", "计算赔偿", "赔多少", "工伤赔偿", "经济补偿金",
                                "加班费怎么算", "诉讼费多少", "抚养费多少", "怎么算",
                                "计算公式", "金额计算"),
                        List.of("有没有类似案例", "类似案例", "判例", "判决案例",
                                "类似案件", "别人怎么判", "参考案例", "案例检索",
                                "有没有判过", "法院怎么判", "胜诉率", "判决书"),
                        List.of("怎么写起诉状", "起诉状模板", "帮我写起诉状", "写一份合同",
                                "合同模板", "帮我写合同", "劳动仲裁申请书", "帮我起草",
                                "帮我写一份", "协议模板", "借条怎么写", "离婚协议怎么写",
                                "遗嘱怎么写", "法律文书", "格式文书"),
                        List.of("什么是", "是什么意思", "名词解释", "法律概念", "指的是什么",
                                "定义是什么", "含义", "怎么理解", "通俗解释", "普法"),
                        List.of(),
                        ""
                ),
                null,
                new IntentGateConfig.RouteConfig(0.5,
                        List.of("ARTICLE_LOOKUP", "LEGAL_KNOWLEDGE"),
                        List.of("LEGAL_CONSULTATION"),
                        List.of("DOCUMENT_DRAFTING")),
                Map.of()
        );
        classifier = new IntentClassifierEnhanced(config, chatLanguageModel);
    }

    @Test
    @DisplayName("法条查询意图 — 规则命中")
    void shouldClassifyArticleLookup_byRule() {
        IntentResult result = classifier.classify("劳动合同法第几条是规定解除合同的？");
        assertThat(result.intentType()).isEqualTo(IntentType.ARTICLE_LOOKUP);
        assertThat(result.matchedBy()).isEqualTo("RULE");
        assertThat(result.confidence()).isGreaterThan(0.8);
    }

    @Test
    @DisplayName("法条查询意图 — 查法条")
    void shouldClassifyArticleLookup_checkArticle() {
        IntentResult result = classifier.classify("查法条：民法典婚姻家庭编");
        assertThat(result.intentType()).isEqualTo(IntentType.ARTICLE_LOOKUP);
    }

    @Test
    @DisplayName("金额计算意图 — 规则命中")
    void shouldClassifyCalculation_byRule() {
        IntentResult result = classifier.classify("工伤赔偿多少钱？我工资8000元");
        assertThat(result.intentType()).isEqualTo(IntentType.CALCULATION);
        assertThat(result.matchedBy()).isEqualTo("RULE");
    }

    @Test
    @DisplayName("金额计算意图 — 抚养费")
    void shouldClassifyCalculation_childSupport() {
        IntentResult result = classifier.classify("离婚后抚养费多少？我月收入一万");
        assertThat(result.intentType()).isEqualTo(IntentType.CALCULATION);
    }

    @Test
    @DisplayName("案例检索意图 — 规则命中")
    void shouldClassifyCaseSearch_byRule() {
        IntentResult result = classifier.classify("有没有类似案例？我妈妈被诈骗了");
        assertThat(result.intentType()).isEqualTo(IntentType.CASE_SEARCH);
    }

    @Test
    @DisplayName("案例检索意图 — 法院怎么判")
    void shouldClassifyCaseSearch_courtDecision() {
        IntentResult result = classifier.classify("劳动纠纷法院怎么判？");
        assertThat(result.intentType()).isEqualTo(IntentType.CASE_SEARCH);
    }

    @Test
    @DisplayName("文书生成意图 — 起诉状")
    void shouldClassifyDocumentDrafting_lawsuit() {
        IntentResult result = classifier.classify("怎么写起诉状？我要告公司拖欠工资");
        assertThat(result.intentType()).isEqualTo(IntentType.DOCUMENT_DRAFTING);
    }

    @Test
    @DisplayName("文书生成意图 — 合同模板")
    void shouldClassifyDocumentDrafting_contract() {
        IntentResult result = classifier.classify("请帮我写一份房屋租赁合同");
        assertThat(result.intentType()).isEqualTo(IntentType.DOCUMENT_DRAFTING);
    }

    @Test
    @DisplayName("法律知识问答意图 — 名词解释")
    void shouldClassifyLegalKnowledge_definition() {
        IntentResult result = classifier.classify("什么是无因管理？通俗解释一下");
        assertThat(result.intentType()).isEqualTo(IntentType.LEGAL_KNOWLEDGE);
    }

    @Test
    @DisplayName("默认法律咨询意图 — 无关键词命中")
    void shouldDefaultToLegalConsultation_whenNoKeywordMatch() {
        IntentResult result = classifier.classify("我和公司发生了劳动纠纷，应该怎么维权？");
        // "劳动纠纷"不在关键词列表中，但"劳动"是domain关键词不是intent关键词
        assertThat(result.intentType()).isEqualTo(IntentType.LEGAL_CONSULTATION);
        assertThat(result.matchedBy()).isEqualTo("RULE"); // ruleOnly模式返回默认
    }

    @Test
    @DisplayName("空输入 — 默认法律咨询")
    void shouldDefaultToLegalConsultation_forEmptyInput() {
        IntentResult result = classifier.classify("");
        assertThat(result.intentType()).isEqualTo(IntentType.LEGAL_CONSULTATION);
    }

    @Test
    @DisplayName("null输入 — 默认法律咨询")
    void shouldDefaultToLegalConsultation_forNullInput() {
        IntentResult result = classifier.classify(null);
        assertThat(result.intentType()).isEqualTo(IntentType.LEGAL_CONSULTATION);
    }

    @Test
    @DisplayName("法条查询默认路由为FAST")
    void shouldSuggestFastRoute_forArticleLookup() {
        IntentResult result = classifier.classify("法条查询：刑法第232条");
        assertThat(result.suggestedRoute()).isEqualTo("fast");
    }

    @Test
    @DisplayName("法律知识默认路由为FAST")
    void shouldSuggestFastRoute_forLegalKnowledge() {
        IntentResult result = classifier.classify("什么是善意取得？");
        assertThat(result.suggestedRoute()).isEqualTo("fast");
    }

    @Test
    @DisplayName("文书生成默认路由为HYBRID")
    void shouldSuggestHybridRoute_forDocumentDrafting() {
        IntentResult result = classifier.classify("帮我写一份离婚协议书");
        assertThat(result.suggestedRoute()).isEqualTo("hybrid");
    }
}
