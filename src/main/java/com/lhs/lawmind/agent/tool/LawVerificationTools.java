package com.lhs.lawmind.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import com.lhs.lawmind.service.SimilarQuestionService;
import com.lhs.lawmind.entity.SimilarQuestion;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LawVerificationTools 是一个专门针对法律问答验证的工具类，提供了两个核心功能：
 * 1. searchSimilarQuestions：检索历史上已解答的相似问题，返回匹配的问答对。如果找到匹配的历史回答，可以复用或参考其中的法律依据。
 * 2. verifyCitation：对答案中引用的法条进行核实提示。返回每个引用的验证状态，提醒LLM在输出前核对准确性。
 * 这些工具通过调用 SimilarQuestionService 来实现具体的相似问题检索逻辑，并且在执行过程中添加了错误处理和日志记录，确保在实际应用中能够稳定运行并提供有用的反馈信息，帮助LLM提高法律问答的准确性和可靠性。
 */
@Slf4j
@Component
public class LawVerificationTools {

    private final SimilarQuestionService similarQuestionService;

    public LawVerificationTools(SimilarQuestionService similarQuestionService) {
        this.similarQuestionService = similarQuestionService;
    }

    @Tool("检索历史上已解答的相似问题，返回匹配的问答对。" +
          "如果找到匹配的历史回答，可以复用或参考其中的法律依据。")
    public String searchSimilarQuestions(
            @P("用户当前问题文本") String question) {
        try {
            SimilarQuestion similar = similarQuestionService.searchSimilarQuestion(question);

            if (similar == null) {
                return "[相似问题] 未找到高匹配度的历史问题，需要通过知识库检索获取信息。";
            }

            return String.format("""
                    [相似问题匹配]
                    历史问题：%s
                    历史回答：%s
                    历史访问次数：%d
                    关联知识点：%s
                    注意事项：以上为历史回答，请核实其中引用的法条是否仍然有效。
                    """,
                    similar.getQuestion(),
                    similar.getAnswer(),
                    similar.getVisitCount() != null ? similar.getVisitCount() : 0,
                    similar.getKnowledgeIds() != null ? similar.getKnowledgeIds() : "无"
            );
        } catch (Exception e) {
            log.error("[Agent Tool] searchSimilarQuestions 执行失败: question={}", question, e);
            return "[Tool 错误] 相似问题检索失败：" + e.getMessage();
        }
    }

    @Tool("对答案中引用的法条进行核实提示。" +
          "返回每个引用的验证状态，提醒LLM在输出前核对准确性。")
    public String verifyCitation(
            @P("需要核实的法条引用文本，如'根据《劳动合同法》第三十九条'")
            String citation,
            @P("生成答案时使用的检索结果原文，用于交叉验证引用是否在其中有依据")
            String sourceText) {
        if (citation == null || citation.isBlank()) {
            return "[引用校验] 未提供需要校验的引用内容。";
        }

        String normalizedCitation = citation.replaceAll("[《》根据]", "").trim();
        boolean found = sourceText != null && sourceText.contains(normalizedCitation);

        if (found) {
            return String.format("""
                    [引用校验] 通过
                    引用：%s
                    在检索结果中已找到对应原文依据，可以输出该引用并标注来源。
                    """, citation);
        } else {
            return String.format("""
                    [引用校验] 未通过
                    引用：%s
                    在检索结果中未找到对应原文依据。
                    请谨慎输出该引用，如不确定请标注"待核实"或删除该引用。
                    """, citation);
        }
    }
}
