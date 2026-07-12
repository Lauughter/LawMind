package com.lhs.lawmind.agent.compress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layer 1：规则提取器 —— 零 LLM 成本的文本压缩。
 * 使用纯 Java 正则 + 字符串处理，从法律工具返回结果中
 * 提取法律命名实体、金额、时效等结构化信息。
 * 同时输出 KnowledgeAtom 列表供 KnowledgeState 消费。
 */
public class RuleExtractor {

    private static final Pattern LAW_NAME_PATTERN = Pattern.compile("《(.+?)》");
    private static final Pattern ARTICLE_NUM_PATTERN = Pattern.compile(
            "第([一二三四五六七八九十百千]+)条");
    private static final Pattern ARTICLE_NUM_DIGIT_PATTERN = Pattern.compile(
            "第(\\d+)条");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([\\d,]+\\.?\\d*)\\s*([元万元])\\s*(?:以[上下])?.*?(?:赔偿|补偿|工资|抚养|赡养|加班|罚|偿)");
    private static final Pattern DEADLINE_PATTERN = Pattern.compile(
            "(\\d+)\\s*([年个月日]).*?(?:时效|仲裁|诉讼|申请|起诉|提出)");

    /**
     * 提取结果：结构化摘要 + 知识原子列表。
     */
    public record ExtractResult(
            String summary,
            List<KnowledgeAtom> atoms
    ) {}

    /**
     * 从工具返回结果中提取结构化摘要和知识原子。
     */
    public ExtractResult extract(String toolName, String rawResult) {
        if (rawResult == null || rawResult.isEmpty()) {
            return new ExtractResult(rawResult, List.of());
        }

        List<KnowledgeAtom> atoms = new ArrayList<>();
        StringBuilder summary = new StringBuilder();

        // 1. 提取法条信息
        List<String> lawRefs = extractLawReferences(rawResult);
        if (!lawRefs.isEmpty()) {
            summary.append("【法条引用】\n");
            for (String ref : lawRefs) {
                summary.append("  ").append(ref).append("\n");
            }
            // Create atoms for KnowledgeState
            for (String ref : lawRefs) {
                extractArticleAtoms(ref, toolName).forEach(atoms::add);
            }
        }

        // 2. 提取金额信息
        List<String> amounts = extractAmounts(rawResult);
        if (!amounts.isEmpty()) {
            summary.append("【金额/计算公式】\n");
            for (String amt : amounts) {
                summary.append("  ").append(amt).append("\n");
            }
        }

        // 3. 提取时效信息
        List<String> deadlines = extractDeadlines(rawResult);
        if (!deadlines.isEmpty()) {
            summary.append("【时效信息】\n");
            for (String dl : deadlines) {
                summary.append("  ").append(dl).append("\n");
            }
        }

        // 4. 如果提取到关键信息，追加保留的原文摘要
        String stripped = stripDecorative(rawResult);
        if (summary.length() == 0) {
            // No structured info extracted, return stripped version
            summary.append(stripped.length() > 500 ? stripped.substring(0, 500) + "..." : stripped);
        } else if (stripped.length() < 200) {
            summary.append("\n---\n").append(stripped);
        }

        return new ExtractResult(summary.toString(), atoms);
    }

    /**
     * 只提取知识原子（供 KnowledgeState 使用），不做摘要。
     */
    public List<KnowledgeAtom> extractAtoms(String toolName, String rawResult) {
        ExtractResult result = extract(toolName, rawResult);
        return result.atoms();
    }

    // ---- Private extraction helpers ----

    private List<String> extractLawReferences(String text) {
        List<String> refs = new ArrayList<>();
        Matcher lawMatcher = LAW_NAME_PATTERN.matcher(text);

        while (lawMatcher.find()) {
            String lawName = lawMatcher.group(1);
            // Find nearest article number after this law name
            int searchStart = lawMatcher.end();
            int searchEnd = Math.min(searchStart + 200, text.length());
            String nearby = text.substring(searchStart, searchEnd);

            Matcher artMatcher = ARTICLE_NUM_PATTERN.matcher(nearby);
            Matcher artDigitMatcher = ARTICLE_NUM_DIGIT_PATTERN.matcher(nearby);

            if (artMatcher.find()) {
                refs.add(String.format("《%s》第%s条", lawName, artMatcher.group(1)));
            } else if (artDigitMatcher.find()) {
                refs.add(String.format("《%s》第%s条", lawName, artDigitMatcher.group(1)));
            } else {
                refs.add(String.format("《%s》（相关条款）", lawName));
            }
        }
        return refs.stream().distinct().toList();
    }

    private List<KnowledgeAtom> extractArticleAtoms(String lawRef, String toolName) {
        List<KnowledgeAtom> atoms = new ArrayList<>();
        Matcher m = Pattern.compile("《(.+?)》第(.+?)条").matcher(lawRef);
        if (m.find()) {
            atoms.add(new KnowledgeAtom.ArticleAtom(
                    m.group(1), "第" + m.group(2) + "条", "",
                    1, List.of(toolName), false));
        }
        return atoms;
    }

    private List<String> extractAmounts(String text) {
        List<String> amounts = new ArrayList<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            amounts.add(matcher.group(0).trim());
        }
        return amounts.stream().distinct().toList();
    }

    private List<String> extractDeadlines(String text) {
        List<String> deadlines = new ArrayList<>();
        Matcher matcher = DEADLINE_PATTERN.matcher(text);
        while (matcher.find()) {
            deadlines.add(matcher.group(0).trim());
        }
        return deadlines.stream().distinct().toList();
    }

    /**
     * Strip decorative/boilerplate text, keep legal substance.
     */
    private String stripDecorative(String text) {
        return text
                .replaceAll("\\[检索结果\\]", "")
                .replaceAll("\\[查询结果\\]", "")
                .replaceAll("\\[相似问题匹配\\]", "")
                .replaceAll("\\[Tool 错误\\].*?。", "")
                .replaceAll("建议：尝试更换关键词或扩大搜索范围。", "")
                .replaceAll("注意事项：以上为历史回答，请核实其中引用的法条是否仍然有效。", "")
                .replaceAll("(?m)^\\s*$\\n?", "")
                .trim();
    }
}
