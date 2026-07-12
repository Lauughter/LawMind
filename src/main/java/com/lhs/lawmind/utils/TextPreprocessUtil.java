package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * 文本预处理工具类
 * 用于问题文本的清洗、去重、标准化以及MD5生成
 */
@Slf4j
public class TextPreprocessUtil {

    // 匹配特殊字符的正则表达式
    private static final Pattern SPECIAL_CHARS_PATTERN = Pattern.compile("[^\\u4e00-\\u9fa5a-zA-Z0-9\\s]");

    // 匹配多余空格的正则表达式
    private static final Pattern MULTIPLE_SPACES_PATTERN = Pattern.compile("\\s+");

    // 常见语气词和无意义前缀/后缀
    private static final String[] COMMON_TONE_WORDS = {
        "请问", "我想知道", "我想问", "请问一下", "想请问", "麻烦问一下", "想知道", "请问您",
        "谢谢", "谢谢了", "多谢", "感谢", "非常感谢", "谢谢解答", "谢谢回答",
        "您好", "你好", "嗨", "嗨喽", "哈喽"
    };

    // 常见法律术语标准化映射
    private static final String[][] LEGAL_TERMS_MAPPING = {
        {"试用期", "试用期"}, {"正式工资", "正式工资"}, {"加班费", "加班费"},
        {"工伤", "工伤"}, {"劳动合同", "劳动合同"}, {"社保", "社会保险"},
        {"五险一金", "社会保险和住房公积金"}, {"裁员", "经济性裁员"},
        {"辞职", "解除劳动合同"}, {"辞退", "解除劳动合同"},
        {"赔偿金", "经济补偿"}, {"补偿金", "经济补偿"}
    };

    /**
     * 预处理问题文本
     * 1. 去除特殊字符
     * 2. 统一句式（去除多余空格）
     * 3. 转为小写（对于英文部分）
     * 4. 去除语气词和无意义前缀/后缀
     * 5. 标准化法律术语
     *
     * @param question 原始问题文本
     * @return 预处理后的文本
     */
    public static String preprocess(String question) {
        if (question == null || question.trim().isEmpty()) {
            log.warn("预处理的问题文本为空");
            return "";
        }

        log.debug("开始预处理问题文本：{}", question);

        // 1. 去除首尾空格
        String processed = question.trim();

        // 2. 去除语气词和无意义前缀/后缀
        for (String toneWord : COMMON_TONE_WORDS) {
            if (processed.startsWith(toneWord)) {
                // 去除开头的语气词
                processed = processed.substring(toneWord.length()).trim();
            }
            if (processed.endsWith(toneWord)) {
                // 去除结尾的语气词
                processed = processed.substring(0, processed.length() - toneWord.length()).trim();
            }
        }

        // 3. 标准化法律术语
        for (String[] mapping : LEGAL_TERMS_MAPPING) {
            String original = mapping[0];// 原始术语
            String standard = mapping[1];// 标准术语
            processed = processed.replace(original, standard);
        }

        // 4. 去除特殊字符，保留中文、英文、数字和空格
        processed = SPECIAL_CHARS_PATTERN.matcher(processed).replaceAll("");

        // 5. 统一多个空格为单个空格
        processed = MULTIPLE_SPACES_PATTERN.matcher(processed).replaceAll(" ");

        // 6. 转为小写（对于英文部分）
        processed = processed.toLowerCase();

        log.debug("预处理完成：{} -> {}", question, processed);
        return processed;
    }

    /**
     * 生成文本的MD5值
     *
     * @param text 输入文本
     * @return MD5值（32位小写）
     */
    public static String generateMD5(String text) {
        if (text == null || text.isEmpty()) {
            log.warn("生成MD5的文本为空");
            return "";
        }

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            String md5 = sb.toString();
            log.debug("生成MD5：{} -> {}", text, md5);
            return md5;
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5算法不存在", e);
            throw new com.lhs.lawmind.common.BusinessException(500, "文本预处理失败");
        }
    }

    /**
     * 预处理并生成MD5（便捷方法）
     *
     * @param question 原始问题
     * @return 包含预处理后文本和MD5的结果
     */
    public static PreprocessResult preprocessAndGenerateMD5(String question) {
        String processed = preprocess(question);
        String md5 = generateMD5(processed);
        return new PreprocessResult(processed, md5);
    }

    /**
     * 预处理结果类
     */
    public static class PreprocessResult {
        private final String processedText;
        private final String md5;

        public PreprocessResult(String processedText, String md5) {
            this.processedText = processedText;
            this.md5 = md5;
        }

        public String getProcessedText() {
            return processedText;
        }

        public String getMd5() {
            return md5;
        }
    }
}
