package com.lhs.lawmind.utils.chunking;

import java.util.List;

/**
 * 文本分块策略接口
 */
public interface TextChunker {
    List<String> chunk(String text);
}
