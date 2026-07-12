package com.lhs.lawmind.agent.gate;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Layer 1 — 领域门控。
 *
 * <p>双策略判断用户问题是否属于法律领域：
 * <ol>
 *   <li>规则快速判断（&lt; 1ms）：法律核心关键词 → 通过；非法律主题词 → 拒绝</li>
 *   <li>LLM 兜底判断（仅对 ~10-15% 模糊边界问题）：轻量 prompt，二元分类"是/否"</li>
 * </ol>
 *
 * <p>规则命中率目标 ≥ 90%，LLM 兜底延迟 &lt; 500ms。</p>
 */
@Slf4j
@Component
public class DomainGate {

    private final IntentGateConfig config;
    private final ChatLanguageModel chatLanguageModel;

    /** 书名号模式：匹配《XXX》形式的法条引用 */
    private static final Pattern BOOK_TITLE_PATTERN = Pattern.compile("《.+?》");

    /** 法条引用模式：第X条/款 */
    private static final Pattern ARTICLE_PATTERN = Pattern.compile(
            "第[一二三四五六七八九十百千零\\d]+[条条款项]");

    /** 纯符号/乱码检测 */
    private static final Pattern MALFORMED_PATTERN = Pattern.compile("^[\\p{Punct}\\s\\d]{1,5}$");

    /** 敏感词列表（硬编码，不依赖配置） */
    private static final List<String> SENSITIVE_KEYWORDS = List.of(
            "色情", "暴力", "政治敏感", "赌博", "毒品", "翻墙", "VPN推荐");

    public DomainGate(IntentGateConfig config, ChatLanguageModel chatLanguageModel) {
        this.config = config;
        this.chatLanguageModel = chatLanguageModel;
    }

    /**
     * 判断用户问题是否属于法律领域。
     *
     * @param question 用户原始问题
     * @return 领域判断结果
     */
    public DomainVerdict judge(String question) {
        if (question == null || question.isBlank()) {
            return DomainVerdict.nonLegal(1.0, "空输入", "malformed");
        }

        String trimmed = question.trim();

        // 格式异常检测
        if (trimmed.length() < 2) {
            log.info("[DomainGate] 输入过短，判定为格式异常");
            return DomainVerdict.nonLegal(1.0, "输入过短", "malformed");
        }
        if (MALFORMED_PATTERN.matcher(trimmed).matches()) {
            log.info("[DomainGate] 检测到格式异常输入");
            return DomainVerdict.nonLegal(1.0, "格式异常（纯符号/数字）", "malformed");
        }

        // 敏感内容检测
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (trimmed.contains(keyword)) {
                log.info("[DomainGate] 检测到敏感内容: keyword={}", keyword);
                return DomainVerdict.nonLegal(1.0, "敏感内容", "sensitive");
            }
        }

        // 规则层 1：法律核心关键词匹配
        boolean hitLegal = false;
        String matchedLegal = null;
        for (String keyword : config.domain().legalKeywords()) {
            if (trimmed.contains(keyword)) {
                hitLegal = true;
                matchedLegal = keyword;
                break;
            }
        }

        // 规则层 1：法条/案例引用模式匹配（书名号、第X条）
        boolean hitPattern = BOOK_TITLE_PATTERN.matcher(trimmed).find()
                || ARTICLE_PATTERN.matcher(trimmed).find();

        if (hitLegal || hitPattern) {
            log.info("[DomainGate] 法律关键词或法条模式命中: keyword={}, pattern={}",
                    matchedLegal, hitPattern);
            return DomainVerdict.legal("法律关键词或法条模式命中: "
                    + (matchedLegal != null ? matchedLegal : "法条引用模式"));
        }

        // 规则层 2：非法律主题词匹配
        for (String keyword : config.domain().nonLegalKeywords()) {
            if (trimmed.contains(keyword)) {
                log.info("[DomainGate] 非法律主题词命中: keyword={}", keyword);
                return DomainVerdict.nonLegal("非法律主题词命中: " + keyword);
            }
        }

        // 规则层 3：明显非法律问题（纯英文、超短问候等）
        if (trimmed.length() < 4 && !hitLegal) {
            log.info("[DomainGate] 短输入且无法律关键词，判定为非法律");
            return DomainVerdict.nonLegal(0.8, "短输入，无法律特征", "non_legal");
        }

        // 模糊边界 — 走 LLM 兜底
        if (config.ruleOnly()) {
            log.info("[DomainGate] 纯规则模式，模糊边界默认放行");
            return DomainVerdict.legal(0.5, "纯规则模式，模糊边界默认放行");
        }

        return llmJudge(trimmed);
    }

    /**
     * LLM 兜底判断（仅对模糊边界问题调用）。
     */
    private DomainVerdict llmJudge(String question) {
        try {
            String prompt = config.domain().llmPrompt() + question;
            var messages = List.of(
                    SystemMessage.from("你是一个法律领域识别助手。仅输出[是]或[否]，不要输出其他任何内容。"),
                    UserMessage.from(prompt));

            Response<dev.langchain4j.data.message.AiMessage> response =
                    chatLanguageModel.generate(messages);
            String answer = response.content().text().trim();

            log.info("[DomainGate] LLM 兜底判断结果: answer={}", answer);

            if (answer.contains("是")) {
                return DomainVerdict.legal(0.7, "LLM 判断为法律领域");
            }
            return DomainVerdict.nonLegal(0.7, "LLM 判断为非法律领域", "non_legal");
        } catch (Exception e) {
            log.error("[DomainGate] LLM 兜底判断失败，默认通过: error={}", e.getMessage());
            // 降级策略：LLM 判断失败时默认通过（宁可错放，不可错拦）
            return DomainVerdict.legal(0.5, "LLM 判断失败，默认通过");
        }
    }
}
