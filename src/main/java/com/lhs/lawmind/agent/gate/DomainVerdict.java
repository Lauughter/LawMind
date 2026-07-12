package com.lhs.lawmind.agent.gate;

/**
 * 领域门控判断结果。
 *
 * @param isLegal   是否属于法律领域
 * @param confidence 置信度 0.0~1.0
 * @param reason    判断依据简述
 * @param category  分类标签（legal / non_legal / ambiguous / sensitive）
 */
public record DomainVerdict(boolean isLegal, double confidence, String reason, String category) {

    public static DomainVerdict legal(String reason) {
        return new DomainVerdict(true, 0.95, reason, "legal");
    }

    public static DomainVerdict legal(double confidence, String reason) {
        return new DomainVerdict(true, confidence, reason, "legal");
    }

    public static DomainVerdict nonLegal(String reason) {
        return new DomainVerdict(false, 0.95, reason, "non_legal");
    }

    public static DomainVerdict nonLegal(double confidence, String reason, String category) {
        return new DomainVerdict(false, confidence, reason, category);
    }
}
