package com.lhs.lawmind.agent.gate;

/**
 * 意图分类结果。
 *
 * @param intentType     分类后的意图类型
 * @param confidence     置信度 0.0~1.0
 * @param suggestedRoute 建议的路由通道（fast / agent / hybrid）
 * @param matchedBy      匹配方式（RULE / LLM）
 */
public record IntentResult(IntentType intentType, double confidence, String suggestedRoute, String matchedBy) {

    public static IntentResult rule(IntentType intentType, double confidence, String suggestedRoute) {
        return new IntentResult(intentType, confidence, suggestedRoute, "RULE");
    }

    public static IntentResult llm(IntentType intentType, double confidence, String suggestedRoute) {
        return new IntentResult(intentType, confidence, suggestedRoute, "LLM");
    }
}
