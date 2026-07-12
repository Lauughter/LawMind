package com.lhs.lawmind.agent.memory;

import com.lhs.lawmind.utils.EmbeddingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 两级混合检索器 —— 一级索引注入 + 二级语义自动匹配。
 */
@Slf4j
@Component
public class MemoryRetriever {

    private final MemoryStore memoryStore;
    private final EmbeddingUtil embeddingUtil;
    private final MemoryConfig memoryConfig;

    public MemoryRetriever(MemoryStore memoryStore,
                           EmbeddingUtil embeddingUtil,
                           MemoryConfig memoryConfig) {
        this.memoryStore = memoryStore;
        this.embeddingUtil = embeddingUtil;
        this.memoryConfig = memoryConfig;
    }

    /**
     * 完整检索：一级索引 + 二级语义自动匹配。
     */
    public MemoryContext retrieve(Long userId, String currentQuestion) {
        MemoryConfig.Retrieval cfg = memoryConfig.getRetrieval();

        // 一级：按重要性排序的索引
        List<AiMemory> indexMemories = memoryStore.findTopByImportance(userId, cfg.getMaxIndexItems());

        // 二级：语义相似度匹配
        List<AiMemory> detailMemories = List.of();
        if (currentQuestion != null && !currentQuestion.isBlank()) {
            try {
                float[] questionVector = embeddingUtil.embed(currentQuestion);
                if (questionVector != null && questionVector.length > 0) {
                    List<Long> matchedIds = memoryStore.searchByVector(questionVector, null,
                            cfg.getMaxAutoInject(), cfg.getSimilarityThreshold());
                    detailMemories = new ArrayList<>();
                    for (Long id : matchedIds) {
                        AiMemory mem = memoryStore.findById(id);
                        if (mem != null) {
                            detailMemories.add(mem);
                            memoryStore.updateAccessInfo(id);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("语义检索失败，跳过二级注入: {}", e.getMessage());
            }
        }

        return new MemoryContext(indexMemories, detailMemories);
    }

    /**
     * 格式化一级索引，用于注入 System Prompt（~200 token 预算）。
     */
    public String formatIndexForPrompt(List<AiMemory> memories) {
        if (memories == null || memories.isEmpty()) return "";

        // 按类型分组
        Map<MemoryType, List<AiMemory>> grouped = new LinkedHashMap<>();
        for (MemoryType type : MemoryType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (AiMemory m : memories) {
            grouped.get(m.getType()).add(m);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n## 用户记忆索引\n");

        for (Map.Entry<MemoryType, List<AiMemory>> entry : grouped.entrySet()) {
            List<AiMemory> list = entry.getValue();
            if (list.isEmpty()) continue;
            sb.append("### ").append(typeLabel(entry.getKey())).append("\n");
            for (AiMemory m : list) {
                sb.append("- [M").append(m.getId()).append("] ").append(m.getTitle());
                if (m.getImportance() != null && m.getImportance() >= 0.8) {
                    sb.append(" ★");
                }
                sb.append("\n");
            }
        }
        sb.append("\nAgent 可调用 retrieveMemory(记忆ID) 获取详细内容。\n");
        return sb.toString();
    }

    /**
     * 格式化二级详情，用于注入 System Prompt（~600 token 预算）。
     */
    public String formatDetailForPrompt(List<AiMemory> memories) {
        if (memories == null || memories.isEmpty()) return "";

        // 按类型分组
        Map<MemoryType, List<AiMemory>> grouped = new LinkedHashMap<>();
        for (MemoryType type : MemoryType.values()) {
            grouped.put(type, new ArrayList<>());
        }
        for (AiMemory m : memories) {
            grouped.get(m.getType()).add(m);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n## 用户记忆（来自历史交互）\n");

        for (Map.Entry<MemoryType, List<AiMemory>> entry : grouped.entrySet()) {
            List<AiMemory> list = entry.getValue();
            if (list.isEmpty()) continue;
            sb.append("\n### ").append(detailLabel(entry.getKey())).append("\n");
            for (AiMemory m : list) {
                String content = m.getSummary() != null && !m.getSummary().isBlank()
                        ? m.getSummary() : m.getBody();
                sb.append("- ").append(content).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 获取单条记忆详情（供 Agent 工具调用）。
     */
    public AiMemory getMemoryDetail(Long id) {
        AiMemory memory = memoryStore.findById(id);
        if (memory != null) {
            memoryStore.updateAccessInfo(id);
        }
        return memory;
    }

    private String typeLabel(MemoryType type) {
        return switch (type) {
            case USER -> "用户画像 (USER)";
            case FEEDBACK -> "历史反馈 (FEEDBACK)";
            case PROJECT -> "近期事项 (PROJECT)";
            case REFERENCE -> "相关参考 (REFERENCE)";
        };
    }

    private String detailLabel(MemoryType type) {
        return switch (type) {
            case USER -> "用户画像";
            case FEEDBACK -> "历史反馈";
            case PROJECT -> "近期事项";
            case REFERENCE -> "相关参考";
        };
    }

    /**
     * 两级检索结果上下文。
     */
    public record MemoryContext(List<AiMemory> indexMemories, List<AiMemory> detailMemories) {
        public boolean isEmpty() {
            return (indexMemories == null || indexMemories.isEmpty())
                    && (detailMemories == null || detailMemories.isEmpty());
        }
    }
}
