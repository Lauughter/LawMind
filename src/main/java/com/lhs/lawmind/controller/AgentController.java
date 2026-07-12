package com.lhs.lawmind.controller;

import com.lhs.lawmind.agent.AgentRunner;
import com.lhs.lawmind.agent.gate.*;
import com.lhs.lawmind.agent.monitor.AgentMetricsCollector;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.dto.AgentAskRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentRunner agentRunner;
    private final AgentMetricsCollector metricsCollector;
    private final IntentGate intentGate;
    private final FastChannelHandler fastChannelHandler;

    @Value("${lawmind.admin-user-id:1}")
    private Long adminUserId;

    public AgentController(AgentRunner agentRunner,
                           AgentMetricsCollector metricsCollector,
                           IntentGate intentGate,
                           FastChannelHandler fastChannelHandler) {
        this.agentRunner = agentRunner;
        this.metricsCollector = metricsCollector;
        this.intentGate = intentGate;
        this.fastChannelHandler = fastChannelHandler;
    }

    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter askStream(@RequestBody AgentAskRequest request) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return errorEmitter("用户未登录，请先登录");
        }

        String question = request.getQuestion();
        if (question == null || question.trim().isEmpty()) {
            return errorEmitter("问题不能为空");
        }

        metricsCollector.recordAgentCall(userId, question);

        // ★ 意图门控：在 Agent 循环前进行领域判断、意图分类、路由决策
        String mode = request.getMode();
        boolean manualAgentMode = "agent".equalsIgnoreCase(mode);

        GateResult gateResult;
        if (manualAgentMode) {
            // 前端手动切换到 Agent 模式 → 跳过门控，直接走 Agent 通道
            log.info("[Agent] 前端手动 Agent 模式，跳过门控");
            gateResult = GateResult.accept(
                    DomainVerdict.legal("前端手动 Agent 模式"),
                    IntentResult.rule(IntentType.LEGAL_CONSULTATION, 1.0, "agent"),
                    RouteDecision.agent("前端手动 Agent 模式", 1500));
        } else {
            gateResult = intentGate.process(question.trim());
        }

        // 记录门控统计
        metricsCollector.recordGateProcess(gateResult);

        if (!gateResult.accepted()) {
            return rejectEmitter(gateResult.rejectResponse());
        }

        RouteDecision.Channel channel = gateResult.routeDecision().channel();

        return switch (channel) {
            case FAST -> handleFastChannel(question.trim(), gateResult, userId);
            case HYBRID -> handleHybridChannel(question.trim(), gateResult, userId);
            case AGENT -> handleAgentChannel(question.trim(), gateResult, userId);
            case REJECT -> rejectEmitter(gateResult.rejectResponse());
        };
    }

    /**
     * 快速通道：关键词检索 + 单次 LLM 生成，SSE 流式返回。
     */
    private SseEmitter handleFastChannel(String question, GateResult gateResult, Long userId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        log.info("[Agent] 快速通道: intent={}", gateResult.intentResult().intentType());

        new Thread(() -> {
            try {
                String answer = fastChannelHandler.handle(question,
                        gateResult.intentResult().intentType());

                for (int i = 0; i < answer.length(); i++) {
                    emitter.send(SseEmitter.event()
                            .name("message")
                            .data(String.valueOf(answer.charAt(i))));
                    if (i % 3 == 0) {
                        Thread.sleep(5);
                    }
                }
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("{\"status\":\"completed\",\"channel\":\"fast\"}"));
                emitter.complete();
                log.info("[Agent] 快速通道完成: userId={}", userId);
            } catch (IOException e) {
                log.error("[Agent] 快速通道 SSE 推送失败: userId={}, error={}", userId, e.getMessage());
                emitter.completeWithError(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("[Agent] 快速通道异常: userId={}, error={}", userId, e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"快速通道处理失败，请稍后重试\"}"));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }, "fast-" + userId).start();

        return emitter;
    }

    /**
     * 混合通道：主要用于文书生成。当前版本复用快速通道处理。
     */
    private SseEmitter handleHybridChannel(String question, GateResult gateResult, Long userId) {
        log.info("[Agent] 混合通道（文书生成）: intent={}", gateResult.intentResult().intentType());
        // 当前版本：混合通道复用快速通道（后续可增强为模板+参数化）
        return handleFastChannel(question, gateResult, userId);
    }

    /**
     * Agent 通道：多步推理 + 工具调用。
     */
    private SseEmitter handleAgentChannel(String question, GateResult gateResult, Long userId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        log.info("[Agent] Agent 通道: intent={}, complexity={}",
                gateResult.intentResult().intentType(),
                gateResult.routeDecision().strategy());

        new Thread(() -> {
            try {
                AgentRunner.AgentResult result = agentRunner.execute(
                        question.trim(), null, userId, null);

                if (result.success()) {
                    String answer = result.answer();
                    for (int i = 0; i < answer.length(); i++) {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(String.valueOf(answer.charAt(i))));
                        if (i % 3 == 0) {
                            Thread.sleep(5);
                        }
                    }
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("{\"status\":\"completed\",\"channel\":\"agent\"}"));
                    emitter.complete();
                    log.info("[Agent] Agent 通道完成: userId={}", userId);
                } else {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"" + result.answer() + "\"}"));
                    emitter.complete();
                }
            } catch (IOException e) {
                log.error("[Agent] Agent 通道 SSE 推送失败: userId={}, error={}",
                        userId, e.getMessage());
                emitter.completeWithError(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("[Agent] Agent 通道异常: userId={}, error={}",
                        userId, e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"回答生成失败，请稍后重试\"}"));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }, "agent-" + userId).start();

        return emitter;
    }

    /**
     * 拒绝通道：返回分级拒绝响应。
     */
    private SseEmitter rejectEmitter(String message) {
        SseEmitter emitter = new SseEmitter();
        try {
            for (int i = 0; i < message.length(); i++) {
                emitter.send(SseEmitter.event()
                        .name("message")
                        .data(String.valueOf(message.charAt(i))));
                if (i % 3 == 0) {
                    Thread.sleep(5);
                }
            }
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data("{\"status\":\"rejected\"}"));
            emitter.complete();
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
        return emitter;
    }

    private SseEmitter errorEmitter(String message) {
        SseEmitter errorEmitter = new SseEmitter();
        try {
            errorEmitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"message\":\"" + message + "\"}"));
            errorEmitter.complete();
        } catch (IOException e) {
            errorEmitter.completeWithError(e);
        }
        return errorEmitter;
    }

    @GetMapping("/metrics")
    public Result<?> metrics() {
        Long userId = RequestContext.getUserId();
        if (userId == null || !userId.equals(adminUserId)) {
            return Result.error(403, "无权访问，仅限管理员");
        }

        AgentMetricsCollector.AgentMetricsSnapshot snapshot = metricsCollector.getSnapshot();
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("totalAgentCalls", snapshot.totalAgentCalls());
        data.put("totalToolCalls", snapshot.totalToolCalls());
        data.put("totalFallbackCalls", snapshot.totalFallbackCalls());
        data.put("totalCompressions", snapshot.totalCompressions());
        data.put("estimatedTokensSaved", snapshot.estimatedTokensSaved());
        data.put("knowledgeStateAtomCounts", snapshot.knowledgeStateAtomCounts());
        data.put("toolCallCounts", snapshot.toolCallCounts());
        Map<String, Object> gateStats = new java.util.LinkedHashMap<>();
        gateStats.put("channelCounts", snapshot.gateChannelCounts());
        gateStats.put("totalRejects", snapshot.totalGateRejects());
        data.put("gateStats", gateStats);
        data.put("startTime", snapshot.startTime().toString());
        return Result.success(data);
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Agent service is running");
    }
}
