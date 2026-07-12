package com.lhs.lawmind.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * 法律文档结构化数据模型
 * 将法律文档解析为 章→节→条→款 的树形结构，保留完整层级关系
 * 每个节点包含标题、编号、内容等信息，方便后续分块、向量化和检索
 * 适用于中国法律文档的常见结构，支持前言、总则等特殊部分的处理
 * 设计时考虑了法律文档的层级复杂性和多样性，提供了灵活的接口以适应不同法律文档的解析
 * 章 - chapter
 * 节 - section
 * 条 - article
 * 款 - paragraph
 */
public class LegalDocumentNode {

    /**
     * 解析后的完整法律文档
     */
    public static class LegalDocument {
        private String lawName;     // 法律名称
        private String preamble;    // 前言、总则等开头部分内容
        private final List<ChapterNode> chapters = new ArrayList<>();   // 章列表

        public String getLawName() { return lawName; }

        public void setLawName(String lawName) { this.lawName = lawName; }

        public String getPreamble() { return preamble; }

        public void setPreamble(String preamble) { this.preamble = preamble; }

        public List<ChapterNode> getChapters() { return chapters; }

        public void addChapter(ChapterNode chapter) { chapters.add(chapter); }

        public boolean hasPreamble() { return preamble != null && !preamble.isBlank(); }

        /** 获取所有条文（扁平遍历） */
        public List<ArticleNode> getAllArticles() {
            List<ArticleNode> all = new ArrayList<>();
            for (ChapterNode ch : chapters) {
                all.addAll(ch.getAllArticles());
            }
            return all;
        }
    }

    /**
     * 章
     * <p>设计时考虑到有些法律文档可能存在没有节的情况，条直接隶属于章，因此在 ChapterNode 中增加了 directArticles 列表来存储这种情况的条文</p>
     * <p>在获取所有条文时，先检查是否有节，如果有节则从节中获取条文，如果没有节则直接返回隶属于章的条文列表</p>
     * <p>这种设计使得 LegalDocumentNode 能够适应不同法律文档的结构复杂性，既能处理常见的章→节→条结构，也能处理特殊的章→条结构，保证了数据模型的灵活性和完整性</p>
     */
    public static class ChapterNode {
        private String heading;// 章标题
        private int chapterNum;// 章编号
        private final List<SectionNode> sections = new ArrayList<>();// 节列表
        private final List<ArticleNode> directArticles = new ArrayList<>();// 直接隶属于章的条列表（无节的情况）

        public String getHeading() { return heading; }

        public void setHeading(String heading) { this.heading = heading; }

        public int getChapterNum() { return chapterNum; }

        public void setChapterNum(int chapterNum) { this.chapterNum = chapterNum; }

        public List<SectionNode> getSections() { return sections; }

        public List<ArticleNode> getDirectArticles() { return directArticles; }

        // 判断是否有节，如果没有节则条直接隶属于章
        public boolean hasSections() { return !sections.isEmpty(); }

        // 添加节或直接添加条
        public void addSection(SectionNode section) { sections.add(section); }

        // 直接添加条，适用于没有节的情况
        public void addArticle(ArticleNode article) { directArticles.add(article); }

        // 获取所有条文（扁平遍历），无论是直接隶属于章还是在节中
        public List<ArticleNode> getAllArticles() {
            if (hasSections()) {
                List<ArticleNode> all = new ArrayList<>();
                for (SectionNode sec : sections) {
                    all.addAll(sec.getArticles());
                }
                return all;
            }
            return directArticles;
        }
    }

    /**
     * 节
     * <p>节是章的子节点，包含节标题、节编号和条列表。设计时考虑到有些法律文档可能存在没有节的情况，条直接隶属于章，因此在 ChapterNode 中增加了 directArticles 列表来存储这种情况的条文。在获取所有条文时，先检查是否有节，如果有节则从节中获取条文，如果没有节则直接返回隶属于章的条文列表。这种设计使得 LegalDocumentNode 能够适应不同法律文档的结构复杂性，既能处理常见的章→节→条结构，也能处理特殊的章→条结构，保证了数据模型的灵活性和完整性</p>
     *
     */
    public static class SectionNode {
        private String heading;
        private int sectionNum;
        private final List<ArticleNode> articles = new ArrayList<>();

        public String getHeading() { return heading; }
        public void setHeading(String heading) { this.heading = heading; }
        public int getSectionNum() { return sectionNum; }
        public void setSectionNum(int sectionNum) { this.sectionNum = sectionNum; }
        public List<ArticleNode> getArticles() { return articles; }
        public void addArticle(ArticleNode article) { articles.add(article); }
    }

    /**
     * 条
     * <p>条是节的子节点，包含条标题、条编号、内容和款列表。设计时考虑到有些法律文档可能存在没有节的情况，条直接隶属于章，因此在 ChapterNode 中增加了 directArticles 列表来存储这种情况的条文。在获取所有条文时，先检查是否有节，如果有节则从节中获取条文，如果没有节则直接返回隶属于章的条文列表。这种设计使得 LegalDocumentNode 能够适应不同法律文档的结构复杂性，既能处理常见的章→节→条结构，也能处理特殊的章→条结构，保证了数据模型的灵活性和完整性</p>
     */
    public static class ArticleNode {
        // 条标题（通常包含条编号和标题文本）
        private String heading;

        // 条编号（通常是整数，但有些法律文档可能使用其他格式，如“第一条”）
        private int articleNum;

        // 条内容，可能包含多个段落，设计时考虑到有些条文内容较长，包含多个段落，因此在 ArticleNode 中增加了 content 字段来存储完整的条文内容，同时增加了 paragraphs 列表来存储分段后的内容。这种设计使得 LegalDocumentNode 能够适应不同法律文档的结构复杂性，既能处理简单的单段条文，也能处理复杂的多段条文，保证了数据模型的灵活性和完整性
        private String content;

        // 分段后的内容列表，适用于内容较长的条文，设计时考虑到有些条文内容较长，包含多个段落，因此在 ArticleNode 中增加了 content 字段来存储完整的条文内容，同时增加了 paragraphs 列表来存储分段后的内容。这种设计使得 LegalDocumentNode 能够适应不同法律文档的结构复杂性，既能处理简单的单段条文，也能处理复杂的多段条文，保证了数据模型的灵活性和完整性
        private final List<String> paragraphs = new ArrayList<>();

        /** 完整路径：法律名 章标题 */
        private String chapterPath;

        /** 完整路径：法律名 章标题 节标题 */
        private String sectionPath;

        /** 上下文前缀，用于嵌入到分块内容前 */
        private String contextPrefix;

        public String getHeading() { return heading; }

        public void setHeading(String heading) { this.heading = heading; }

        public int getArticleNum() { return articleNum; }

        public void setArticleNum(int articleNum) { this.articleNum = articleNum; }

        public String getContent() { return content; }

        public void setContent(String content) { this.content = content; }

        public List<String> getParagraphs() { return paragraphs; }

        public void addParagraph(String p) { paragraphs.add(p); }

        public String getChapterPath() { return chapterPath; }

        public void setChapterPath(String chapterPath) { this.chapterPath = chapterPath; }

        public String getSectionPath() { return sectionPath; }

        public void setSectionPath(String sectionPath) { this.sectionPath = sectionPath; }

        public String getContextPrefix() { return contextPrefix; }

        public void setContextPrefix(String contextPrefix) { this.contextPrefix = contextPrefix; }
    }
}
