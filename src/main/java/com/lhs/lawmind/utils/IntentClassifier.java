package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 用户意图分类器
 *
 * <p>将用户问题分类到不同意图，用于路由到不同的 RAG 处理策略：</p>
 * <ul>
 *   <li>LEGAL_CONSULTATION - 法律咨询（默认走完整 RAG 管道）</li>
 *   <li>ARTICLE_LOOKUP - 法条查询（加重精确匹配权重）</li>
 *   <li>CASE_SEARCH - 案例检索（查相似判例）</li>
 *   <li>CALCULATION - 法律金额计算</li>
 * </ul>
 *
 * <p>实现方式：关键词 + 规则匹配（零延迟，不消耗 LLM Token）</p>
 */
@Slf4j
@Component
public class IntentClassifier {

    public enum Intent {
        LEGAL_CONSULTATION,
        ARTICLE_LOOKUP,
        CASE_SEARCH,
        CALCULATION
    }

    private static final Map<String[], Intent> INTENT_RULES = new LinkedHashMap<>();

    static {
        // 法条查询相关关键词
        INTENT_RULES.put(new String[]{
                "第几条规定", "第几条", "法条原文", "法条内容", "哪一条法律",
                "法律条文", "查法条", "哪个法条", "什么法条", "法条是",
                "法律规定是什么", "法律如何规定", "法律怎么规定"
        }, Intent.ARTICLE_LOOKUP);

        // 计算相关关键词
        INTENT_RULES.put(new String[]{
                "赔偿多少钱", "赔偿多少", "赔多少钱", "赔偿金",
                "赔偿标准", "赔偿金额", "计算赔偿", "赔多少",
                "工伤赔偿", "经济补偿金", "加班费怎么算", "诉讼费多少"
        }, Intent.CALCULATION);

        // 案例检索相关关键词
        INTENT_RULES.put(new String[]{
                "有没有类似案例", "类似案例", "判例", "判决案例",
                "类似案件", "别人怎么判", "参考案例", "案例检索",
                "有没有判过", "法院怎么判", "胜诉率"
        }, Intent.CASE_SEARCH);
    }

    /**
     * 分类用户问题意图
     *
     * @param question 用户问题
     * @return Intent 枚举，默认 LEGAL_CONSULTATION
     */
    public Intent classify(String question) {
        if (question == null || question.isBlank()) {
            return Intent.LEGAL_CONSULTATION;
        }

        for (Map.Entry<String[], Intent> entry : INTENT_RULES.entrySet()) {
            for (String keyword : entry.getKey()) {
                if (question.contains(keyword)) {
                    log.info("意图分类: {} -> {}", entry.getValue(), question.substring(0, Math.min(50, question.length())));
                    return entry.getValue();
                }
            }
        }

        log.debug("意图分类: 默认 LEGAL_CONSULTATION");
        return Intent.LEGAL_CONSULTATION;
    }

    /**
     * 根据意图返回检索策略配置
     *
     * @param intent 用户意图
     * @param defaultTopK 默认 Top-K 值
     * @return 调整后的 Top-K 值（法条查询加量、计算类减量等）
     */
    public int adjustTopK(Intent intent, int defaultTopK) {
        return switch (intent) {
            case ARTICLE_LOOKUP -> Math.max(defaultTopK + 5, 15);
            case CASE_SEARCH -> Math.max(defaultTopK + 3, 13);
            case CALCULATION -> Math.max(defaultTopK - 2, 3);
            default -> defaultTopK;
        };
    }

    /**
     * 判断是否启用深度检索（多路召回 + 更多结果）
     */
    public boolean useDeepRetrieval(Intent intent) {
        return intent == Intent.ARTICLE_LOOKUP || intent == Intent.CASE_SEARCH;
    }
}
