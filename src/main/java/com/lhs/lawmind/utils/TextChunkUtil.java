package com.lhs.lawmind.utils;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分块工具 — 根据文本类型自动选择分块策略
 */
@Component
public class TextChunkUtil {

    // 法律条文匹配正则
    private static final Pattern LEGAL_PATTERN =
            Pattern.compile("第[零一二三四五六七八九十百千]+[章节条编款目]");

    private final LegalArticleChunker legalArticleChunker;
    private final FixedWindowChunker fixedWindowChunker;

    /**
     * 构造函数，注入分块器
     * @param legalArticleChunker 法律条文分块器（基于正则的智能分块）
     * @param fixedWindowChunker 固定窗口分块器（基于长度的简单分块）
     */
    public TextChunkUtil(LegalArticleChunker legalArticleChunker,
                         FixedWindowChunker fixedWindowChunker) {
        this.legalArticleChunker = legalArticleChunker;
        this.fixedWindowChunker = fixedWindowChunker;
    }

    /**
     * 按法律条文拆分文本（用于上传文档 → 逐条入库）
     */
    public List<String> chunkByArticle(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return legalArticleChunker.chunk(text);
    }

    /**
     * 固定窗口分块（用于过长条文的子分块）
     * <p>当条文内容过长时，先按条文拆分，再对每条进行固定窗口分块，确保每块内容长度适中且条文结构完整</p>
     * @param text 长条文内容，默认窗口大小为500字符，重叠大小为50字符，确保分块之间有足够的上下文信息
     */
    public List<String> chunkByWindow(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        return fixedWindowChunker.chunk(text);
    }

    /**
     * 自动选择分块策略（用于聊天上下文构建 → 既要保持条文完整又要避免过长）
     * <p>如果文本包含法律条文特征，则使用法律条文分块器；否则使用固定窗口分块器</p>
     * <p>这种策略确保在构建聊天上下文时，既能保持法律条文的完整性，又能避免过长的文本块导致模型处理效率降低</p>
     * @param text 输入文本，可能是完整的法律文档或过长的条文内容，根据文本特征自动选择分块策略
     */
    public List<String> chunk(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        if (isLegalArticle(text)) {
            return legalArticleChunker.chunk(text);
        }
        return fixedWindowChunker.chunk(text);
    }

    /**
     * 判断文本是否包含法律条文特征
     * <p>根据正则表达式匹配，判断文本是否包含法律条文特征，如“第”、“条”、“节”等</p>
     * @param text 输入文本
     * @return true 表示包含法律条文特征，false 表示不包含法律条文特征
     */
    private boolean isLegalArticle(String text) {
        return LEGAL_PATTERN.matcher(text).find();
    }
}
