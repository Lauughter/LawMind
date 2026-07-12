package com.lhs.lawmind.evaluation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Faithfulness（忠实度）评估器。
 * 使用 LLM-as-judge 逐句判断回答是否能从检索到的知识中推断。
 * 得分 = FAITHFUL 的句子数 / 总句子数（有实际内容的句子）。
 */
@Slf4j
public class FaithfulnessEvaluator {

    private final ChatLanguageModel model;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
            你是一个严格的法律事实核查员。我会给出一段 AI 回答和对应的法律知识原文。
            请逐句判断回答中有实质性法律内容的句子，是否能从法律知识原文中直接推断或证实。

            只评估包含法律事实、法条引用、建议或结论的句子。跳过问候语、免责声明和过渡性语句。

            请用 JSON 数组格式输出，每项包含：
            - statement: 被评估的原句
            - verdict: "FAITHFUL" 或 "UNFAITHFUL"
            - reason: 判定理由（简短）

            只输出 JSON 数组，不要输出其他内容。""";

    public FaithfulnessEvaluator(Optional<ChatLanguageModel> model) {
        this.model = model.orElse(null);
    }

    /**
     * 评估回答相对于检索知识的忠实度
     * @param answer     LLM 生成的回答
     * @param contexts   检索到的知识列表（title + content 拼接）
     * @return 忠实度得分 0.0-1.0，如果 LLM 不可用返回 0.0
     */
    public double evaluate(String answer, List<String> contexts) {
        if (model == null || answer == null || answer.isBlank() || contexts == null || contexts.isEmpty()) {
            return 0.0;
        }

        String contextText = String.join("\n---\n", contexts);
        if (contextText.length() > 6000) {
            contextText = contextText.substring(0, 6000) + "...(截断)";
        }

        String userMessage = "回答：\n" + answer + "\n\n法律知识原文：\n" + contextText;

        try {
            Response<AiMessage> response = model.generate(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(userMessage)
            );

            String json = response.content().text();
            return parseFaithfulness(json);
        } catch (Exception e) {
            log.warn("Faithfulness 评估失败: {}", e.getMessage());
            return 0.0;
        }
    }

    // ──────────── 结果解析 ────────────

    private double parseFaithfulness(String json) {
        try {
            // 提取 JSON 数组
            String cleaned = json.trim();
            if (cleaned.startsWith("```")) {
                cleaned = cleaned.replaceAll("```json\\s*", "").replaceAll("```\\s*", "");
            }

            List<Map<String, Object>> statements = mapper.readValue(cleaned,
                    new TypeReference<List<Map<String, Object>>>() {});

            if (statements.isEmpty()) return 0.0;

            long faithful = statements.stream()
                    .filter(s -> "FAITHFUL".equalsIgnoreCase((String) s.getOrDefault("verdict", "")))
                    .count();

            return (double) faithful / statements.size();
        } catch (Exception e) {
            log.debug("Faithfulness JSON 解析失败: {}", e.getMessage());
            return 0.0;
        }
    }
}
