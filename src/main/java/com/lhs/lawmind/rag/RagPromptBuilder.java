package com.lhs.lawmind.rag;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.mapper.AiChatMapper;
import com.lhs.lawmind.service.SysConfigService;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RAG Prompt 构建器 —— 系统/知识/直答提示词、对话历史、改写/HyDE 提示词、免责声明。
 * 从 RagServiceImpl 拆出，集中 prompt 缓存与构建逻辑。
 */
@Slf4j
@Component
public class RagPromptBuilder {

    private static final String PROMPT_CONFIG_KEY = "rag.system_prompt";
    private static final String REWRITE_PROMPT_CONFIG_KEY = "rag.query_rewrite_prompt";
    private static final String HYDE_PROMPT_CONFIG_KEY = "rag.hyde_prompt";

    private static final String COMPLIANCE_DISCLAIMER =
            "\n\n---\n> ⚠️ 以上内容由 AI 生成，仅供参考，不构成法律建议。如涉及具体法律事务，请咨询专业律师。";

    private static final String DEFAULT_SYSTEM_PROMPT =
            "你是一个专业的中国法律咨询助手，由法律专家团队训练，精通中国法律法规体系。\n\n" +
            "你的核心职责：\n" +
            "- 基于提供的法律知识库内容，为用户提供准确、专业、易懂的法律解答\n" +
            "- 帮助用户理解复杂的法律概念、程序和权利义务\n" +
            "- 指导用户依法维权，告知可行的法律途径\n\n" +
            "行为准则（必须严格遵守）：\n" +
            "- 回答必须基于中国现行法律法规，不得凭空编造或臆测\n" +
            "- 重要法律依据必须明确引用，格式为：《法律名称》第XX条\n" +
            "- 如果你对某个法律问题不确定，必须明确说\"根据现有知识库，我无法确定\"，不得猜测\n" +
            "- 不得提供违法、违规或不道德的建议\n" +
            "- 涉及诉讼、仲裁等程序性问题时，提醒用户咨询专业律师\n" +
            "- 回答语言要专业但通俗易懂，让没有法律背景的普通用户也能理解\n\n" +
            "回答格式要求：\n" +
            "- 开头先给出简洁的结论或核心观点（2-3句话）\n" +
            "- 中间展开详细的法律分析和法条依据\n" +
            "- 结尾给出具体的操作建议或维权步骤\n" +
            "- 使用自然的段落和小标题组织内容，保持排版整洁\n" +
            "- 对于复杂问题，使用分步骤的方式说明，每一步标注序号";

    private final RagConfig ragConfig;
    private final AiChatMapper aiChatMapper;
    private final SysConfigService sysConfigService;

    private volatile String cachedSystemPrompt;
    private volatile String cachedRewritePrompt;
    private volatile String cachedHydePrompt;

    public RagPromptBuilder(RagConfig ragConfig, AiChatMapper aiChatMapper,
                            SysConfigService sysConfigService) {
        this.ragConfig = ragConfig;
        this.aiChatMapper = aiChatMapper;
        this.sysConfigService = sysConfigService;
    }

    public SystemMessage buildSystemPrompt() {
        return SystemMessage.from(getSystemPrompt());
    }

    public String getSystemPrompt() {
        if (cachedSystemPrompt != null) {
            return cachedSystemPrompt;
        }
        synchronized (this) {
            if (cachedSystemPrompt != null) {
                return cachedSystemPrompt;
            }
            try {
                com.lhs.lawmind.entity.SysConfig config = sysConfigService.selectByKey(PROMPT_CONFIG_KEY);
                if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
                    cachedSystemPrompt = config.getConfigValue();
                    log.info("System prompt loaded from sys_config");
                }
            } catch (Exception e) {
                log.warn("Failed to load system prompt from sys_config, using default: {}", e.getMessage());
            }
            if (cachedSystemPrompt == null) {
                cachedSystemPrompt = DEFAULT_SYSTEM_PROMPT;
            }
            return cachedSystemPrompt;
        }
    }

    public void refreshSystemPrompt() {
        cachedSystemPrompt = null;
        log.info("System prompt cache cleared, will reload on next request");
    }

    public String getRewritePrompt() {
        if (cachedRewritePrompt != null) {
            return cachedRewritePrompt;
        }
        synchronized (this) {
            if (cachedRewritePrompt != null) {
                return cachedRewritePrompt;
            }
            try {
                com.lhs.lawmind.entity.SysConfig config = sysConfigService.selectByKey(REWRITE_PROMPT_CONFIG_KEY);
                if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
                    cachedRewritePrompt = config.getConfigValue();
                    log.info("Rewrite prompt loaded from sys_config");
                }
            } catch (Exception e) {
                log.warn("Failed to load rewrite prompt from sys_config: {}", e.getMessage());
            }
            if (cachedRewritePrompt == null) {
                cachedRewritePrompt = "你是一个法律检索专家。请将用户的口语化法律问题改写为适合法律知识库检索的查询语句。\n\n"
                    + "规则：\n1. 保留用户原始问题的核心诉求\n2. 补充相关的正式法律术语和法条关键词\n"
                    + "3. 输出仅一行纯文本，不要有任何解释、标点或换行\n4. 如果用户问题已经是正式法律表述，原样输出";
            }
            return cachedRewritePrompt;
        }
    }

    public String getHydePrompt() {
        if (cachedHydePrompt != null) {
            return cachedHydePrompt;
        }
        synchronized (this) {
            if (cachedHydePrompt != null) {
                return cachedHydePrompt;
            }
            try {
                com.lhs.lawmind.entity.SysConfig config = sysConfigService.selectByKey(HYDE_PROMPT_CONFIG_KEY);
                if (config != null && config.getConfigValue() != null && !config.getConfigValue().isBlank()) {
                    cachedHydePrompt = config.getConfigValue();
                    log.info("HyDE prompt loaded from sys_config");
                }
            } catch (Exception e) {
                log.warn("Failed to load HyDE prompt from sys_config: {}", e.getMessage());
            }
            if (cachedHydePrompt == null) {
                cachedHydePrompt = "你是一位资深中国法律专家。请根据用户的法律问题，写一段假设性的法律分析回答（约200-400字）。\n\n"
                    + "要求：\n1. 引用相关的中国法律法规条文（如《劳动合同法》《民法典》等）\n"
                    + "2. 使用正式的法律文书语言风格\n3. 涵盖问题的核心法律要点和可能的处理方式\n"
                    + "4. 不要使用\"假设\"、\"如果\"等推测性措辞，直接以法律专家身份给出分析";
            }
            return cachedHydePrompt;
        }
    }

    /**
     * 构建基于法律知识库的用户提示词。
     */
    public String buildKnowledgeUserPrompt(String question, List<LawKnowledge> relatedKnowledge, String historyContext) {
        StringBuilder prompt = new StringBuilder();
        if (historyContext != null && !historyContext.isEmpty()) {
            prompt.append("=== 对话历史 ===\n").append(historyContext).append("\n\n");
        }
        prompt.append("=== 法律知识库内容 ===\n");
        for (LawKnowledge knowledge : relatedKnowledge) {
            prompt.append("【").append(knowledge.getTitle()).append("】\n");
            prompt.append(knowledge.getContent()).append("\n\n");
        }
        prompt.append("=== 用户问题 ===\n").append(question).append("\n");
        return prompt.toString();
    }

    /**
     * 构建大模型直接回答的用户提示词。
     */
    public String buildDirectUserPrompt(String question, String historyContext) {
        StringBuilder prompt = new StringBuilder();
        if (historyContext != null && !historyContext.isEmpty()) {
            prompt.append("=== 对话历史 ===\n").append(historyContext).append("\n\n");
        }
        prompt.append("问题：").append(question).append("\n");
        return prompt.toString();
    }

    /**
     * 构建多轮对话历史上下文（从数据库查询最近消息拼接）。
     */
    public String buildConversationHistory(Long conversationId) {
        if (conversationId == null) {
            log.debug("会话ID为空，跳过构建对话历史");
            return "";
        }
        try {
            int maxMessages = ragConfig.getMaxHistoryMessages();
            List<AiChat> historyMessages = aiChatMapper.selectRecentByConversationId(conversationId, maxMessages);

            if (historyMessages == null || historyMessages.isEmpty()) {
                log.debug("会话 {} 无历史消息", conversationId);
                return "";
            }

            StringBuilder history = new StringBuilder();
            for (AiChat chat : historyMessages) {
                history.append("用户: ").append(chat.getUserQuestion()).append("\n");
                String aiAnswer = chat.getAiAnswer();
                if (aiAnswer != null && aiAnswer.length() > 500) {
                    aiAnswer = aiAnswer.substring(0, 500) + "...";
                }
                history.append("助手: ").append(aiAnswer).append("\n\n");
            }

            log.info("构建对话历史上下文完成，会话ID: {}, 历史消息数: {}, 上下文长度: {}",
                    conversationId, historyMessages.size(), history.length());
            return history.toString();
        } catch (Exception e) {
            log.error("构建对话历史上下文失败: conversationId={}, error={}", conversationId, e.getMessage(), e);
            return "";
        }
    }

    /**
     * 为 AI 回答附加合规性声明。
     */
    public String appendComplianceDisclaimer(String answer, String source) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }
        if ("non_legal_reject".equals(source)) {
            return answer;
        }
        if (answer.endsWith(COMPLIANCE_DISCLAIMER)) {
            return answer;
        }
        return answer + COMPLIANCE_DISCLAIMER;
    }

    /**
     * 构建合规性声明文本。
     */
    public String buildComplianceDisclaimer() {
        return COMPLIANCE_DISCLAIMER;
    }
}
