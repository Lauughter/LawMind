package com.lhs.lawmind.agent.tool;

import com.lhs.lawmind.agent.memory.AiMemory;
import com.lhs.lawmind.agent.memory.MemoryRetriever;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Agent 可调用的记忆检索工具 —— 从一级索引中的记忆 ID 获取详细内容。
 */
@Slf4j
@Component
public class RetrieveMemoryTool {

    private final MemoryRetriever memoryRetriever;

    public RetrieveMemoryTool(MemoryRetriever memoryRetriever) {
        this.memoryRetriever = memoryRetriever;
    }

    @Tool("获取指定记忆的详细内容。当需要了解用户的历史背景、偏好或之前相关讨论时调用。参数 memoryId 从用户记忆索引中获取，如 M12、M5 等。")
    public String retrieveMemory(@P("记忆 ID，如 M12、M5") String memoryId) {
        try {
            String idStr = memoryId.startsWith("M") ? memoryId.substring(1) : memoryId;
            Long id = Long.parseLong(idStr);
            AiMemory memory = memoryRetriever.getMemoryDetail(id);
            if (memory == null) {
                return "记忆不存在: " + memoryId;
            }
            return String.format("[%s] %s\n%s",
                    memory.getType(), memory.getTitle(), memory.getBody());
        } catch (NumberFormatException e) {
            return "无效的记忆 ID: " + memoryId + "，请使用 M1、M5 等格式";
        } catch (Exception e) {
            log.error("retrieveMemory 执行失败: memoryId={}, error={}", memoryId, e.getMessage());
            return "[错误] 记忆检索失败: " + e.getMessage();
        }
    }
}
