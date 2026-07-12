package com.lhs.lawmind.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 个人信息（PII）检测与脱敏工具
 * 支持中国大陆身份证号、手机号、银行卡号、邮箱等常见敏感信息
 */
public final class PiiUtil {

    private PiiUtil() {}

    // ── 检测正则 ──

    /** 18位身份证（前17位数字 + 最后1位数字或X） */
    private static final Pattern ID_CARD_18 = Pattern.compile(
            "[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[\\dXx]");

    /** 15位身份证（全数字） */
    private static final Pattern ID_CARD_15 = Pattern.compile(
            "[1-9]\\d{7}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}");

    /** 中国大陆手机号（13/14/15/16/17/18/19 开头） */
    private static final Pattern PHONE = Pattern.compile(
            "(?<!\\d)1[3-9]\\d{9}(?!\\d)");

    /** 座机号码（含区号） */
    private static final Pattern LANDLINE = Pattern.compile(
            "(?<!\\d)0\\d{2,3}-?\\d{7,8}(?!\\d)");

    /** 银行卡号（16-19位数字） */
    private static final Pattern BANK_CARD = Pattern.compile(
            "(?<!\\d)\\d{16,19}(?!\\d)");

    /** 电子邮箱 */
    private static final Pattern EMAIL = Pattern.compile(
            "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");

    /** IPv4 地址 */
    private static final Pattern IPV4 = Pattern.compile(
            "(?<!\\d)((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)(?!\\d)");

    // ── 校验码权重（身份证） ──
    private static final int[] ID_WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CHECK = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    /**
     * 检测文本中是否含有任何 PII
     */
    public static boolean hasPii(String text) {
        if (text == null || text.isEmpty()) return false;
        return ID_CARD_18.matcher(text).find()
                || ID_CARD_15.matcher(text).find()
                || PHONE.matcher(text).find()
                || EMAIL.matcher(text).find()
                || BANK_CARD.matcher(text).find();
    }

    /**
     * 返回文本中匹配到的所有 PII 片段（最多20条）
     */
    public static List<String> detectPii(String text) {
        List<String> matches = new ArrayList<>();
        if (text == null || text.isEmpty()) return matches;

        addMatches(matches, ID_CARD_18.matcher(text));
        addMatches(matches, ID_CARD_15.matcher(text));
        addMatches(matches, PHONE.matcher(text));
        addMatches(matches, EMAIL.matcher(text));
        addMatches(matches, BANK_CARD.matcher(text));
        return matches;
    }

    /**
     * 将文本中的 PII 替换为掩码，如 138****1234
     */
    public static String maskPii(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;

        // 身份证 → 前3后4保留
        result = ID_CARD_18.matcher(result).replaceAll(m -> maskMiddle(m.group(), 3, 4));
        result = ID_CARD_15.matcher(result).replaceAll(m -> maskMiddle(m.group(), 3, 4));

        // 手机号 → 前3后4保留
        result = PHONE.matcher(result).replaceAll(m -> maskMiddle(m.group(), 3, 4));

        // 座机 → 保留区号，其余掩码
        result = LANDLINE.matcher(result).replaceAll(m -> {
            String s = m.group().replace("-", "");
            return s.substring(0, 3) + "****" + s.substring(s.length() - 2);
        });

        // 银行卡 → 前4后4保留
        result = BANK_CARD.matcher(result).replaceAll(m -> {
            String filtered = m.group();
            if (isLikelyIdOrPhone(filtered)) return filtered; // 避免误伤身份证/手机号（已在上面处理）
            return maskMiddle(filtered, 4, 4);
        });

        // 邮箱 → user部分掩码
        result = EMAIL.matcher(result).replaceAll(m -> {
            String email = m.group();
            int at = email.indexOf('@');
            if (at <= 2) return "***" + email.substring(at);
            return email.substring(0, 2) + "***" + email.substring(at);
        });

        // IPv4 → 后两段掩码
        result = IPV4.matcher(result).replaceAll(m -> {
            String ip = m.group();
            int lastDot = ip.lastIndexOf('.');
            int prevDot = ip.lastIndexOf('.', lastDot - 1);
            return ip.substring(0, prevDot + 1) + "*.*";
        });

        return result;
    }

    /**
     * 验证18位身份证校验码是否正确（可选严格模式）
     */
    public static boolean isValidIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) return false;
        if (!ID_CARD_18.matcher(idCard).matches()) return false;
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * ID_WEIGHT[i];
        }
        char expected = ID_CHECK[sum % 11];
        return Character.toUpperCase(idCard.charAt(17)) == expected;
    }

    // ── helpers ──

    private static void addMatches(List<String> list, java.util.regex.Matcher m) {
        while (m.find() && list.size() < 20) {
            list.add(m.group());
        }
    }

    private static String maskMiddle(String s, int keepPrefix, int keepSuffix) {
        if (s.length() <= keepPrefix + keepSuffix) return s;
        StringBuilder sb = new StringBuilder();
        sb.append(s, 0, keepPrefix);
        sb.append("*".repeat(s.length() - keepPrefix - keepSuffix));
        sb.append(s, s.length() - keepSuffix, s.length());
        return sb.toString();
    }

    /** 银行卡匹配前检查是否已被身份证或手机号匹配覆盖 */
    private static boolean isLikelyIdOrPhone(String s) {
        if (s.length() == 18 && ID_CARD_18.matcher(s).matches()) return true;
        if (s.length() == 15 && ID_CARD_15.matcher(s).matches()) return true;
        if (s.length() == 11 && PHONE.matcher(s).matches()) return true;
        return false;
    }
}
