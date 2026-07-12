package com.lhs.lawmind.agent.memory;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 统一记忆实体 —— 四类型记忆的唯一存储模型。
 */
@Data
public class AiMemory {
    private Long id;
    private Long userId;
    private MemoryType type;
    private String title;
    private String body;
    private String summary;
    private Long originSessionId;
    private String sourceSessionIds;
    private Double confidence;
    private Double importance;
    private Integer accessCount;
    private String embedding;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastAccessedAt;
}
