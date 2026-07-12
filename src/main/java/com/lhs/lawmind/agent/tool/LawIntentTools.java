package com.lhs.lawmind.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import com.lhs.lawmind.utils.IntentClassifier;
import com.lhs.lawmind.utils.LegalQueryExpander;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LawIntentTools 是一个专门针对法律领域的工具类，提供了两个核心功能：
 * 1. classifyLegalIntent：分析用户问题的法律意图类型，帮助后续检索更有针对性。
 * 2. expandLegalQuery：对用户的原始查询进行法律术语扩展，生成更适合法律检索的查询语句。
 * 这些工具通过调用 IntentClassifier 和 LegalQueryExpander 来实现具体的功能逻辑，并且在执行过程中添加了错误处理和日志记录，确保在实际应用中能够稳定运行并提供有用的反馈信息。
 */
@Slf4j
@Component
public class LawIntentTools {

    private final IntentClassifier intentClassifier;
    private final LegalQueryExpander legalQueryExpander;

    public LawIntentTools(IntentClassifier intentClassifier,
                          LegalQueryExpander legalQueryExpander) {
        this.intentClassifier = intentClassifier;
        this.legalQueryExpander = legalQueryExpander;
    }

    /**
     * 分析用户问题的法律意图类型，返回意图类别包括：LEGAL_CONSULTATION（法律咨询）、ARTICLE_LOOKUP（法条查询）、CASE_SEARCH（案例检索）、CALCULATION（金额计算）。
     * 根据不同的意图类型，提供相应的检索策略建议，如增加精确匹配权重、检索相似案例、关注赔偿标准等，以帮助后续检索更有针对性和效率。
     * @param question 用户的原始问题文本，可能包含口语化表达，需要通过意图分类来确定用户的具体需求类型，以便后续提供更精准的法律信息和建议。
     * @return 一个字符串，包含意图分析结果、建议的检索数量、是否需要深度检索以及针对不同意图的策略建议，帮助用户理解系统对其问题的理解和后续处理方向。
     */
    @Tool("分析用户问题的法律意图类型。" +
          "返回意图类别包括：LEGAL_CONSULTATION（法律咨询）、ARTICLE_LOOKUP（法条查询）、" +
          "CASE_SEARCH（案例检索）、CALCULATION（金额计算）。" +
          "用于帮助后续检索更有针对性。")
    public String classifyLegalIntent(
            @P("用户的原始问题文本") String question) {
        try {
            IntentClassifier.Intent intent = intentClassifier.classify(question);
            int topK = intentClassifier.adjustTopK(intent, 15);
            boolean deepRetrieval = intentClassifier.useDeepRetrieval(intent);

            String suggestion = switch (intent) {
                case ARTICLE_LOOKUP -> "建议直接搜索具体法律条文，加重精确匹配权重";
                case CASE_SEARCH -> "建议检索相似案例和判例";
                case CALCULATION -> "建议检索赔偿标准和计算公式相关的法律条文";
                case LEGAL_CONSULTATION -> "建议走完整RAG检索流程，全面获取法律依据";
            };

            return String.format("""
                    [意图分析]
                    意图类别：%s
                    建议检索数量：%d
                    是否需要深度检索：%s
                    策略建议：%s
                    """,
                    intent.name(),
                    topK,
                    deepRetrieval ? "是" : "否",
                    suggestion
            );
        } catch (Exception e) {
            log.error("[Agent Tool] classifyLegalIntent 执行失败: question={}", question, e);
            return "[Tool 错误] 意图分类失败：" + e.getMessage();
        }
    }

    /**
     * 对用户的原始查询进行法律术语扩展，补充同义词、相关法条表述，生成更适合法律检索的查询语句。适用于口语化表达的标准化。
     * @param originalQuery
     * @return
     */
    @Tool("对用户的原始查询进行法律术语扩展，补充同义词、相关法条表述，" +
          "生成更适合法律检索的查询语句。适用于口语化表达的标准化。")
    public String expandLegalQuery(
            @P("用户的原始查询文本") String originalQuery) {
        try {
            String expanded = legalQueryExpander.expandQuery(originalQuery);
            if (expanded.equals(originalQuery)) {
                return "[查询扩展] 未匹配到可扩展的口语化表达，使用原始查询即可。";
            }
            return "[查询扩展结果]\n原始查询：" + originalQuery
                    + "\n扩展后查询：" + expanded;
        } catch (Exception e) {
            log.error("[Agent Tool] expandLegalQuery 执行失败: query={}", originalQuery, e);
            return "[Tool 错误] 查询扩展失败：" + e.getMessage();
        }
    }
}
