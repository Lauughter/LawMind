package com.lhs.lawmind.agent.gate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("IntentRouter — 路由决策测试")
class IntentRouterTest {

    private IntentRouter router;

    @BeforeEach
    void setUp() {
        var config = new IntentGateConfig(
                true,
                null,
                null,
                null,
                new IntentGateConfig.RouteConfig(
                        0.5,
                        List.of("ARTICLE_LOOKUP", "LEGAL_KNOWLEDGE"),
                        List.of("LEGAL_CONSULTATION"),
                        List.of("DOCUMENT_DRAFTING")),
                Map.of()
        );
        router = new IntentRouter(config);
    }

    @Test
    @DisplayName("法条查询 + SIMPLE → FAST 通道")
    void shouldRouteArticleLookupSimple_toFast() {
        IntentResult intent = IntentResult.rule(IntentType.ARTICLE_LOOKUP, 0.9, "fast");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.SIMPLE);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.FAST);
    }

    @Test
    @DisplayName("法条查询 + COMPLEX → AGENT 通道（升级）")
    void shouldRouteArticleLookupComplex_toAgent() {
        IntentResult intent = IntentResult.rule(IntentType.ARTICLE_LOOKUP, 0.9, "fast");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.COMPLEX);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.AGENT);
    }

    @Test
    @DisplayName("法律知识 + SIMPLE → FAST 通道")
    void shouldRouteLegalKnowledge_toFast() {
        IntentResult intent = IntentResult.rule(IntentType.LEGAL_KNOWLEDGE, 0.9, "fast");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.SIMPLE);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.FAST);
    }

    @Test
    @DisplayName("法律咨询 + SIMPLE → FAST 通道（降级）")
    void shouldRouteConsultationSimple_toFast() {
        IntentResult intent = IntentResult.rule(IntentType.LEGAL_CONSULTATION, 0.9, "agent");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.SIMPLE);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.FAST);
    }

    @Test
    @DisplayName("法律咨询 + MEDIUM → AGENT 通道")
    void shouldRouteConsultationMedium_toAgent() {
        IntentResult intent = IntentResult.rule(IntentType.LEGAL_CONSULTATION, 0.9, "agent");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.MEDIUM);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.AGENT);
    }

    @Test
    @DisplayName("法律咨询 + COMPLEX → AGENT 通道")
    void shouldRouteConsultationComplex_toAgent() {
        IntentResult intent = IntentResult.rule(IntentType.LEGAL_CONSULTATION, 0.9, "agent");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.COMPLEX);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.AGENT);
    }

    @Test
    @DisplayName("文书生成 + 任意复杂度 → HYBRID 通道")
    void shouldAlwaysRouteDocumentDrafting_toHybrid() {
        IntentResult intent = IntentResult.rule(IntentType.DOCUMENT_DRAFTING, 0.9, "hybrid");

        RouteDecision simple = router.decide(intent, ComplexityAssessor.ComplexityLevel.SIMPLE);
        RouteDecision medium = router.decide(intent, ComplexityAssessor.ComplexityLevel.MEDIUM);
        RouteDecision complex = router.decide(intent, ComplexityAssessor.ComplexityLevel.COMPLEX);

        assertThat(simple.channel()).isEqualTo(RouteDecision.Channel.HYBRID);
        assertThat(medium.channel()).isEqualTo(RouteDecision.Channel.HYBRID);
        assertThat(complex.channel()).isEqualTo(RouteDecision.Channel.HYBRID);
    }

    @Test
    @DisplayName("案例检索 + SIMPLE → FAST 通道（默认）")
    void shouldRouteCaseSearchSimple_toFast() {
        IntentResult intent = IntentResult.rule(IntentType.CASE_SEARCH, 0.9, "agent");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.SIMPLE);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.FAST);
    }

    @Test
    @DisplayName("案例检索 + COMPLEX → AGENT 通道")
    void shouldRouteCaseSearchComplex_toAgent() {
        IntentResult intent = IntentResult.rule(IntentType.CASE_SEARCH, 0.9, "agent");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.COMPLEX);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.AGENT);
    }

    @Test
    @DisplayName("金额计算 + MEDIUM → AGENT 通道")
    void shouldRouteCalculationMedium_toAgent() {
        IntentResult intent = IntentResult.rule(IntentType.CALCULATION, 0.9, "agent");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.MEDIUM);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.AGENT);
    }

    @Test
    @DisplayName("FAST 通道预估 Token 应该较小")
    void shouldHaveLowTokenEstimate_forFastChannel() {
        IntentResult intent = IntentResult.rule(IntentType.ARTICLE_LOOKUP, 0.9, "fast");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.SIMPLE);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.FAST);
        assertThat(decision.estimatedTokens()).isLessThan(500);
    }

    @Test
    @DisplayName("AGENT 通道预估 Token 应该较大")
    void shouldHaveHighTokenEstimate_forAgentChannel() {
        IntentResult intent = IntentResult.rule(IntentType.LEGAL_CONSULTATION, 0.9, "agent");
        RouteDecision decision = router.decide(intent, ComplexityAssessor.ComplexityLevel.COMPLEX);
        assertThat(decision.channel()).isEqualTo(RouteDecision.Channel.AGENT);
        assertThat(decision.estimatedTokens()).isGreaterThan(1000);
    }
}
