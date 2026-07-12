package com.lhs.lawmind.agent.compress;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ★ 结构化知识状态 —— 压缩策略的核心创新。
 * 在 Agent 推理过程中逐步积累和维护已获取的法律知识，
 * 实现去重合并、引用溯源、冲突检测。
 *
 * 类比 Claude Code 的 conversation summary ——
 * 一个独立于原始消息列表的结构化理解，随对话逐步更新。
 */
public class KnowledgeState {

    private static final Pattern LAW_NAME_PATTERN = Pattern.compile("《(.+?)》");
    private static final Pattern ARTICLE_NUM_PATTERN = Pattern.compile(
            "第([一二三四五六七八九十百千]+)条(?:第([一二三四五六七八九十百千]+)款)?");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "([\\d,]+\\.?\\d*)\\s*([元万元])");
    private static final Pattern DEADLINE_PATTERN = Pattern.compile(
            "(\\d+)\\s*([年个月日]).*?(?:时效|仲裁|诉讼|申请|起诉)");

    private final List<ArticleEntry> articles = new ArrayList<>();
    private final List<CalcEntry> calculations = new ArrayList<>();
    private final List<String> reminders = new ArrayList<>();
    private final List<CaseEntry> cases = new ArrayList<>();
    private final int maxArticles;

    public KnowledgeState(int maxArticles) {
        this.maxArticles = maxArticles;
    }

    public KnowledgeState() {
        this(20);
    }

    /**
     * 从工具结果中提取知识原子并合并到状态中。
     * @return 新发现的知识原子数量
     */
    public int ingest(String toolName, String toolResult, int roundIndex) {
        if (toolResult == null || toolResult.isEmpty()) {
            return 0;
        }
        int newAtoms = 0;
        String sourceTag = toolName + "(R" + roundIndex + ")";

        List<KnowledgeAtom.ArticleAtom> extractedArticles = extractArticles(toolResult, sourceTag);
        for (KnowledgeAtom.ArticleAtom atom : extractedArticles) {
            ArticleEntry existing = findArticle(atom.lawName(), atom.articleNumber());
            if (existing != null) {
                existing.atom = existing.atom.mergeSource(atom);
            } else {
                articles.add(new ArticleEntry(atom));
                newAtoms++;
            }
        }

        newAtoms += extractCalculations(toolResult, sourceTag);
        newAtoms += extractDeadlines(toolResult);
        newAtoms += extractCases(toolResult, sourceTag);

        evictIfNeeded();
        return newAtoms;
    }

    /**
     * 将当前知识状态格式化为 LLM 可读的结构化知识索引。
     */
    public String toCompactSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("[已检索到的关键法律信息]\n");

        if (!articles.isEmpty()) {
            sb.append("\n■ 相关法条（按引用频次排序）：\n");
            articles.stream()
                    .sorted(Comparator.comparingInt(a -> -a.atom.citeCount()))
                    .forEach(a -> sb.append("  ").append(a.atom.toCompactString()).append("\n"));
            long verified = articles.stream().filter(a -> a.atom.verified()).count();
            long unverified = articles.size() - verified;
            sb.append(String.format("  状态：已核实 %d 条 | 待核实 %d 条\n", verified, unverified));
        }

        if (!calculations.isEmpty()) {
            sb.append("\n■ 金额计算：\n");
            calculations.forEach(c -> sb.append("  ").append(c.atom.toCompactString()).append("\n"));
        }

        if (!reminders.isEmpty()) {
            sb.append("\n■ 时效与程序提醒：\n");
            reminders.forEach(r -> sb.append("  - ").append(r).append("\n"));
        }

        if (!cases.isEmpty()) {
            sb.append("\n■ 参考案例：\n");
            cases.forEach(c -> sb.append("  - ").append(c.atom.toCompactString()).append("\n"));
        }

        return sb.toString();
    }

    public int getArticleCount() { return articles.size(); }
    public int getCalcCount() { return calculations.size(); }
    public int getReminderCount() { return reminders.size(); }
    public int getCaseCount() { return cases.size(); }

    // ---- internal extraction ----

    private List<KnowledgeAtom.ArticleAtom> extractArticles(String text, String source) {
        List<KnowledgeAtom.ArticleAtom> result = new ArrayList<>();
        Matcher lawMatcher = LAW_NAME_PATTERN.matcher(text);
        Matcher artMatcher = ARTICLE_NUM_PATTERN.matcher(text);

        // Extract article numbers with surrounding law name context
        while (artMatcher.find()) {
            String articleNum = artMatcher.group(1);
            String paragraph = artMatcher.group(2);

            // Find the nearest law name before this article
            String lawName = findNearestLawName(text, artMatcher.start());
            String keyRule = extractKeyRule(text, artMatcher.start());

            String articleStr = "第" + articleNum + "条";
            if (paragraph != null) {
                articleStr += "第" + paragraph + "款";
            }

            result.add(new KnowledgeAtom.ArticleAtom(
                    lawName != null ? lawName : "未知法律",
                    articleStr,
                    keyRule.length() > 80 ? keyRule.substring(0, 80) + "..." : keyRule,
                    1,
                    List.of(source),
                    false
            ));
        }
        return result;
    }

    private String findNearestLawName(String text, int pos) {
        Matcher lawMatcher = LAW_NAME_PATTERN.matcher(text);
        String nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        while (lawMatcher.find()) {
            int dist = pos - lawMatcher.end();
            if (dist >= 0 && dist < nearestDist) {
                nearestDist = dist;
                nearest = lawMatcher.group(1);
            }
        }
        return nearest;
    }

    private String extractKeyRule(String text, int startPos) {
        int end = Math.min(startPos + 120, text.length());
        return text.substring(startPos, end).replaceAll("\\s+", " ").trim();
    }

    private int extractCalculations(String text, String source) {
        int added = 0;
        Matcher matcher = AMOUNT_PATTERN.matcher(text);
        while (matcher.find()) {
            String amount = matcher.group(1);
            String unit = matcher.group(2);
            // Look for context words around the amount
            int ctxStart = Math.max(0, matcher.start() - 20);
            int ctxEnd = Math.min(text.length(), matcher.end() + 20);
            String context = text.substring(ctxStart, ctxEnd).replaceAll("\\s+", " ").trim();

            String desc = String.format("%s%s元的%s", amount, unit, context);
            calculations.add(new CalcEntry(
                    new KnowledgeAtom.CalcAtom(desc.length() > 100 ? desc.substring(0, 100) : desc,
                            "见上下文", source)));
            added++;
        }
        return added;
    }

    private int extractDeadlines(String text) {
        int added = 0;
        Matcher matcher = DEADLINE_PATTERN.matcher(text);
        while (matcher.find()) {
            String reminder = String.format("%s%s时效提醒：%s",
                    matcher.group(1), matcher.group(2), matcher.group(0));
            if (!reminders.contains(reminder)) {
                reminders.add(reminder);
                added++;
            }
        }
        return added;
    }

    @SuppressWarnings("unused")
    private int extractCases(String text, String source) {
        // Cases are harder to extract with regex alone; this is a simplified version
        // For now, detect case-like patterns (判决书, 案例, etc.)
        if (text.contains("判决书") || text.contains("案例") || text.contains("判例")) {
            // Extract a short snippet as case reference
            String snippet = text.length() > 200 ? text.substring(0, 200) + "..." : text;
            cases.add(new CaseEntry(
                    new KnowledgeAtom.CaseAtom("参考案例", snippet, source)));
            return 1;
        }
        return 0;
    }

    private ArticleEntry findArticle(String lawName, String articleNumber) {
        for (ArticleEntry entry : articles) {
            KnowledgeAtom.ArticleAtom a = entry.atom;
            if (a.lawName().equals(lawName) && a.articleNumber().equals(articleNumber)) {
                return entry;
            }
        }
        return null;
    }

    private void evictIfNeeded() {
        while (articles.size() > maxArticles) {
            articles.stream()
                    .min(Comparator.comparingInt(a -> a.atom.citeCount()))
                    .ifPresent(articles::remove);
        }
    }

    // ---- internal entries ----

    static class ArticleEntry {
        KnowledgeAtom.ArticleAtom atom;
        ArticleEntry(KnowledgeAtom.ArticleAtom atom) { this.atom = atom; }
    }

    static class CalcEntry {
        KnowledgeAtom.CalcAtom atom;
        CalcEntry(KnowledgeAtom.CalcAtom atom) { this.atom = atom; }
    }

    static class CaseEntry {
        KnowledgeAtom.CaseAtom atom;
        CaseEntry(KnowledgeAtom.CaseAtom atom) { this.atom = atom; }
    }
}
