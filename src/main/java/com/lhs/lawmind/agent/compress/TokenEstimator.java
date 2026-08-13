package com.lhs.lawmind.agent.compress;

import dev.langchain4j.data.message.ChatMessage;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Token 估算器 —— 中英混合文本的 token 数估算。
 * 中文约 1.2 字符/token，英文约 1.3 单词/token，其他字符约 0.25 token/字符。
 * 校准（P2-12）：中文从 1.5 字符/token 下调至 1.2，避免低估导致上下文超限；
 * 取略保守值以支撑压缩/截断决策（宁可稍多估算，不可溢出模型上下文）。
 */
public class TokenEstimator {

    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff\\u3400-\\u4dbf]");
    private static final Pattern ENGLISH_WORD_PATTERN = Pattern.compile("[a-zA-Z]+");

    /**
     * 估算单段文本的 token 数。
     */
    public int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int chineseChars = 0;
        Matcher chineseMatcher = CHINESE_PATTERN.matcher(text);
        while (chineseMatcher.find()) {
            chineseChars++;
        }

        int englishWords = 0;
        Matcher englishMatcher = ENGLISH_WORD_PATTERN.matcher(text);
        while (englishMatcher.find()) {
            englishWords++;
        }

        int otherChars = text.length() - chineseChars;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z') {
                otherChars--;
            }
        }

        return (int) Math.ceil(chineseChars / 1.2 + englishWords * 1.3 + otherChars * 0.25);
    }

    /**
     * 估算消息列表的总 token 数。
     */
    public int estimate(List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ChatMessage msg : messages) {
            if (msg.text() != null) {
                total += estimate(msg.text());
            }
        }
        return total;
    }
}
