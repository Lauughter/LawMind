package com.lhs.lawmind.controller;

import com.lhs.lawmind.agent.AgentRunner;
import com.lhs.lawmind.agent.gate.*;
import com.lhs.lawmind.agent.monitor.AgentMetricsCollector;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.dto.AgentAskRequest;
import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.Conversation;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.service.ConversationService;
import com.lhs.lawmind.service.RagMetricsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/agent")
public class AgentController {

    private final AgentRunner agentRunner;
    private final AgentMetricsCollector metricsCollector;
    private final IntentGate intentGate;
    private final FastChannelHandler fastChannelHandler;
    private final ConversationService conversationService;
    private final AiChatService aiChatService;
    private final RagMetricsService ragMetricsService;

    @Value("${lawmind.admin-user-id:1}")
    private Long adminUserId;

    public AgentController(AgentRunner agentRunner,
                           AgentMetricsCollector metricsCollector,
                           IntentGate intentGate,
                           FastChannelHandler fastChannelHandler,
                           ConversationService conversationService,
                           AiChatService aiChatService,
                           RagMetricsService ragMetricsService) {
        this.agentRunner = agentRunner;
        this.metricsCollector = metricsCollector;
        this.intentGate = intentGate;
        this.fastChannelHandler = fastChannelHandler;
        this.conversationService = conversationService;
        this.aiChatService = aiChatService;
        this.ragMetricsService = ragMetricsService;
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

        // ★ 会话管理：使用前端传入的 conversationId，否则自动创建
        Long conversationId = resolveConversationId(userId, request.getConversationId());

        metricsCollector.recordAgentCall(userId, question);

        // ★ 意图门控：在 Agent 循环前进行领域判断、意图分类、路由决策
        String mode = request.getMode();
        boolean manualAgentMode = "agent".equalsIgnoreCase(mode);

        GateResult gateResult;
        if (manualAgentMode) {
            log.info("[Agent] 前端手动 Agent 模式，跳过门控");
            gateResult = GateResult.accept(
                    DomainVerdict.legal("前端手动 Agent 模式"),
                    IntentResult.rule(IntentType.LEGAL_CONSULTATION, 1.0, "agent"),
                    RouteDecision.agent("前端手动 Agent 模式", 1500));
        } else {
            gateResult = intentGate.process(question.trim());
        }

        metricsCollector.recordGateProcess(gateResult);

        // P1-6 门控统计持久化：reject / 异常降级 计入 rag_metrics 日报
        if (!gateResult.accepted()) {
            ragMetricsService.recordRequest("gate_reject", 0, 0, 0, 0, 0, 0, 0.0, false, null);
        } else if (gateResult.routeDecision().strategy() != null
                && gateResult.routeDecision().strategy().contains("降级")) {
            ragMetricsService.recordRequest("gate_degraded", 0, 0, 0, 0, 0, 0, 0.0, false, null);
        }

        if (!gateResult.accepted()) {
            return rejectEmitter(gateResult.rejectResponse());
        }

        RouteDecision.Channel channel = gateResult.routeDecision().channel();

        return switch (channel) {
            case FAST -> handleFastChannel(question.trim(), gateResult, userId, conversationId);
            case HYBRID -> handleHybridChannel(question.trim(), gateResult, userId, conversationId);
            case AGENT -> handleAgentChannel(question.trim(), gateResult, userId, conversationId);
            case REJECT -> rejectEmitter(gateResult.rejectResponse());
        };
    }

    /**
     * 解析或创建会话 ID。
     */
    private Long resolveConversationId(Long userId, Long requestConversationId) {
        if (requestConversationId != null) {
            Conversation existing = conversationService.getById(requestConversationId);
            if (existing != null && existing.getUserId().equals(userId)) {
                conversationService.touch(requestConversationId);
                return requestConversationId;
            }
            log.warn("[Agent] 会话不存在或不属于当前用户: id={}, userId={}", requestConversationId, userId);
        }
        Conversation conv = conversationService.create(userId, "新对话");
        return conv.getId();
    }

    /**
     * 构建会话历史上下文文本（P1-4 多轮对话）。
     */
    private String buildConversationHistory(Long conversationId) {
        if (conversationId == null) return "";
        try {
            List<AiChat> history = aiChatService.selectByConversationId(conversationId);
            if (history == null || history.isEmpty()) return "";
            StringBuilder sb = new StringBuilder();
            for (AiChat chat : history) {
                sb.append("用户: ").append(chat.getUserQuestion()).append("\n");
                String answer = chat.getAiAnswer();
                if (answer != null && answer.length() > 500) {
                    answer = answer.substring(0, 500) + "...";
                }
                sb.append("助手: ").append(answer).append("\n\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("构建会话历史失败: conversationId={}, error={}", conversationId, e.getMessage());
            return "";
        }
    }

    /**
     * 快速通道：关键词检索 + 单次 LLM 生成，SSE 流式返回。
     */
    private SseEmitter handleFastChannel(String question, GateResult gateResult,
                                          Long userId, Long conversationId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        log.info("[Agent] 快速通道: intent={}", gateResult.intentResult().intentType());

        new Thread(() -> {
            try {
                String answer = fastChannelHandler.handle(question,
                        gateResult.intentResult().intentType());

                // ★ 持久化聊天记录
                Long chatId = saveChatRecord(userId, conversationId, question, answer, "agent_fast");

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
                        .data(buildDoneJson(conversationId, chatId, "fast")));
                emitter.complete();
                log.info("[Agent] 快速通道完成: userId={} conversationId={} chatId={}",
                        userId, conversationId, chatId);
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
    private SseEmitter handleHybridChannel(String question, GateResult gateResult,
                                            Long userId, Long conversationId) {
        log.info("[Agent] 混合通道（文书生成）: intent={}", gateResult.intentResult().intentType());
        return handleFastChannel(question, gateResult, userId, conversationId);
    }

    /**
     * Agent 通道：多步推理 + 工具调用。
     */
    private SseEmitter handleAgentChannel(String question, GateResult gateResult,
                                           Long userId, Long conversationId) {
        SseEmitter emitter = new SseEmitter(120_000L);
        log.info("[Agent] Agent 通道: intent={}, complexity={}",
                gateResult.intentResult().intentType(),
                gateResult.routeDecision().strategy());

        new Thread(() -> {
            try {
                // ★ P1-4 多轮对话：注入会话历史
                String history = buildConversationHistory(conversationId);
                // ★ P0-3 流式：通过回调逐步推送推理过程（Thought / ToolCall / 结果）
                AgentRunner.AgentResult result = agentRunner.execute(
                        question.trim(), null, userId, null, event -> {
                    try {
                        switch (event.type()) {
                            case THOUGHT -> emitter.send(SseEmitter.event()
                                    .name("agent_thought").data(event.content()));
                            case TOOL_CALL -> emitter.send(SseEmitter.event()
                                    .name("agent_tool_call").data(event.content()));
                            case TOOL_RESULT -> emitter.send(SseEmitter.event()
                                    .name("agent_tool_result").data(event.content()));
                            case FINAL -> { /* 最终答案在 execute 返回后统一流式发送 */ }
                        }
                    } catch (IOException e) {
                        log.warn("[Agent] 流式事件推送失败: {}", e.getMessage());
                    }
                }, history);

                if (result.success()) {
                    String answer = result.answer();

                    // ★ 持久化聊天记录
                    Long chatId = saveChatRecord(userId, conversationId, question, answer, "agent");

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
                            .data(buildDoneJson(conversationId, chatId, "agent")));
                    emitter.complete();
                    log.info("[Agent] Agent 通道完成: userId={} conversationId={} chatId={}",
                            userId, conversationId, chatId);
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
     * 持久化 Agent 模式聊天记录。
     */
    private Long saveChatRecord(Long userId, Long conversationId,
                                 String question, String answer, String source) {
        try {
            AiChat aiChat = new AiChat();
            aiChat.setUserId(userId);
            aiChat.setConversationId(conversationId);
            aiChat.setUserQuestion(question);
            aiChat.setAiAnswer(answer);
            aiChat.setKnowledgeMatch("[]");
            Long chatId = aiChatService.insert(aiChat);
            conversationService.touch(conversationId);
            log.info("[Agent] 聊天记录已保存: userId={} conversationId={} chatId={} source={}",
                    userId, conversationId, chatId, source);
            return chatId;
        } catch (Exception e) {
            log.error("[Agent] 保存聊天记录失败: userId={} conversationId={} source={} error={}",
                    userId, conversationId, source, e.getMessage(), e);
            return null;
        }
    }

    private String buildDoneJson(Long conversationId, Long chatId, String channel) {
        return String.format(
                "{\"status\":\"completed\",\"channel\":\"%s\",\"conversationId\":%d,\"chatId\":%d}",
                channel,
                conversationId != null ? conversationId : 0,
                chatId != null ? chatId : 0);
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
