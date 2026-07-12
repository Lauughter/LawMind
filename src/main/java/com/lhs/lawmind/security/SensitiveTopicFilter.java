package com.lhs.lawmind.security;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 敏感话题检测与过滤
 * 维护分类敏感词库，在用户问题进入 RAG 管道前拦截违规内容
 */
public final class SensitiveTopicFilter {

    private SensitiveTopicFilter() {}

    /**
     * 敏感话题分类及其关键词（仅匹配，不含 LLM 调用）
     */
    private static final Map<String, Set<String>> CATEGORIES = new LinkedHashMap<>();

    static {
        CATEGORIES.put("national_security", Set.of(
                "颠覆国家", "分裂国家", "泄露国家秘密", "间谍", "策反",
                "武装叛乱", "恐怖主义", "恐怖组织", "恐怖活动", "极端主义",
                "煽动颠覆", "危害国家安全", "政治敏感"
        ));
        CATEGORIES.put("violence", Set.of(
                "制造炸弹", "如何杀人", "买枪", "自制武器", "爆炸物制作",
                "如何实施犯罪", "犯罪教程", "杀人方法", "投毒方法",
                "雇凶", "买凶", "砍人", "绑架", "勒索方法"
        ));
        CATEGORIES.put("pornography_gambling_drugs", Set.of(
                "色情", "淫秽", "招嫖", "卖淫", "嫖娼",
                "赌博技巧", "赌场", "赌博网站", "出千", "洗钱",
                "吸毒", "毒品", "贩毒", "冰毒", "大麻", "海洛因",
                "制毒", "种毒", "买毒品"
        ));
        CATEGORIES.put("self_harm", Set.of(
                "自杀方法", "如何自杀", "怎么死", "自残方法",
                "割腕", "跳楼", "上吊", "安眠药自杀", "烧炭"
        ));
        CATEGORIES.put("fraud_methods", Set.of(
                "诈骗方法", "如何骗", "骗保", "骗贷款", "造假证",
                "伪造", "冒充", "盗刷", "信用卡套现", "虚假发票"
        ));
        CATEGORIES.put("medical_claims", Set.of(
                "我得了什么病", "诊断", "治疗方案", "吃什么药", "开药方",
                "能治好吗", "严重吗", "会死吗", "传染吗"
        ));
    }

    /**
     * 过滤结果
     */
    public record FilterResult(boolean blocked, String reason, String category) {
        public static FilterResult pass() {
            return new FilterResult(false, null, null);
        }
        public static FilterResult block(String reason, String category) {
            return new FilterResult(true, reason, category);
        }
    }

    /**
     * 检测用户问题是否触发敏感话题，返回过滤结果
     */
    public static FilterResult filter(String question) {
        if (question == null || question.isBlank()) {
            return FilterResult.pass();
        }
        String lower = question.toLowerCase();
        for (var entry : CATEGORIES.entrySet()) {
            String category = entry.getKey();
            for (String keyword : entry.getValue()) {
                if (lower.contains(keyword)) {
                    String reason = switch (category) {
                        case "self_harm" -> "涉及自我伤害内容，建议寻求专业心理帮助";
                        case "medical_claims" -> "涉及医疗诊断建议，请咨询专业医生";
                        default -> "抱歉，我无法回答此类问题。如果您有法律相关问题，我很乐意帮助。";
                    };
                    return FilterResult.block(reason, category);
                }
            }
        }
        return FilterResult.pass();
    }
}
