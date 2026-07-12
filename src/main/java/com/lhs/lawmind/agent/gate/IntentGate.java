package com.lhs.lawmind.agent.gate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 意图门控主控器。
 *
 * <p>串联三层门控流水线：
 * <ol>
 *   <li>DomainGate — 领域判断（是否法律问题）</li>
 *   <li>IntentClassifierEnhanced — 意图细分（哪种法律问题）</li>
 *   <li>ComplexityAssessor + IntentRouter — 复杂度评估 + 路由决策</li>
 * </ol>
 *
 * <p>降级策略：门控异常时自动降级到 Agent 通道，宁可慢，不可拦。</p>
 */
@Slf4j
@Component
public class IntentGate {

    private final DomainGate domainGate;
    private final IntentClassifierEnhanced intentClassifier;
    private final ComplexityAssessor complexityAssessor;
    private final IntentRouter router;
    private final IntentGateConfig config;

    public IntentGate(DomainGate domainGate,
                      IntentClassifierEnhanced intentClassifier,
                      ComplexityAssessor complexityAssessor,
                      IntentRouter router,
                      IntentGateConfig config) {
        this.domainGate = domainGate;
        this.intentClassifier = intentClassifier;
        this.complexityAssessor = complexityAssessor;
        this.router = router;
        this.config = config;
    }

    /**
     * 处理用户问题，返回门控结果。
     *
     * @param question 用户原始问题
     * @return 门控结果（接受 + 路由决策 / 拒绝 + 响应文本）
     */
    public GateResult process(String question) {
        long startTime = System.currentTimeMillis();

        try {
            // Layer 1: 领域门控
            DomainVerdict verdict = domainGate.judge(question);
            if (!verdict.isLegal()) {
                String rejectResponse = buildRejectResponse(verdict, question);
                long elapsed = System.currentTimeMillis() - startTime;
                log.info("[IntentGate] 拒绝: reason={}, elapsed={}ms", verdict.reason(), elapsed);
                return GateResult.reject(verdict, rejectResponse);
            }

            // Layer 2: 意图细分
            IntentResult intentResult = intentClassifier.classify(question);

            // Layer 3: 复杂度评估 + 路由决策
            ComplexityAssessor.ComplexityLevel complexity = complexityAssessor.assess(question);
            RouteDecision route = router.decide(intentResult, complexity);

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[IntentGate] 接受: intent={}, channel={}, complexity={}, elapsed={}ms",
                    intentResult.intentType(), route.channel(), complexity, elapsed);

            return GateResult.accept(verdict, intentResult, route);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[IntentGate] 门控异常，降级到 Agent 通道: error={}, elapsed={}ms",
                    e.getMessage(), elapsed);

            DomainVerdict fallbackVerdict = DomainVerdict.legal(0.3, "门控异常降级");
            IntentResult fallbackIntent = IntentResult.rule(
                    IntentType.LEGAL_CONSULTATION, 0.3, "agent");
            RouteDecision fallbackRoute = RouteDecision.agent(
                    "门控异常降级到 Agent 通道", 1500);
            return GateResult.accept(fallbackVerdict, fallbackIntent, fallbackRoute);
        }
    }

    /**
     * 根据领域判断结果构建分级拒绝响应。
     */
    private String buildRejectResponse(DomainVerdict verdict, String question) {
        return switch (verdict.category()) {
            case "non_legal" -> {
                boolean borderline = question != null
                        && config.domain().legalKeywords().stream().anyMatch(question::contains);
                if (borderline) {
                    yield config.rejectResponses()
                            .getOrDefault("borderline-non-legal",
                                    "您好！请就具体的法律问题向我咨询。");
                }
                yield config.rejectResponses()
                        .getOrDefault("completely-unrelated",
                                "您好！我是 LawMind 法律智能助手，请提出法律相关问题。");
            }
            case "malformed" -> config.rejectResponses()
                    .getOrDefault("malformed-input",
                            "您输入的内容格式不太规范，请用清晰的中文描述法律问题。");
            case "sensitive" -> config.rejectResponses()
                    .getOrDefault("sensitive-content",
                            "您的问题涉及敏感内容，请重新表述法律问题。");
            default -> config.rejectResponses()
                    .getOrDefault("completely-unrelated",
                            "您好！我是 LawMind 法律智能助手，请提出法律相关问题。");
        };
    }
}
