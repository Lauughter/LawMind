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
@DisplayName("IntentGate — 意图门控集成测试")
class IntentGateTest {

    @Mock
    private ChatLanguageModel chatLanguageModel;

    private IntentGate intentGate;

    @BeforeEach
    void setUp() {
        var config = new IntentGateConfig(
                true, // ruleOnly
                new IntentGateConfig.DomainConfig(
                        List.of("合同", "劳动", "工伤", "赔偿", "婚姻", "离婚", "继承",
                                "刑事", "犯罪", "诉讼", "仲裁", "法院", "律师", "法条",
                                "法律", "法规", "司法", "判决", "债务", "侵权", "违约",
                                "交通事故", "保险", "公司", "房产", "拆迁", "征收"),
                        List.of("天气", "游戏", "电影", "音乐", "美食", "旅游", "运动",
                                "健身", "美妆", "宠物", "彩票", "赌博"),
                        ""
                ),
                new IntentGateConfig.IntentConfig(
                        List.of("第几条规定", "第几条", "法条原文", "查法条", "法律条文",
                                "法条是", "法律规定是什么", "条文内容", "法条查询"),
                        List.of("赔偿多少钱", "赔偿多少", "赔偿金", "赔偿标准", "赔偿金额",
                                "计算赔偿", "工伤赔偿", "经济补偿金", "加班费怎么算",
                                "抚养费多少", "怎么算", "计算公式", "金额计算"),
                        List.of("有没有类似案例", "类似案例", "判例", "判决案例",
                                "类似案件", "案例检索", "判决书"),
                        List.of("怎么写起诉状", "起诉状模板", "帮我写起诉状", "写一份合同",
                                "合同模板", "帮我写合同", "劳动仲裁申请书", "帮我起草",
                                "帮我写一份", "协议模板", "借条怎么写", "离婚协议怎么写"),
                        List.of("什么是", "是什么意思", "名词解释", "法律概念",
                                "指的是什么", "通俗解释", "普法"),
                        List.of(),
                        ""
                ),
                new IntentGateConfig.ComplexityConfig(
                        0.4, 0.2, 0.2, 0.2,
                        0.35, 0.65,
                        List.of("刑法", "刑事", "知识产权", "破产", "重整", "反垄断",
                                "证券", "信托", "公司", "股权", "跨国", "涉外"),
                        List.of("怎么起诉", "起诉流程", "仲裁流程", "诉讼程序",
                                "怎么申请", "需要什么材料", "管辖权", "时效", "上诉",
                                "再审", "强制执行")
                ),
                new IntentGateConfig.RouteConfig(
                        0.5,
                        List.of("ARTICLE_LOOKUP", "LEGAL_KNOWLEDGE"),
                        List.of("LEGAL_CONSULTATION"),
                        List.of("DOCUMENT_DRAFTING")),
                Map.of(
                        "completely-unrelated", "我是法律助手，请提出法律相关问题。",
                        "borderline-non-legal", "请就具体法律问题向我咨询。",
                        "malformed-input", "输入格式不规范，请重新描述。",
                        "sensitive-content", "涉及敏感内容，请重新表述。"
                )
        );

        DomainGate domainGate = new DomainGate(config, chatLanguageModel);
        IntentClassifierEnhanced classifier = new IntentClassifierEnhanced(config, chatLanguageModel);
        ComplexityAssessor complexityAssessor = new ComplexityAssessor(config);
        IntentRouter router = new IntentRouter(config);

        intentGate = new IntentGate(domainGate, classifier, complexityAssessor, router, config);
    }

    @Test
    @DisplayName("完整流程 — 法条查询被接受并路由到 FAST 通道")
    void shouldAcceptArticleLookup_routeToFast() {
        GateResult result = intentGate.process("查法条：劳动合同法第47条");
        assertThat(result.accepted()).isTrue();
        assertThat(result.intentResult().intentType()).isEqualTo(IntentType.ARTICLE_LOOKUP);
        assertThat(result.routeDecision().channel()).isEqualTo(RouteDecision.Channel.FAST);
    }

    @Test
    @DisplayName("完整流程 — 简单法律咨询被路由到 FAST 通道（降级）")
    void shouldRouteSimpleConsultation_toFast() {
        GateResult result = intentGate.process("劳动合同到期不续签有补偿吗？");
        assertThat(result.accepted()).isTrue();
        assertThat(result.routeDecision().channel()).isEqualTo(RouteDecision.Channel.FAST);
    }

    @Test
    @DisplayName("完整流程 — 复杂法律咨询被路由到 AGENT 通道")
    void shouldRouteComplexConsultation_toAgent() {
        GateResult result = intentGate.process(
                "我在一家跨国公司工作3年，公司涉嫌证券欺诈被调查，现在要破产重整。我持有股权激励，工资被拖欠了3个月。请问我该如何维权？需要收集哪些证据？去哪里起诉？");
        assertThat(result.accepted()).isTrue();
        assertThat(result.routeDecision().channel()).isEqualTo(RouteDecision.Channel.AGENT);
    }

    @Test
    @DisplayName("完整流程 — 文书生成被路由到 HYBRID 通道")
    void shouldRouteDocumentDrafting_toHybrid() {
        GateResult result = intentGate.process("帮我写一份离婚协议书，我们有孩子和共同财产");
        assertThat(result.accepted()).isTrue();
        assertThat(result.routeDecision().channel()).isEqualTo(RouteDecision.Channel.HYBRID);
    }

    @Test
    @DisplayName("完整流程 — 法律知识问答被路由到 FAST 通道")
    void shouldRouteLegalKnowledge_toFast() {
        GateResult result = intentGate.process("什么是善意取得？通俗解释一下");
        assertThat(result.accepted()).isTrue();
        assertThat(result.routeDecision().channel()).isEqualTo(RouteDecision.Channel.FAST);
    }

    @Test
    @DisplayName("完整流程 — 非法律问题被拒绝")
    void shouldRejectNonLegalQuestion() {
        GateResult result = intentGate.process("今天天气怎么样？");
        assertThat(result.accepted()).isFalse();
        assertThat(result.rejectResponse()).isNotNull();
        assertThat(result.rejectResponse()).contains("法律");
    }

    @Test
    @DisplayName("完整流程 — 游戏问题被拒绝")
    void shouldRejectGameQuestion() {
        GateResult result = intentGate.process("推荐一款好玩的手机游戏");
        assertThat(result.accepted()).isFalse();
    }

    @Test
    @DisplayName("完整流程 — 金额计算被接受")
    void shouldAcceptCalculation() {
        GateResult result = intentGate.process("工伤赔偿多少钱？我工资5000元，鉴定为十级伤残");
        assertThat(result.accepted()).isTrue();
        assertThat(result.intentResult().intentType()).isEqualTo(IntentType.CALCULATION);
    }

    @Test
    @DisplayName("完整流程 — 案例检索被接受")
    void shouldAcceptCaseSearch() {
        GateResult result = intentGate.process("有没有类似案例？被公司违法辞退的判决书");
        assertThat(result.accepted()).isTrue();
        assertThat(result.intentResult().intentType()).isEqualTo(IntentType.CASE_SEARCH);
    }

    @Test
    @DisplayName("完整流程 — 空输入被拒绝")
    void shouldRejectEmptyInput() {
        GateResult result = intentGate.process("");
        assertThat(result.accepted()).isFalse();
    }

    @Test
    @DisplayName("GateResult 工厂方法 — accept")
    void gateResultAccept_shouldReturnAccepted() {
        DomainVerdict verdict = DomainVerdict.legal("test");
        IntentResult intent = IntentResult.rule(IntentType.LEGAL_CONSULTATION, 0.9, "agent");
        RouteDecision route = RouteDecision.agent("test strategy", 1500);

        GateResult result = GateResult.accept(verdict, intent, route);
        assertThat(result.accepted()).isTrue();
        assertThat(result.rejectResponse()).isNull();
        assertThat(result.intentResult()).isEqualTo(intent);
        assertThat(result.routeDecision()).isEqualTo(route);
    }

    @Test
    @DisplayName("GateResult 工厂方法 — reject")
    void gateResultReject_shouldReturnRejected() {
        DomainVerdict verdict = DomainVerdict.nonLegal("test");
        GateResult result = GateResult.reject(verdict, "请提出法律问题");

        assertThat(result.accepted()).isFalse();
        assertThat(result.rejectResponse()).isEqualTo("请提出法律问题");
        assertThat(result.intentResult()).isNull();
        assertThat(result.routeDecision()).isNull();
    }

    @Test
    @DisplayName("DomainVerdict 工厂方法")
    void domainVerdictFactoryMethods() {
        DomainVerdict legal = DomainVerdict.legal("test");
        assertThat(legal.isLegal()).isTrue();
        assertThat(legal.confidence()).isGreaterThan(0.9);

        DomainVerdict nonLegal = DomainVerdict.nonLegal("test");
        assertThat(nonLegal.isLegal()).isFalse();

        DomainVerdict custom = DomainVerdict.nonLegal(0.5, "reason", "sensitive");
        assertThat(custom.category()).isEqualTo("sensitive");
        assertThat(custom.confidence()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("IntentResult 工厂方法")
    void intentResultFactoryMethods() {
        IntentResult rule = IntentResult.rule(IntentType.ARTICLE_LOOKUP, 0.9, "fast");
        assertThat(rule.matchedBy()).isEqualTo("RULE");
        assertThat(rule.confidence()).isEqualTo(0.9);

        IntentResult llm = IntentResult.llm(IntentType.LEGAL_CONSULTATION, 0.7, "agent");
        assertThat(llm.matchedBy()).isEqualTo("LLM");
        assertThat(llm.confidence()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("RouteDecision 工厂方法")
    void routeDecisionFactoryMethods() {
        RouteDecision fast = RouteDecision.fast("quick", 300);
        assertThat(fast.channel()).isEqualTo(RouteDecision.Channel.FAST);
        assertThat(fast.estimatedTokens()).isEqualTo(300);

        RouteDecision agent = RouteDecision.agent("complex", 1500);
        assertThat(agent.channel()).isEqualTo(RouteDecision.Channel.AGENT);

        RouteDecision hybrid = RouteDecision.hybrid("template", 2000);
        assertThat(hybrid.channel()).isEqualTo(RouteDecision.Channel.HYBRID);

        RouteDecision reject = RouteDecision.reject("non-legal");
        assertThat(reject.channel()).isEqualTo(RouteDecision.Channel.REJECT);
        assertThat(reject.estimatedTokens()).isZero();
    }
}
