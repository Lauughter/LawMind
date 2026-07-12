package com.lhs.lawmind.agent.memory;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 记忆系统配置 —— 绑定 application.yml 中 lawmind.memory.* 配置项。
 */
@Data
@Component
@ConfigurationProperties(prefix = "lawmind.memory")
public class MemoryConfig {
    /** 是否启用记忆系统 */
    private boolean enabled = true;

    /** 每用户最多保留记忆数 */
    private int maxPerUser = 200;

    /** 检索配置 */
    private Retrieval retrieval = new Retrieval();

    /** 归并配置 */
    private Consolidation consolidation = new Consolidation();

    /** 分类型衰减配置 */
    private Decay decay = new Decay();

    @Data
    public static class Retrieval {
        /** 一级索引最多条目数 */
        private int maxIndexItems = 30;
        /** 二级自动注入最多条数 */
        private int maxAutoInject = 3;
        /** 一级索引注入 token 上限 */
        private int indexTokenBudget = 200;
        /** 记忆注入总 token 上限 */
        private int totalTokenBudget = 800;
        /** 语义检索相似度阈值 */
        private double similarityThreshold = 0.7;
    }

    @Data
    public static class Consolidation {
        /** 归并 cron 表达式 */
        private String cron = "0 0 3 * * ?";
        /** 向量相似度超过此值触发合并 */
        private double mergeThreshold = 0.85;
        /** 低于此值的记忆自动删除 */
        private double minImportance = 0.2;
    }

    @Data
    public static class Decay {
        /** 项目记忆衰减触发天数 */
        private int projectDays = 30;
        /** 参考记忆衰减触发天数 */
        private int referenceDays = 60;
        /** 反馈记忆衰减触发天数 */
        private int feedbackDays = 90;
        /** 用户记忆衰减触发天数 */
        private int userDays = 180;
    }
}
