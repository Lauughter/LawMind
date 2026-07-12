package com.lhs.lawmind.agent.monitor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Agent Token 消耗、Tool 调用次数、压缩统计追踪器。
 * 使用 AtomicLong + ConcurrentHashMap 保证线程安全。
 */
@Slf4j
@Component
public class AgentMetricsCollector {

    private final AtomicLong totalAgentCalls = new AtomicLong(0);
    private final AtomicLong totalToolCalls = new AtomicLong(0);
    private final AtomicLong totalFallbackCalls = new AtomicLong(0);
    private final AtomicLong totalCompressions = new AtomicLong(0);
    private final AtomicLong estimatedTokensSaved = new AtomicLong(0);
    private final ConcurrentHashMap<String, AtomicLong> toolCallCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> knowledgeStateAtomCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, AtomicLong> gateChannelCounts = new ConcurrentHashMap<>();
    private final AtomicLong totalGateRejects = new AtomicLong(0);
    private final LocalDateTime startTime = LocalDateTime.now();

    public void recordAgentCall(Long userId, String question) {
        totalAgentCalls.incrementAndGet();
        log.info("[Agent Metrics] 新请求: userId={}, questionLen={}, totalCalls={}",
                userId, question != null ? question.length() : 0, totalAgentCalls.get());
    }

    public void recordToolCall(String toolName) {
        totalToolCalls.incrementAndGet();
        toolCallCounts.computeIfAbsent(toolName, k -> new AtomicLong(0)).incrementAndGet();
        log.debug("[Agent Metrics] Tool调用: tool={}, totalToolCalls={}", toolName, totalToolCalls.get());
    }

    public void recordFallback(String reason) {
        totalFallbackCalls.incrementAndGet();
        log.warn("[Agent Metrics] Agent降级: reason={}, totalFallbacks={}",
                reason, totalFallbackCalls.get());
    }

    /**
     * 记录一次压缩操作及其节省的 token 数（估算）。
     */
    public void recordCompression(int originalTokens, int compressedTokens) {
        totalCompressions.incrementAndGet();
        int saved = originalTokens - compressedTokens;
        if (saved > 0) {
            estimatedTokensSaved.addAndGet(saved);
        }
        log.debug("[Agent Metrics] 压缩: original={}, compressed={}, saved={}, totalSaved={}",
                originalTokens, compressedTokens, saved, estimatedTokensSaved.get());
    }

    /**
     * 记录门控处理结果。
     */
    public void recordGateProcess(com.lhs.lawmind.agent.gate.GateResult gateResult) {
        if (gateResult.accepted()) {
            String channel = gateResult.routeDecision().channel().name();
            gateChannelCounts.computeIfAbsent(channel, k -> new AtomicLong(0)).incrementAndGet();
        } else {
            totalGateRejects.incrementAndGet();
        }
    }

    /**
     * 记录 KnowledgeState 知识原子统计。
     */
    public void recordKnowledgeAtom(String type) {
        knowledgeStateAtomCounts
                .computeIfAbsent(type, k -> new AtomicLong(0))
                .incrementAndGet();
    }

    public AgentMetricsSnapshot getSnapshot() {
        Map<String, Long> toolCounts = new LinkedHashMap<>();
        toolCallCounts.forEach((name, count) -> toolCounts.put(name, count.get()));

        Map<String, Long> atomCounts = new LinkedHashMap<>();
        knowledgeStateAtomCounts.forEach((type, count) -> atomCounts.put(type, count.get()));

        Map<String, Long> gateCounts = new LinkedHashMap<>();
        gateChannelCounts.forEach((channel, count) -> gateCounts.put(channel, count.get()));

        return new AgentMetricsSnapshot(
                totalAgentCalls.get(),
                totalToolCalls.get(),
                totalFallbackCalls.get(),
                toolCounts,
                totalCompressions.get(),
                estimatedTokensSaved.get(),
                atomCounts,
                gateCounts,
                totalGateRejects.get(),
                startTime
        );
    }

    public record AgentMetricsSnapshot(
            long totalAgentCalls,
            long totalToolCalls,
            long totalFallbackCalls,
            Map<String, Long> toolCallCounts,
            long totalCompressions,
            long estimatedTokensSaved,
            Map<String, Long> knowledgeStateAtomCounts,
            Map<String, Long> gateChannelCounts,
            long totalGateRejects,
            LocalDateTime startTime
    ) {}
}
