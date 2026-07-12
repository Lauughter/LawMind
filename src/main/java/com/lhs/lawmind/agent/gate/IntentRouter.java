package com.lhs.lawmind.agent.gate;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Layer 3 — 路由决策器。
 *
 * <p>根据意图类型 × 复杂度矩阵决定处理通道：
 * <ul>
 *   <li>FAST — 直接检索 + 单次 LLM 生成，不走 Agent 循环</li>
 *   <li>AGENT — 多步推理 + 工具调用</li>
 *   <li>HYBRID — 模板 + 参数化 + 可选检索（文书生成）</li>
 * </ul>
 */
@Slf4j
@Component
public class IntentRouter {

    private final IntentGateConfig config;

    public IntentRouter(IntentGateConfig config) {
        this.config = config;
    }

    /**
     * 根据意图和复杂度做出路由决策。
     *
     * @param intentResult 意图分类结果
     * @param complexity   复杂度等级
     * @return 路由决策
     */
    public RouteDecision decide(IntentResult intentResult, ComplexityAssessor.ComplexityLevel complexity) {
        IntentType intentType = intentResult.intentType();
        List<String> fastIntents = config.route().fastIntents();
        List<String> agentIntents = config.route().agentIntents();
        List<String> hybridIntents = config.route().hybridIntents();
        String name = intentType.name();

        // DOCUMENT_DRAFTING → 始终 HYBRID
        if (hybridIntents.contains(name)) {
            log.info("[Router] 文书生成意图 → HYBRID 通道");
            return RouteDecision.hybrid("模板填充 + 可选检索", estimateTokens(intentType, complexity));
        }

        // 明确指定 FAST 的意图
        if (fastIntents.contains(name)) {
            if (complexity == ComplexityAssessor.ComplexityLevel.COMPLEX) {
                // 虽然意图简单但输入很复杂（如大段文字中包含法条查询需求）
                log.info("[Router] {} 意图 + COMPLEX 复杂度 → AGENT 通道（升级）", intentType);
                return RouteDecision.agent(
                        "意图简单但问题复杂，升级到 Agent 通道处理",
                        estimateTokens(intentType, ComplexityAssessor.ComplexityLevel.MEDIUM));
            }
            log.info("[Router] {} 意图 → FAST 通道", intentType);
            return RouteDecision.fast("直接检索 + 单次 LLM 生成", 300);
        }

        // 明确指定 AGENT 的意图
        if (agentIntents.contains(name)) {
            if (complexity == ComplexityAssessor.ComplexityLevel.SIMPLE) {
                // 简单法律咨询也可以降级到快速通道
                log.info("[Router] {} 意图 + SIMPLE 复杂度 → FAST 通道（降级）", intentType);
                return RouteDecision.fast("简单法律咨询，快速通道处理", 500);
            }
            log.info("[Router] {} 意图 → AGENT 通道", intentType);
            return RouteDecision.agent("多步推理 + 工具调用", estimateTokens(intentType, complexity));
        }

        // 默认：根据复杂度决定
        if (complexity == ComplexityAssessor.ComplexityLevel.SIMPLE) {
            log.info("[Router] 默认意图 + SIMPLE 复杂度 → FAST 通道");
            return RouteDecision.fast("简单问题快速通道", 400);
        }
        log.info("[Router] 默认意图 + {}} 复杂度 → AGENT 通道", complexity);
        return RouteDecision.agent("复杂问题 Agent 通道", estimateTokens(intentType, complexity));
    }

    /**
     * 预估 Token 消耗。
     */
    private int estimateTokens(IntentType intentType, ComplexityAssessor.ComplexityLevel complexity) {
        int base = switch (intentType) {
            case ARTICLE_LOOKUP, LEGAL_KNOWLEDGE -> 500;
            case CALCULATION -> 800;
            case CASE_SEARCH -> 1200;
            case LEGAL_CONSULTATION -> 1500;
            case DOCUMENT_DRAFTING -> 2000;
            case CONTRACT_REVIEW -> 3000;
        };
        double multiplier = switch (complexity) {
            case SIMPLE -> 0.6;
            case MEDIUM -> 1.0;
            case COMPLEX -> 2.0;
        };
        return (int) (base * multiplier);
    }
}
