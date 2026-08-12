package com.lhs.lawmind.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * LawVerificationTools 用于法律问答验证，提供 verifyCitation 功能：
 * 对答案中引用的法条进行核实提示，返回每个引用的验证状态，提醒LLM在输出前核对准确性。
 * 通过交叉比对检索结果原文与引用，帮助 LLM 提高法律问答的准确性和可靠性。
 */
@Slf4j
@Component
public class LawVerificationTools {

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
