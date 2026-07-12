package com.lhs.lawmind.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律条文分块器 — 按文档结构树（章/节/条/款）拆分
 *
 * <p>解析策略：
 * <ol>
 *   <li>剥离目录（"目录"到第一个正文"第X章"之间的内容）</li>
 *   <li>识别序言/前言（第一个"第X章"之前的文本，按段落分块）</li>
 *   <li>构建 章→节→条→款 的层级树</li>
 *   <li>条内按自然段落（双换行）拆分为款</li>
 * </ol>
 */
@Slf4j
@Component
public class LegalArticleChunker implements TextChunker {

    private static final String CHINESE_NUM = "[零一二三四五六七八九十百千]+";
    private static final Pattern ARTICLE_PATTERN = Pattern.compile("(第" + CHINESE_NUM + "[章节条编款目])");
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("第(" + CHINESE_NUM + ")章");
    private static final Pattern SECTION_PATTERN = Pattern.compile("第(" + CHINESE_NUM + ")节");
    private static final Pattern PART_PATTERN = Pattern.compile("第(" + CHINESE_NUM + ")编");
    private static final Pattern ARTICLE_ONLY = Pattern.compile("第(" + CHINESE_NUM + ")条");
    private static final Pattern TOC_START = Pattern.compile("^[\\s　]*目[\\s　]+录", Pattern.MULTILINE);
    private static final Pattern HEADING_LINE = Pattern.compile("^第" + CHINESE_NUM + "[章节].*$", Pattern.MULTILINE);

    private static final int MIN_CHUNK_LENGTH = 256;
    private static final int MIN_CONTENT_LENGTH = 5;

    // ──────────── 公开接口 ────────────

    /** 原有兼容接口：返回扁平条文列表 */
    @Override
    public List<String> chunk(String text) {
        return chunk(text, null);
    }

    /**
     * 带法律名称的分块，上下文前缀会包含法律名
     */
    public List<String> chunk(String text, String lawName) {
        LegalDocumentNode.LegalDocument doc = parse(text, lawName);
        List<String> result = new ArrayList<>();
        if (doc.hasPreamble()) {
            result.addAll(splitPreamble(doc.getPreamble()));
        }
        for (LegalDocumentNode.ArticleNode article : doc.getAllArticles()) {
            String enriched = article.getContextPrefix() + "\n" + article.getContent();
            result.add(enriched);
        }
        return mergeShortSegments(validateChunks(result));
    }

    /** 按法律条文拆分（与 chunk 相同，用于上传文档 → 逐条入库） */
    public List<String> chunkByArticle(String text) {
        return chunk(text, null);
    }

    /** 按法律条文拆分（带法律名称） */
    public List<String> chunkByArticle(String text, String lawName) {
        return chunk(text, lawName);
    }

    /**
     * 核心方法：解析法律文档为结构化树
     *
     * @param text    文档全文
     * @return 解析后的文档树（含序言和 编→章→节→条 结构）
     */
    public LegalDocumentNode.LegalDocument parse(String text) {
        return parse(text, null);
    }

    /**
     * 核心方法：解析法律文档为结构化树（带法律名称）
     *
     * @param text    文档全文
     * @param lawName 法律名称，用于构建上下文前缀
     * @return 解析后的文档树（含序言和 编→章→节→条 结构）
     */
    public LegalDocumentNode.LegalDocument parse(String text, String lawName) {
        LegalDocumentNode.LegalDocument doc = new LegalDocumentNode.LegalDocument();
        doc.setLawName(lawName != null ? lawName : extractLawNameFromText(text));

        if (text == null || text.isBlank()) return doc;

        String cleaned = stripToc(text);
        int firstChapter = findFirstBodyChapter(cleaned);

        // 序言
        if (firstChapter > 0) {
            String preamble = cleaned.substring(0, firstChapter).trim();
            if (!preamble.isBlank()) {
                doc.setPreamble(preamble);
            }
        }

        String body = cleaned.substring(firstChapter).trim();

        // 找到所有结构标记的位置和类型
        List<Marker> markers = findAllMarkers(body);
        if (markers.isEmpty()) {
            // 无标记文本
            for (String p : body.split("\n{2,}")) {
                p = p.trim();
                if (!p.isEmpty()) {
                    LegalDocumentNode.ArticleNode article = new LegalDocumentNode.ArticleNode();
                    article.setContent(p);
                    article.setContextPrefix(doc.getLawName() != null ? doc.getLawName() : "");
                    doc.getAllArticles().add(article);
                }
            }
            return doc;
        }

        // 构建树
        LegalDocumentNode.ChapterNode currentChapter = null;
        LegalDocumentNode.SectionNode currentSection = null;
        String currentPartHeading = null; // 当前编标题（如"第一编 总则"）

        for (int i = 0; i < markers.size(); i++) {
            Marker m = markers.get(i);
            int segEnd = (i + 1 < markers.size()) ? markers.get(i + 1).startPos : body.length();
            String segBody = body.substring(m.startPos, segEnd).trim();
            int firstLineEnd = segBody.indexOf('\n');
            String contentStart = firstLineEnd > 0 ? segBody.substring(firstLineEnd).trim() : "";
            String fullContent = segBody;

            switch (m.type) {
                case '编' -> {
                    currentPartHeading = m.rawText;
                    log.debug("识别编: {}", m.rawText);
                }
                case '章' -> {
                    currentSection = null;
                    currentChapter = new LegalDocumentNode.ChapterNode();
                    currentChapter.setHeading(m.rawText);
                    currentChapter.setChapterNum(chineseToInt(m.number));
                    doc.addChapter(currentChapter);
                    if (!contentStart.isBlank()) {
                        boolean hasSection = false;
                        for (int j = i + 1; j < markers.size(); j++) {
                            if (markers.get(j).type == '章' || markers.get(j).type == '编') break;
                            if (markers.get(j).type == '节') { hasSection = true; break; }
                        }
                    }
                }
                case '节' -> {
                    currentSection = new LegalDocumentNode.SectionNode();
                    currentSection.setHeading(m.rawText);
                    currentSection.setSectionNum(chineseToInt(m.number));
                    if (currentChapter != null) {
                        currentChapter.addSection(currentSection);
                    }
                }
                case '条' -> {
                    LegalDocumentNode.ArticleNode article = new LegalDocumentNode.ArticleNode();
                    article.setHeading(m.rawText);
                    article.setArticleNum(chineseToInt(m.number));
                    article.setContent(fullContent);
                    article.setChapterPath(buildChapterPath(doc, currentChapter));
                    article.setSectionPath(buildSectionPath(doc, currentChapter, currentSection));
                    article.setContextPrefix(buildContextPrefix(doc, currentChapter, currentSection, article, currentPartHeading));
                    String[] rawParas = contentStart.split("\n{2,}");
                    for (String rp : rawParas) {
                        rp = rp.trim();
                        if (!rp.isEmpty()) {
                            article.addParagraph(rp);
                        }
                    }
                    if (article.getParagraphs().isEmpty()) {
                        article.addParagraph(contentStart.isEmpty() ? fullContent : contentStart);
                    }

                    if (currentChapter != null) {
                        if (currentSection != null) {
                            currentSection.addArticle(article);
                        } else {
                            currentChapter.addArticle(article);
                        }
                    }
                }
            }
        }

        return doc;
    }

    // ──────────── 目录剥离 ────────────

    /**
     * 剥离目录区域。
     * 目录位于正文之前，通常以"目 录"开头，到第一个"第X章"（正文）结束。
     */
    private String stripToc(String text) {
        Matcher tocMatcher = TOC_START.matcher(text);
        if (!tocMatcher.find()) return text;

        int tocStart = tocMatcher.start();
        // 找到目录后第一个正文章标题
        Matcher chapterMatcher = CHAPTER_PATTERN.matcher(text);
        while (chapterMatcher.find()) {
            if (chapterMatcher.start() > tocStart) {
                // 这是正文的第一个章，目录在它之前
                return text.substring(0, tocStart).trim() + "\n" + text.substring(chapterMatcher.start());
            }
        }
        return text; // 没找到正文章，保留原文
    }

    /** 找到第一个正文章的位置（跳过目录中出现的章标题） */
    private int findFirstBodyChapter(String text) {
        // 查找实际的章标题行（单独成行的"第X章"）
        Matcher m = CHAPTER_PATTERN.matcher(text);
        while (m.find()) {
            // 章标志通常在一行的开头
            int lineStart = m.start();
            if (lineStart == 0 || text.charAt(lineStart - 1) == '\n') {
                return lineStart;
            }
        }
        return 0;
    }

    // ──────────── 序言处理 ────────────

    /** 按段落拆分序言 */
    private List<String> splitPreamble(String preamble) {
        List<String> result = new ArrayList<>();
        String[] paragraphs = preamble.split("\n{2,}");
        StringBuilder buffer = new StringBuilder();
        final int PREAMBLE_CHUNK = 1200;
        for (String p : paragraphs) {
            p = p.trim();
            if (p.isEmpty()) continue;
            if (buffer.length() + p.length() > PREAMBLE_CHUNK && buffer.length() > 0) {
                result.add(buffer.toString());
                buffer = new StringBuilder();
            }
            if (buffer.length() > 0) buffer.append("\n");
            buffer.append(p);
        }
        if (buffer.length() > 0) result.add(buffer.toString());
        return result;
    }

    // ──────────── 标记解析 ────────────

    private static class Marker {
        final char type;       // '章'/'节'/'条'/'款'/'目'
        final String number;   // 中文数字字符串
        final int startPos;
        final String rawText;

        Marker(char type, String number, int startPos, String rawText) {
            this.type = type;
            this.number = number;
            this.startPos = startPos;
            this.rawText = rawText;
        }
    }

    private List<Marker> findAllMarkers(String text) {
        List<Marker> markers = new ArrayList<>();
        Matcher m = ARTICLE_PATTERN.matcher(text);
        while (m.find()) {
            String raw = m.group(1);
            // 从 raw 中提取类型和数字
            // "第一章" → type='章', number="一"
            // "第一百四十三条" → type='条', number="一百四十三"
            char type = raw.charAt(raw.length() - 1);
            String number = raw.substring(1, raw.length() - 1);
            markers.add(new Marker(type, number, m.start(), raw));
        }
        return markers;
    }

    // ──────────── 路径构建 ────────────

    private String buildChapterPath(LegalDocumentNode.LegalDocument doc,
                                     LegalDocumentNode.ChapterNode chapter) {
        if (doc.getLawName() == null || chapter == null) return "";
        return doc.getLawName() + " " + chapter.getHeading();
    }

    private String buildSectionPath(LegalDocumentNode.LegalDocument doc,
                                     LegalDocumentNode.ChapterNode chapter,
                                     LegalDocumentNode.SectionNode section) {
        String chPath = buildChapterPath(doc, chapter);
        if (section == null) return chPath;
        return chPath + " " + section.getHeading();
    }

    private String buildContextPrefix(LegalDocumentNode.LegalDocument doc,
                                       LegalDocumentNode.ChapterNode chapter,
                                       LegalDocumentNode.SectionNode section,
                                       LegalDocumentNode.ArticleNode article,
                                       String partHeading) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (doc.getLawName() != null) {
            sb.append(doc.getLawName());
        }
        if (partHeading != null) {
            sb.append(" ").append(partHeading);
        }
        if (chapter != null) {
            sb.append(" ").append(chapter.getHeading());
        }
        if (section != null) {
            sb.append(" ").append(section.getHeading());
        }
        sb.append(" ").append(article.getHeading()).append("]");
        return sb.toString();
    }

    /** 向后兼容的简化版 */
    private String buildContextPrefix(LegalDocumentNode.LegalDocument doc,
                                       LegalDocumentNode.ChapterNode chapter,
                                       LegalDocumentNode.SectionNode section,
                                       LegalDocumentNode.ArticleNode article) {
        return buildContextPrefix(doc, chapter, section, article, null);
    }

    // ──────────── 中文数字转换 ────────────

    private static final java.util.Map<Character, Integer> CN_DIGIT = Map.of(
            '零', 0, '一', 1, '二', 2, '三', 3, '四', 4,
            '五', 5, '六', 6, '七', 7, '八', 8, '九', 9
    );

    /**
     * 将中文数字字符串转为整数，例如 "一百四十三" → 143, "三十五" → 35, "十" → 10
     */
    static int chineseToInt(String cn) {
        if (cn == null || cn.isEmpty()) return 0;
        int result = 0;
        int section = 0; // 当前节（百/十/个位累积）

        for (int i = 0; i < cn.length(); i++) {
            char c = cn.charAt(i);
            Integer digit = CN_DIGIT.get(c);
            if (digit != null) {
                section = digit;
            } else if (c == '十') {
                if (section == 0) section = 1; // "十" = 10
                result += section * 10;
                section = 0;
            } else if (c == '百') {
                if (section == 0) section = 1;
                result += section * 100;
                section = 0;
            } else if (c == '千') {
                if (section == 0) section = 1;
                result += section * 1000;
                section = 0;
            }
        }
        result += section; // 个位
        return result;
    }

    // ──────────── 短段合并（向后兼容） ────────────

    private List<String> mergeShortSegments(List<String> segments) {
        List<String> result = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String segment : segments) {
            if (buffer.length() == 0) {
                buffer.append(segment);
            } else if (buffer.length() + segment.length() < MIN_CHUNK_LENGTH) {
                buffer.append("\n").append(segment);
            } else {
                result.add(buffer.toString());
                buffer = new StringBuilder(segment);
            }
        }
        if (buffer.length() > 0) {
            if (buffer.length() < MIN_CHUNK_LENGTH && !result.isEmpty()) {
                int lastIdx = result.size() - 1;
                result.set(lastIdx, result.get(lastIdx) + "\n" + buffer);
            } else {
                result.add(buffer.toString());
            }
        }
        return result;
    }

    // ──────────── 分块质量验证 ────────────

    /**
     * 验证并过滤分块质量。
     * <ul>
     *   <li>过滤内容过短（&lt;MIN_CONTENT_LENGTH）的无效分块</li>
     *   <li>标记不以句号结尾的分块（debug 日志）</li>
     *   <li>合并没有上下文前缀的分块到前一个分块</li>
     * </ul>
     */
    private List<String> validateChunks(List<String> chunks) {
        List<String> valid = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk == null || chunk.trim().length() < MIN_CONTENT_LENGTH) {
                log.debug("跳过过短分块: length={}", chunk == null ? 0 : chunk.length());
                continue;
            }
            // 检查句子边界：理想情况下分块应以句号结尾
            String content = extractContentFromChunk(chunk);
            if (content.length() > 50 && !content.trim().endsWith("。")
                    && !content.trim().endsWith("；") && !content.trim().endsWith("）")) {
                log.debug("分块可能截断句子: prefix={}", chunk.substring(0, Math.min(60, chunk.length())));
            }
            valid.add(chunk);
        }
        return valid;
    }

    /** 提取分块中去掉上下文前缀后的实际内容 */
    private String extractContentFromChunk(String chunk) {
        int idx = chunk.indexOf("]\n");
        if (idx > 0 && chunk.startsWith("[")) {
            return chunk.substring(idx + 2);
        }
        return chunk;
    }

    /** 从文本中尝试提取法律名称（作为 parse 不带 lawName 时的兜底） */
    private String extractLawNameFromText(String text) {
        if (text == null || text.length() < 5) return null;
        java.util.regex.Matcher m = Pattern.compile(
                "中华人民共和国([^法条例决定办法则规定见程范通则释引守]{1,30}[法条例决定办法则规定见程范通则释引守])")
                .matcher(text.length() > 2000 ? text.substring(0, 2000) : text);
        if (m.find()) {
            return "中华人民共和国" + m.group(1);
        }
        return null;
    }
}
