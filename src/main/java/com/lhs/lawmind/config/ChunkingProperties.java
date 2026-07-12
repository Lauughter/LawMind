package com.lhs.lawmind.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文档分块策略配置属性
 * 支持通过 application.yml 动态调整分块参数，无需重新编译
 */
@Component
@ConfigurationProperties(prefix = "lawmind.chunking")
public class ChunkingProperties {

    /** 分块窗口大小（中文字符），默认 768（≈512 token） */
    private int windowSize = 768;

    /** 分块重叠大小（中文字符），默认 150（≈100 token） */
    private int overlapSize = 150;

    /** 单块最大字符数，默认 3072（≈2048 token），对齐 text-embedding-v2 限制 */
    private int maxChunkSize = 3072;

    /** 文章超出此长度（字符）时触发子分块，默认 1500 */
    private int subChunkThreshold = 1500;

    public int getWindowSize() { return windowSize; }
    public void setWindowSize(int windowSize) { this.windowSize = windowSize; }

    public int getOverlapSize() { return overlapSize; }
    public void setOverlapSize(int overlapSize) { this.overlapSize = overlapSize; }

    public int getMaxChunkSize() { return maxChunkSize; }
    public void setMaxChunkSize(int maxChunkSize) { this.maxChunkSize = maxChunkSize; }

    public int getSubChunkThreshold() { return subChunkThreshold; }
    public void setSubChunkThreshold(int subChunkThreshold) { this.subChunkThreshold = subChunkThreshold; }
}
