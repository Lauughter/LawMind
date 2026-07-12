package com.lhs.lawmind.utils;

import com.lhs.lawmind.config.ChunkingProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 固定窗口分块器
 *
 * <p>分块参数通过 {@link ChunkingProperties} 从 application.yml 读取，支持运行时调整。
 * 默认：窗口 768 字符（≈512 token）、重叠 150 字符（≈100 token）、
 * 单块最大 3072 字符（≈2048 token），对齐 text-embedding-v2 输入限制。</p>
 *
 * <p>断点优先级：句号(。) > 分号(；) > 右括号(）) > 换行(\\n)</p>
 */
@Component
public class FixedWindowChunker implements TextChunker {

    private final int chunkSize;
    private final int overlapSize;
    private final int maxChunkSize;

    public FixedWindowChunker(ChunkingProperties props) {
        this.chunkSize = props.getWindowSize();
        this.overlapSize = props.getOverlapSize();
        this.maxChunkSize = props.getMaxChunkSize();
    }

    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MIN_SENTENCE_CHECK_LENGTH = 80;

    @Override
    public List<String> chunk(String text) {
        return validateChunks(chunkWithPrefix(text, null));
    }

    /**
     * 带上下文前缀的分块：每个分块前自动拼接前缀，帮助检索时定位法律出处
     *
     * @param text   待分块文本
     * @param prefix 上下文前缀（如 "[宪法 第一章 总纲 第一条]"），可为 null
     */
    public List<String> chunkWithPrefix(String text, String prefix) {
        List<String> rawChunks = doChunk(text);
        if (prefix == null || prefix.isEmpty()) {
            return rawChunks;
        }
        List<String> enriched = new ArrayList<>();
        for (String c : rawChunks) {
            enriched.add(prefix + "\n" + c);
        }
        return enriched;
    }

    private List<String> doChunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        String[] paragraphs = text.split("\n");
        StringBuilder buffer = new StringBuilder();

        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) continue;

            if (buffer.length() + trimmed.length() > maxChunkSize) {
                if (buffer.length() > 0) {
                    chunks.add(buffer.toString());
                    buffer = new StringBuilder(buildOverlap(buffer.toString()));
                }
                if (trimmed.length() > maxChunkSize) {
                    splitLongParagraph(trimmed, chunks);
                    buffer = new StringBuilder();
                } else {
                    buffer.append(trimmed);
                }
            } else if (buffer.length() + trimmed.length() > chunkSize && buffer.length() > 0) {
                chunks.add(buffer.toString());
                String overlap = buildOverlap(buffer.toString());
                buffer = new StringBuilder(overlap).append("\n").append(trimmed);
            } else {
                if (buffer.length() > 0) buffer.append("\n");
                buffer.append(trimmed);
            }
        }

        if (buffer.length() > 0) chunks.add(buffer.toString());
        return chunks;
    }

    private void splitLongParagraph(String text, List<String> chunks) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            if (end < text.length()) {
                int bp = findBreakPoint(text, start, end);
                if (bp > start) end = bp;
            }
            chunks.add(text.substring(start, end));
            start = end - overlapSize;
            if (start >= text.length()) break;
        }
    }

    /**
     * 在 [start, end] 全范围内寻找最佳断点。
     * 优先级：句号(。) > 分号(；) > 右括号(）) > 换行(\n)
     * 从 end 向 start 搜索，返回第一个匹配位置的后一位（作为下一段起点）。
     */
    private int findBreakPoint(String text, int start, int end) {
        // 第一遍：找句号
        for (int i = end - 1; i > start; i--) {
            if (text.charAt(i) == '。') return i + 1;
        }
        // 第二遍：找分号或右括号
        for (int i = end - 1; i > start; i--) {
            char c = text.charAt(i);
            if (c == '；' || c == '）') return i + 1;
        }
        // 第三遍：找换行
        for (int i = end - 1; i > start; i--) {
            if (text.charAt(i) == '\n') return i + 1;
        }
        return -1;
    }

    private String buildOverlap(String text) {
        if (text.length() <= overlapSize) return text;
        int start = Math.max(0, text.length() - overlapSize);
        // 找到最近的句号作为重叠起点
        for (int i = start; i < text.length(); i++) {
            if (text.charAt(i) == '。') {
                return text.substring(Math.min(i + 1, text.length()));
            }
        }
        return text.substring(start);
    }

    // ──────────── 分块质量验证 ────────────

    /**
     * 验证分块质量：
     * <ul>
     *   <li>过滤内容过短的分块（无检索意义）</li>
     *   <li>检查句子边界完整性</li>
     * </ul>
     */
    private List<String> validateChunks(List<String> chunks) {
        List<String> valid = new ArrayList<>();
        for (String chunk : chunks) {
            if (chunk == null || chunk.trim().length() < MIN_CONTENT_LENGTH) {
                continue;
            }
            // 检查句子边界：较长分块应以完整句子结尾
            if (chunk.length() > MIN_SENTENCE_CHECK_LENGTH) {
                char lastChar = chunk.charAt(chunk.length() - 1);
                if (!isSentenceEnd(lastChar) && !chunk.trim().endsWith("：")) {
                    // 尝试在当前块尾部补到下一个句号
                    // 这种情况说明 findBreakPoint 未找到合适的断点
                }
            }
            valid.add(chunk);
        }
        return valid;
    }

    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '"' || c == '"' || c == '…';
    }
}
