package com.lhs.lawmind.agent.gate;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.List;
import java.util.Map;

/**
 * 意图门控配置属性，绑定到 {@code lawmind.agent.gate} 前缀。
 */
@ConfigurationProperties(prefix = "lawmind.agent.gate")
public record IntentGateConfig(
        @DefaultValue("false") boolean ruleOnly,
        @DefaultValue DomainConfig domain,
        @DefaultValue IntentConfig intent,
        @DefaultValue ComplexityConfig complexity,
        @DefaultValue RouteConfig route,
        @DefaultValue Map<String, String> rejectResponses) {

    public record DomainConfig(
            @DefaultValue List<String> legalKeywords,
            @DefaultValue List<String> nonLegalKeywords,
            @DefaultValue("请判断以下问题是否涉及中国法律领域（包括法律咨询、法条查询、案例分析、法律知识问答、文书起草、法律程序等）。仅回答\"是\"或\"否\"。\\n问题：") String llmPrompt) {
    }

    public record IntentConfig(
            @DefaultValue List<String> articleLookupKeywords,
            @DefaultValue List<String> calculationKeywords,
            @DefaultValue List<String> caseSearchKeywords,
            @DefaultValue List<String> documentDraftingKeywords,
            @DefaultValue List<String> legalKnowledgeKeywords,
            @DefaultValue List<String> contractReviewKeywords,
            @DefaultValue("请将以下法律问题分类为以下类型之一（仅输出类型名称）：ARTICLE_LOOKUP（法条查询）、LEGAL_CONSULTATION（法律咨询）、CALCULATION（金额计算）、CASE_SEARCH（案例检索）、DOCUMENT_DRAFTING（文书起草）、LEGAL_KNOWLEDGE（法律知识问答）、CONTRACT_REVIEW（合同审查）。\\n问题：") String llmPrompt) {
    }

    public record ComplexityConfig(
            @DefaultValue("0.4") double involvedLawsWeight,
            @DefaultValue("0.2") double calculationNeededWeight,
            @DefaultValue("0.2") double questionClausesWeight,
            @DefaultValue("0.2") double procedureInvolvedWeight,
            @DefaultValue("0.35") double simpleThreshold,
            @DefaultValue("0.65") double complexThreshold,
            @DefaultValue List<String> complexLawKeywords,
            @DefaultValue List<String> procedureKeywords) {
    }

    public record RouteConfig(
            @DefaultValue("0.5") double fastAgentThreshold,
            @DefaultValue List<String> fastIntents,
            @DefaultValue List<String> agentIntents,
            @DefaultValue List<String> hybridIntents) {
    }
}
