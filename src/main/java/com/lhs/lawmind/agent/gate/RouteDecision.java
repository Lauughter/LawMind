package com.lhs.lawmind.agent.gate;

/**
 * 路由决策结果。
 *
 * @param channel        处理通道
 * @param strategy       处理策略描述
 * @param estimatedTokens 预估 Token 消耗
 */
public record RouteDecision(Channel channel, String strategy, int estimatedTokens) {

    public enum Channel {
        /** 快速通道：直接检索 + 单次 LLM 生成，不走 Agent 循环 */
        FAST,
        /** Agent 通道：多步推理 + 工具调用 */
        AGENT,
        /** 混合通道：模板 + 参数化 + 可选检索（用于文书生成） */
        HYBRID,
        /** 拒绝通道：直接返回拒绝响应，零 LLM 调用 */
        REJECT
    }

    public static RouteDecision fast(String strategy, int estimatedTokens) {
        return new RouteDecision(Channel.FAST, strategy, estimatedTokens);
    }

    public static RouteDecision agent(String strategy, int estimatedTokens) {
        return new RouteDecision(Channel.AGENT, strategy, estimatedTokens);
    }

    public static RouteDecision hybrid(String strategy, int estimatedTokens) {
        return new RouteDecision(Channel.HYBRID, strategy, estimatedTokens);
    }

    public static RouteDecision reject(String strategy) {
        return new RouteDecision(Channel.REJECT, strategy, 0);
    }
}
