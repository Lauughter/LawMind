package com.lhs.lawmind.event;

/**
 * 知识数据创建事件 — 文档导入完成后发布，触发异步向量化
 */
public class KnowledgeCreatedEvent {

    private final String source;

    public KnowledgeCreatedEvent(String source) {
        this.source = source;
    }

    public String getSource() {
        return source;
    }

    @Override
    public String toString() {
        return "KnowledgeCreatedEvent{source='" + source + "'}";
    }
}
