package com.lhs.lawmind.agent.compress;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * 压缩配置属性类 —— 绑定 application.yml 中的 lawmind.agent.compression 配置。
 */
@ConfigurationProperties(prefix = "lawmind.agent.compression")
public record CompressionConfig(
        boolean enabled,
        int singleResultThreshold,
        int totalContextThreshold,
        double minSavingsRatio,
        Map<String, ToolStrategyConfig> toolStrategies,
        RecencyConfig recency,
        KnowledgeStateConfig knowledgeState
) {

    public CompressionConfig {
        if (singleResultThreshold == 0) singleResultThreshold = 500;
        if (totalContextThreshold == 0) totalContextThreshold = 6000;
        if (minSavingsRatio == 0.0) minSavingsRatio = 2.0;
        if (toolStrategies == null) toolStrategies = Map.of();
        if (recency == null) recency = new RecencyConfig(2, 3, 5);
        if (knowledgeState == null) knowledgeState = new KnowledgeStateConfig(true, 20, true);
    }

    /** 默认配置 */
    public static CompressionConfig defaults() {
        return new CompressionConfig(
                true, 500, 6000, 2.0, Map.of(),
                new RecencyConfig(2, 3, 5),
                new KnowledgeStateConfig(true, 20, true)
        );
    }

    /**
     * 获取指定工具的压缩策略，未配置时返回默认策略。
     */
    public ToolStrategyConfig getStrategy(String toolName) {
        return toolStrategies.getOrDefault(toolName, ToolStrategyConfig.DEFAULTS);
    }

    /** 单个工具的压缩策略配置 */
    public record ToolStrategyConfig(
            int layer,
            boolean compress,
            int maxResults,
            int fullDetailTop,
            boolean preserveOriginalTerms
    ) {
        public static final ToolStrategyConfig DEFAULTS = new ToolStrategyConfig(1, true, 5, 3, false);

        public static ToolStrategyConfig noCompress() {
            return new ToolStrategyConfig(0, false, 0, 0, false);
        }

        public static ToolStrategyConfig layer0() {
            return new ToolStrategyConfig(0, true, 0, 0, true);
        }

        public static ToolStrategyConfig layer1(int maxResults, int fullDetailTop) {
            return new ToolStrategyConfig(1, true, maxResults, fullDetailTop, false);
        }
    }

    /** 递归加权配置 */
    public record RecencyConfig(
            int keepFullRecent,
            int layer1StartRound,
            int layer2StartRound
    ) {}

    /** KnowledgeState 配置 */
    public record KnowledgeStateConfig(
            boolean enabled,
            int maxArticles,
            boolean mergeDuplicates
    ) {}
}
