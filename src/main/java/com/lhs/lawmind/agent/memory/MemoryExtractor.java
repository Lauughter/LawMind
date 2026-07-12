package com.lhs.lawmind.agent.memory;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * LLM 驱动的记忆提取器 —— 从完整会话中提取四类型记忆，含冲突检测。
 */
@Slf4j
@Component
public class MemoryExtractor {

    private static final String EXTRACTION_PROMPT = """
            你是一位法律 AI 助手的记忆管理分析师。从以下对话中提取可跨会话复用的用户信息。

            ## 记忆类型

            ### USER（用户画像）
            - 用户的身份、职业、角色
            - 用户的法律知识水平
            - 用户的长期偏好（如"偏好表格"、"要求法条原文"、"通俗语言"）
            - 只提取持久性特征，不提取仅本次有效的临时偏好

            ### FEEDBACK（用户反馈）
            - 用户对 AI 回答的纠正或否定
            - 用户补充的新信息
            - 用户对某类回答方式的偏好声明

            ### PROJECT（事项/案件）
            - 本次合同审查的对象和核心发现
            - 本次法律咨询的核心问题和结论
            - 格式：标题(20字内) + 摘要(200字内) + 重要性(1-10)

            ### REFERENCE（外部参考）
            - 用户引用的或 AI 推荐的法条名称和条款号
            - 有参考价值的指导案例案号
            - 只记录引用指针，不记录全文

            ## 排除规则
            以下内容**不要提取**：
            1. 合同原文、具体金额、身份证号、手机号、银行账号
            2. 法律知识库中已有的法条原文（只需记录法条名称作为 REFERENCE）
            3. AI 的推理过程和技术细节
            4. 问候语、感谢语等无信息量的对话
            5. 没有跨会话复用价值的内容

            ## 重要性评分标准
            - USER: 角色+知识水平=8-10, 格式偏好=4-7
            - FEEDBACK: 纠正法律错误=8-10, 补充信息=4-7
            - PROJECT: 重大风险发现=8-10, 一般咨询=4-7, 简单查询=1-3
            - REFERENCE: 关键案例引用=8-10, 常用法条=4-7

            ## 对话记录
            {conversation}

            ## 输出格式
            返回 JSON（不要 markdown 代码块）：
            {
              "memories": [
                {
                  "type": "USER|FEEDBACK|PROJECT|REFERENCE",
                  "title": "简短标题",
                  "body": "记忆正文（一句话事实或摘要）",
                  "summary": "更短的摘要（可选，不超过100字）",
                  "importance": 0.8
                }
              ]
            }
            如果没有值得记忆的内容，返回 { "memories": [] }""";

    private final ChatLanguageModel chatLanguageModel;
    private final MemoryStore memoryStore;

    public MemoryExtractor(ChatLanguageModel chatLanguageModel, MemoryStore memoryStore) {
        this.chatLanguageModel = chatLanguageModel;
        this.memoryStore = memoryStore;
    }

    /**
     * 从会话中提取记忆（同步，调用方需自行异步化）。
     */
    public List<AiMemory> extract(Long userId, Long sessionId, List<ChatMessage> messages) {
        try {
            String conversationText = formatMessages(messages);
            String prompt = EXTRACTION_PROMPT.replace("{conversation}", conversationText);

            var response = chatLanguageModel.generate(
                    List.of(SystemMessage.from(prompt), UserMessage.from("请提取记忆。")));

            String json = response.content().text();
            json = stripMarkdownCodeBlock(json);

            JSONObject parsed = JSONUtil.parseObj(json);
            JSONArray arr = parsed.getJSONArray("memories");
            if (arr == null || arr.isEmpty()) return List.of();

            List<AiMemory> result = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                AiMemory memory = new AiMemory();
                memory.setUserId(userId);
                memory.setOriginSessionId(sessionId);
                memory.setType(MemoryType.valueOf(obj.getStr("type")));
                memory.setTitle(obj.getStr("title"));
                memory.setBody(obj.getStr("body"));
                memory.setSummary(obj.getStr("summary"));
                memory.setImportance(obj.getDouble("importance", 0.5));
                memory.setConfidence(0.5);
                memory.setAccessCount(0);
                result.add(memory);
            }

            // 冲突检测：与已有记忆比较
            return detectAndResolveConflicts(userId, result);

        } catch (Exception e) {
            log.error("记忆提取失败: userId={}, error={}", userId, e.getMessage());
            return List.of();
        }
    }

    private String formatMessages(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            if (msg instanceof dev.langchain4j.data.message.UserMessage) {
                sb.append("用户: ").append(msg.text()).append("\n");
            } else if (msg instanceof dev.langchain4j.data.message.AiMessage) {
                sb.append("AI: ").append(msg.text()).append("\n");
            }
        }
        String text = sb.toString();
        // 截断过长内容
        return text.length() > 8000 ? text.substring(0, 8000) + "...(截断)" : text;
    }

    private String stripMarkdownCodeBlock(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.startsWith("```json")) t = t.substring(7);
        else if (t.startsWith("```")) t = t.substring(3);
        if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        return t.trim();
    }

    /**
     * 与新记忆做语义相似度比较，检测冲突。
     * 简化实现：基于标题关键词做规则判断。
     */
    private List<AiMemory> detectAndResolveConflicts(Long userId, List<AiMemory> newMemories) {
        List<AiMemory> resolved = new ArrayList<>();
        for (AiMemory newMem : newMemories) {
            List<AiMemory> existing = memoryStore.findByUserIdAndType(userId, newMem.getType());
            boolean conflict = false;
            for (AiMemory exist : existing) {
                if (exist.getTitle() != null && newMem.getTitle() != null
                        && titleSimilar(exist.getTitle(), newMem.getTitle())) {
                    // 增强已有记忆的置信度
                    double newConf = Math.min(1.0, exist.getConfidence() + 0.1);
                    exist.setConfidence(newConf);
                    exist.setSourceSessionIds(mergeSourceIds(exist.getSourceSessionIds(),
                            String.valueOf(newMem.getOriginSessionId())));
                    memoryStore.update(exist);
                    conflict = true;
                    log.info("记忆归并: {} ← {}", exist.getTitle(), newMem.getTitle());
                    break;
                }
            }
            if (!conflict) {
                resolved.add(newMem);
            }
        }
        return resolved;
    }

    private boolean titleSimilar(String t1, String t2) {
        // 简化的标题相似度：共同字符数 > 较短短长的 50%
        int common = 0;
        for (char c : t1.toCharArray()) {
            if (t2.indexOf(c) >= 0) common++;
        }
        int minLen = Math.min(t1.length(), t2.length());
        return minLen > 0 && (double) common / minLen > 0.5;
    }

    private String mergeSourceIds(String existing, String newId) {
        if (existing == null || existing.isBlank()) return "[" + newId + "]";
        if (existing.contains(newId)) return existing;
        return existing.substring(0, existing.length() - 1) + "," + newId + "]";
    }
}
