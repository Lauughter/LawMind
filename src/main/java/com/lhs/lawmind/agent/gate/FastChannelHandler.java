package com.lhs.lawmind.agent.gate;

import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.service.LawKnowledgeService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 快速通道处理器。
 *
 * <p>用于简单问题的快速处理：关键词检索 + 单次 LLM 生成，不走 Agent 循环。
 * 适用于法条查询、法律知识问答等低复杂度场景。</p>
 */
@Slf4j
@Component
public class FastChannelHandler {

    private final LawKnowledgeService lawKnowledgeService;
    private final ChatLanguageModel chatLanguageModel;

    private static final String FAST_SYSTEM_PROMPT = """
            你是一位专业的中国法律智能助手，名为 LawMind。

            ## 回答要求
            1. 基于提供的法律知识库内容回答用户问题
            2. 如果知识库中有相关法条，必须引用法条原文（注明《法律名称》第X条）
            3. 如果知识库信息不足以回答，明确告知用户，不要编造
            4. 回答结构：问题分析 → 法律依据 → 具体解答 → 注意事项 → 免责声明
            5. 涉及金额计算时逐步列出公式和计算过程

            ## 免责声明
            以上内容仅供参考，不构成法律意见。具体案件请咨询专业律师。
            """;

    public FastChannelHandler(LawKnowledgeService lawKnowledgeService,
                               ChatLanguageModel chatLanguageModel) {
        this.lawKnowledgeService = lawKnowledgeService;
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * 快速处理法律问题。
     *
     * @param question 用户问题
     * @param intentType 意图类型（用于调整检索策略）
     * @return AI 生成的回答文本
     */
    public String handle(String question, IntentType intentType) {
        long startTime = System.currentTimeMillis();

        // 1. 关键词检索
        int topK = switch (intentType) {
            case ARTICLE_LOOKUP -> 10;
            case CASE_SEARCH -> 8;
            default -> 5;
        };
        List<LawKnowledge> knowledgeList = lawKnowledgeService.search(question, 1, topK);

        log.info("[FastChannel] 检索完成: results={}, elapsed={}ms",
                knowledgeList.size(), System.currentTimeMillis() - startTime);

        // 2. 构建检索上下文
        String knowledgeContext = buildKnowledgeContext(knowledgeList);

        // 3. 构建消息
        var messages = List.of(
                SystemMessage.from(FAST_SYSTEM_PROMPT),
                UserMessage.from(buildUserPrompt(question, knowledgeContext, intentType)));

        // 4. 单次 LLM 生成
        try {
            Response<dev.langchain4j.data.message.AiMessage> response =
                    chatLanguageModel.generate(messages);
            String answer = response.content().text();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[FastChannel] 回答生成完成: answerLen={}, elapsed={}ms",
                    answer != null ? answer.length() : 0, elapsed);

            return answer != null ? answer : "抱歉，生成回答时出现问题，请稍后重试。";
        } catch (Exception e) {
            log.error("[FastChannel] LLM 生成失败: error={}", e.getMessage(), e);
            return "抱歉，回答生成失败：" + e.getMessage();
        }
    }

    private String buildKnowledgeContext(List<LawKnowledge> knowledgeList) {
        if (knowledgeList.isEmpty()) {
            return "（知识库中暂无直接相关内容）";
        }
        return knowledgeList.stream()
                .map(k -> String.format("[%s] %s: %s",
                        k.getLawType() != null ? k.getLawType() : "法律知识",
                        k.getTitle() != null ? k.getTitle() : "",
                        truncate(k.getContent(), 300)))
                .collect(Collectors.joining("\n"));
    }

    private String buildUserPrompt(String question, String knowledgeContext, IntentType intentType) {
        String strategyHint = switch (intentType) {
            case ARTICLE_LOOKUP -> "用户正在查询具体法条，请直接提供法条原文内容并简要解释。";
            case CALCULATION -> "用户需要计算赔偿/金额，请逐步列出公式和计算过程。";
            case CASE_SEARCH -> "用户想了解类似案例，请参考知识库中的案例进行说明。";
            case DOCUMENT_DRAFTING -> "用户需要法律文书模板，请提供文书结构和关键条款示例。";
            case LEGAL_KNOWLEDGE -> "用户想了解法律概念，请用通俗易懂的语言解释。";
            default -> "用户在进行法律咨询，请根据知识库内容给出专业建议。";
        };

        return String.format("""
                %s

                ## 知识库检索结果
                %s

                ## 用户问题
                %s
                """, strategyHint, knowledgeContext, question);
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "...";
    }
}
