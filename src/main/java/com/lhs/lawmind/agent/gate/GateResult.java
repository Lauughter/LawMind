package com.lhs.lawmind.agent.gate;

/**
 * 意图门控总结果。
 *
 * @param accepted       是否接受该问题（通过门控）
 * @param domainVerdict  领域判断结果
 * @param intentResult   意图分类结果（accepted=true 时有值）
 * @param routeDecision  路由决策（accepted=true 时有值）
 * @param rejectResponse 拒绝响应文本（accepted=false 时有值）
 */
public record GateResult(
        boolean accepted,
        DomainVerdict domainVerdict,
        IntentResult intentResult,
        RouteDecision routeDecision,
        String rejectResponse) {

    public static GateResult accept(DomainVerdict domainVerdict, IntentResult intentResult,
                                     RouteDecision routeDecision) {
        return new GateResult(true, domainVerdict, intentResult, routeDecision, null);
    }

    public static GateResult reject(DomainVerdict domainVerdict, String rejectResponse) {
        return new GateResult(false, domainVerdict, null, null, rejectResponse);
    }
}
