package com.lhs.lawmind.agent.gate;

/**
 * 用户意图类型枚举（7 种）。
 */
public enum IntentType {

    /** 法条查询 — 用户想查具体法条原文 */
    ARTICLE_LOOKUP,

    /** 法律咨询 — 用户有具体纠纷或法律问题需要分析 */
    LEGAL_CONSULTATION,

    /** 金额计算 — 用户需要计算赔偿金、补偿金、抚养费等 */
    CALCULATION,

    /** 案例检索 — 用户想查找类似判例 */
    CASE_SEARCH,

    /** 文书生成 — 用户需要起草起诉状、合同、协议等法律文书 */
    DOCUMENT_DRAFTING,

    /** 法律知识问答 — 用户询问法律概念、制度、程序等一般性知识 */
    LEGAL_KNOWLEDGE,

    /** 合同审查 — 用户上传合同文件，由 Agent 分析条款合法性与公平性 */
    CONTRACT_REVIEW
}
