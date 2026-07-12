package com.lhs.lawmind.agent.memory;

import dev.langchain4j.data.message.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 记忆系统统一入口 —— 检索注入 + 异步提取 + 用户管理。
 */
@Slf4j
@Component
public class MemoryManager {

    private final MemoryRetriever retriever;
    private final MemoryExtractor extractor;
    private final MemoryStore store;
    private final MemoryConfig config;

    public MemoryManager(MemoryRetriever retriever,
                         MemoryExtractor extractor,
                         MemoryStore store,
                         MemoryConfig config) {
        this.retriever = retriever;
        this.extractor = extractor;
        this.store = store;
        this.config = config;
    }

    // ---- 检索 + 格式化（同步，请求前调用） ----

    /**
     * 检索并格式化为可直接注入 System Prompt 的文本。
     * 包含一级索引 + 二级语义匹配详情。
     */
    public String retrieveAndFormat(Long userId, String currentQuestion) {
        if (!config.isEnabled()) return "";

        try {
            MemoryRetriever.MemoryContext ctx = retriever.retrieve(userId, currentQuestion);
            if (ctx.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();

            // 一级索引（始终注入）
            String indexText = retriever.formatIndexForPrompt(ctx.indexMemories());
            if (!indexText.isEmpty()) {
                sb.append(indexText);
            }

            // 二级详情（语义匹配到的才注入）
            String detailText = retriever.formatDetailForPrompt(ctx.detailMemories());
            if (!detailText.isEmpty()) {
                sb.append(detailText);
            }

            String result = sb.toString();
            log.info("记忆注入: userId={}, 索引{}条, 详情{}条, 总长度≈{}字",
                    userId,
                    ctx.indexMemories() != null ? ctx.indexMemories().size() : 0,
                    ctx.detailMemories() != null ? ctx.detailMemories().size() : 0,
                    result.length());
            return result;

        } catch (Exception e) {
            log.warn("记忆检索失败: userId={}, error={}", userId, e.getMessage());
            return "";
        }
    }

    // ---- 异步提取（请求后调用） ----

    /**
     * 异步提取会话中产生的记忆，不阻塞用户响应。
     */
    @Async
    public void extractAsync(Long userId, Long sessionId, List<ChatMessage> messages) {
        if (!config.isEnabled()) return;

        try {
            log.info("开始异步记忆提取: userId={}, sessionId={}", userId, sessionId);

            // 检查用户记忆数量，超出上限时先清理
            enforceCapacity(userId);

            List<AiMemory> memories = extractor.extract(userId, sessionId, messages);
            for (AiMemory memory : memories) {
                store.save(memory);
                log.info("记忆已保存: userId={}, type={}, title={}", userId, memory.getType(), memory.getTitle());
            }
            log.info("记忆提取完成: userId={}, 新增{}条", userId, memories.size());

        } catch (Exception e) {
            log.error("记忆异步提取失败: userId={}, error={}", userId, e.getMessage());
        }
    }

    // ---- 用户管理 ----

    public List<AiMemory> getMemoryList(Long userId) {
        return store.findByUserId(userId);
    }

    public boolean deleteMemory(Long id, Long userId) {
        return store.deleteById(id, userId);
    }

    public int clearAllMemories(Long userId) {
        return store.deleteByUserId(userId);
    }

    /**
     * 容量控制：超出上限时删除 importance 最低的记忆。
     */
    private void enforceCapacity(Long userId) {
        long count = store.countByUserId(userId);
        int max = config.getMaxPerUser();
        if (count >= max) {
            int excess = (int) (count - max + 1);
            List<AiMemory> toDelete = store.findLowestImportance(userId, excess);
            for (AiMemory m : toDelete) {
                store.deleteById(m.getId(), userId);
                log.info("记忆容量控制: 删除低重要性记忆 id={}, importance={}", m.getId(), m.getImportance());
            }
        }
    }
}
