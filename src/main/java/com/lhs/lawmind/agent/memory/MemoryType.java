package com.lhs.lawmind.agent.memory;

/**
 * 记忆类型 —— 四种使用语义，决定检索策略、衰减周期和注入方式。
 */
public enum MemoryType {
    /** 用户画像：身份、角色、知识水平、长期偏好。衰减周期 180 天。 */
    USER,
    /** 反馈记忆：用户纠正、确认、偏好声明。衰减周期 90 天。 */
    FEEDBACK,
    /** 项目记忆：案件、合同审查、咨询事项。衰减周期 30 天。 */
    PROJECT,
    /** 参考记忆：外部资源指针（法条、案例）。衰减周期 60 天。 */
    REFERENCE
}
