package com.lhs.lawmind.agent;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lhs.lawmind.agent.compress.ContextCompressor;
import com.lhs.lawmind.agent.memory.MemoryManager;
import com.lhs.lawmind.agent.monitor.AgentMetricsCollector;
import com.lhs.lawmind.llm.LLMInvoker;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Slf4j
public class AgentRunner {

    private final LLMInvoker llmInvoker;
    private final String systemPrompt;
    private final Map<String, ToolMethod> toolRegistry = new LinkedHashMap<>();
    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    private final int maxIterations;
    private final long maxDurationMs;
    private final AgentMetricsCollector metricsCollector;
    private final ContextCompressor contextCompressor;
    private final MemoryManager memoryManager;

    private record ToolMethod(Object instance, Method method, String[] paramNames) {}

    public AgentRunner(LLMInvoker llmInvoker,
                       String systemPrompt,
                       List<Object> toolObjects,
                       int maxIterations,
                       long maxDurationMs,
                       AgentMetricsCollector metricsCollector,
                       ContextCompressor contextCompressor,
                       MemoryManager memoryManager) {
        this.llmInvoker = llmInvoker;
        this.systemPrompt = systemPrompt;
        this.maxIterations = maxIterations;
        this.maxDurationMs = maxDurationMs;
        this.metricsCollector = metricsCollector;
        this.contextCompressor = contextCompressor;
        this.memoryManager = memoryManager;
        for (Object toolObject : toolObjects) {
            registerTools(toolObject);
        }
    }

    private void registerTools(Object toolObject) {
        List<ToolSpecification> specs = ToolSpecifications.toolSpecificationsFrom(toolObject);
        toolSpecifications.addAll(specs);

        for (Method method : toolObject.getClass().getDeclaredMethods()) {
            Tool tool = method.getAnnotation(Tool.class);
            if (tool == null) {
                continue;
            }
            String toolName = resolveToolName(tool, method);
            String[] paramNames = resolveParamNames(method);
            toolRegistry.put(toolName, new ToolMethod(toolObject, method, paramNames));
            log.info("[Agent] 注册 Tool: {} (方法: {}, 参数: {})",
                    toolName, method.getName(), paramNames.length);
        }
    }

    private String resolveToolName(Tool tool, Method method) {
        if (!tool.name().isEmpty()) {
            return tool.name();
        }
        return method.getName();
    }

    private String[] resolveParamNames(Method method) {
        Parameter[] parameters = method.getParameters();
        String[] names = new String[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isNamePresent()) {
                names[i] = parameters[i].getName();
            } else {
                names[i] = "arg" + i;
            }
        }
        return names;
    }

    private String executeTool(ToolExecutionRequest request) {
        ToolMethod toolMethod = toolRegistry.get(request.name());
        if (toolMethod == null) {
            log.warn("[Agent] Tool 未注册: {}", request.name());
            return "[Tool 错误] 未知工具: " + request.name();
        }

        try {
            Object[] args = resolveArgs(toolMethod, request.arguments());
            Object result = toolMethod.method.invoke(toolMethod.instance, args);
            String resultStr = result != null ? result.toString() : "";
            log.info("[Agent] Tool 执行成功: {} 返回长度={}", request.name(), resultStr.length());
            if (metricsCollector != null) {
                metricsCollector.recordToolCall(request.name());
            }
            return resultStr;
        } catch (Exception e) {
            log.error("[Agent] Tool 执行失败: {} error={}", request.name(), e.getMessage(), e);
            return "[Tool 错误] " + request.name() + " 执行失败: " + e.getMessage();
        }
    }

    private Object[] resolveArgs(ToolMethod toolMethod, String argumentsJson) {
        Class<?>[] paramTypes = toolMethod.method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        if (argumentsJson == null || argumentsJson.isBlank() || paramTypes.length == 0) {
            return args;
        }

        // P2-10/11：只按参数名匹配（移除脆弱的按位置兜底）；缺失参数保持 null（兼容可选参数）
        JSONObject json = JSONUtil.parseObj(argumentsJson); // 解析失败由 executeTool 捕获并回传模型
        for (int i = 0; i < paramTypes.length; i++) {
            String paramName = toolMethod.paramNames[i];
            if (json.containsKey(paramName)) {
                args[i] = parseArg(json.get(paramName), paramTypes[i], paramName);
            }
        }
        return args;
    }

    /**
     * 按目标类型解析工具参数（支持 String / 数值 / 布尔 / List / 复杂对象）。
     *
     * @throws IllegalArgumentException 类型不匹配或解析失败（由 executeTool 转为错误返回给模型重试）
     */
    private Object parseArg(Object value, Class<?> type, String paramName) {
        if (value == null) return null;
        try {
            if (type == String.class) {
                return value.toString();
            }
            if (type == int.class || type == Integer.class) {
                return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
            }
            if (type == long.class || type == Long.class) {
                return value instanceof Number n ? n.longValue() : Long.parseLong(value.toString());
            }
            if (type == double.class || type == Double.class) {
                return value instanceof Number n ? n.doubleValue() : Double.parseDouble(value.toString());
            }
            if (type == boolean.class || type == Boolean.class) {
                return Boolean.parseBoolean(value.toString());
            }
            if (List.class.isAssignableFrom(type)) {
                return JSONUtil.parseArray(value).toList(Object.class);
            }
            // 复杂对象：反序列化为目标类型
            return JSONUtil.toBean(value instanceof JSONObject jo ? jo : JSONUtil.parseObj(value.toString()), type);
        } catch (Exception e) {
            throw new IllegalArgumentException("参数 " + paramName + " 解析失败（期望 " + type.getSimpleName() + "）: " + e.getMessage());
        }
    }

    public AgentResult execute(String userQuestion) {
        return execute(userQuestion, systemPrompt);
    }

    /**
     * 使用自定义 System Prompt 执行 Agent 推理。
     * 专项场景可通过此方法注入领域专用 Prompt，
     * 使 Agent 以特定角色和框架执行任务。
     */
    public AgentResult execute(String userQuestion, String effectiveSystemPrompt) {
        return execute(userQuestion, effectiveSystemPrompt, null, null);
    }

    /**
     * 执行 Agent 推理（含记忆注入和异步提取）。
     * @param userId 用户 ID（用于记忆检索，nullable）
     * @param sessionId 会话 ID（用于记忆溯源，nullable）
     */
    public AgentResult execute(String userQuestion, String effectiveSystemPrompt,
                                Long userId, Long sessionId) {
        return execute(userQuestion, effectiveSystemPrompt, userId, sessionId, event -> {}, null);
    }

    /**
     * 执行 Agent 推理（含记忆注入、异步提取与流式事件回调）。
     * <p>通过 {@code eventConsumer} 逐步推送推理过程（Thought / ToolCall / ToolResult / Final），
     * 供 SSE 实时转发到前端，避免用户等待整个 ReAct 循环完成。</p>
     *
     * @param userId        用户 ID（用于记忆检索，nullable）
     * @param sessionId     会话 ID（用于记忆溯源，nullable）
     * @param eventConsumer 流式事件回调，nullable（不传则静默）
     */
    public AgentResult execute(String userQuestion, String effectiveSystemPrompt,
                                Long userId, Long sessionId,
                                Consumer<AgentEvent> eventConsumer) {
        return execute(userQuestion, effectiveSystemPrompt, userId, sessionId, eventConsumer, null);
    }

    /**
     * 执行 Agent 推理（含记忆注入、会话历史、异步提取与流式事件回调）。
     *
     * @param userId             用户 ID（用于记忆检索，nullable）
     * @param sessionId          会话 ID（用于记忆溯源，nullable）
     * @param eventConsumer      流式事件回调，nullable
     * @param conversationHistory 历史对话文本（「用户: ...\n助手: ...」格式），nullable
     */
    public AgentResult execute(String userQuestion, String effectiveSystemPrompt,
                                Long userId, Long sessionId,
                                Consumer<AgentEvent> eventConsumer,
                                String conversationHistory) {
        Consumer<AgentEvent> events = eventConsumer != null ? eventConsumer : event -> {};
        List<ChatMessage> messages = buildInitialMessages(userQuestion, effectiveSystemPrompt, userId, conversationHistory);
        long loopStart = System.currentTimeMillis();

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            log.info("[Agent] 第 {} 轮推理开始 (历史消息数={})", iteration + 1, messages.size());

            // P2-9 总耗时超时：整体超过 maxDurationMs 则提前收尾
            if (System.currentTimeMillis() - loopStart > maxDurationMs) {
                log.warn("[Agent] 推理总耗时超限({}ms)，强制生成最终答案", maxDurationMs);
                return generateFinalAnswer(userQuestion, effectiveSystemPrompt,
                        userId, sessionId, messages, events,
                        "推理总耗时超限(" + maxDurationMs + "ms)，强制生成最终答案");
            }

            // 全局上下文超阈值 → 结构化摘要替换并强制最终回答
            if (contextCompressor != null && contextCompressor.needsCompression(messages)) {
                log.info("[Agent] 上下文超阈值，执行全局压缩");
                return generateFinalAnswer(userQuestion, effectiveSystemPrompt,
                        userId, sessionId, messages, events,
                        "上下文超阈值，用结构化知识摘要替换全部对话");
            }

            LLMInvoker.LLMResult llmResult = llmInvoker.invoke(messages, toolSpecifications);
            if (!llmResult.success()) {
                log.error("[Agent] 模型调用失败: {}", llmResult.answer());
                return AgentResult.error("模型调用失败: " + llmResult.answer());
            }
            AiMessage aiMessage = llmResult.aiMessage();

            if (aiMessage.hasToolExecutionRequests()) {
                if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
                    events.accept(new AgentEvent(AgentEventType.THOUGHT, aiMessage.text()));
                }
                messages = handleToolCalls(messages, aiMessage, iteration + 1, events);
                continue;
            }

            if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
                log.info("[Agent] 第 {} 轮: 模型返回最终答案 (长度={})",
                        iteration + 1, aiMessage.text().length());
                events.accept(new AgentEvent(AgentEventType.FINAL, aiMessage.text()));
                triggerMemoryExtraction(userId, sessionId, messages);
                return AgentResult.success(aiMessage.text(), messages);
            }

            log.warn("[Agent] 第 {} 轮: 模型返回空内容，重试", iteration + 1);
        }

        // 达到最大迭代次数
        return generateFinalAnswer(userQuestion, effectiveSystemPrompt,
                userId, sessionId, messages, events,
                "达最大迭代次数(" + maxIterations + ")，用结构化知识摘要构建最终上下文");
    }

    /**
     * 构建初始消息列表：系统提示 + 记忆注入 + 用户问题。
     */
    private List<ChatMessage> buildInitialMessages(String userQuestion,
                                                   String effectiveSystemPrompt, Long userId,
                                                   String conversationHistory) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(effectiveSystemPrompt));
        if (memoryManager != null && userId != null) {
            String memoryContext = memoryManager.retrieveAndFormat(userId, userQuestion);
            if (!memoryContext.isEmpty()) {
                // 记忆作为独立 SystemMessage，避免与系统指令混为一体
                messages.add(SystemMessage.from(memoryContext));
            }
        }
        if (conversationHistory != null && !conversationHistory.isBlank()) {
            messages.add(SystemMessage.from("以下是本次会话的历史对话，请结合它们理解上下文：\n" + conversationHistory));
        }
        messages.add(UserMessage.from(userQuestion));
        return messages;
    }

    /**
     * 执行一轮的所有 Tool 调用并压缩结果，返回更新后的消息列表。
     */
    private List<ChatMessage> handleToolCalls(List<ChatMessage> messages,
                                              AiMessage aiMessage, int roundIndex,
                                              Consumer<AgentEvent> events) {
        log.info("[Agent] 第 {} 轮: 检测到 {} 个 Tool 调用请求",
                roundIndex, aiMessage.toolExecutionRequests().size());

        messages.add(aiMessage);

        for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
            log.info("[Agent] 调用 Tool: {} args={}", toolRequest.name(),
                    toolRequest.arguments());
            events.accept(new AgentEvent(AgentEventType.TOOL_CALL,
                    toolRequest.name() + "(" + toolRequest.arguments() + ")"));

            String toolResult = executeTool(toolRequest);

            // 压缩工具结果（更新 KnowledgeState + 按策略压缩）
            if (contextCompressor != null) {
                int originalLen = toolResult.length();
                toolResult = contextCompressor.compressToolResult(
                        toolRequest.name(), toolResult, messages, roundIndex);
                if (metricsCollector != null && toolResult.length() < originalLen) {
                    metricsCollector.recordCompression(
                            originalLen, toolResult.length());
                }
            }

            events.accept(new AgentEvent(AgentEventType.TOOL_RESULT,
                    toolResult.length() > 2000 ? toolResult.substring(0, 2000) + "…" : toolResult));
            messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult));
        }
        return messages;
    }

    /**
     * 用 KnowledgeState 结构化摘要构建最终消息并生成回答（全局压缩 / 达最大迭代次数共用）。
     */
    private AgentResult generateFinalAnswer(String userQuestion, String effectiveSystemPrompt,
                                            Long userId, Long sessionId,
                                            List<ChatMessage> messages, Consumer<AgentEvent> events,
                                            String reason) {
        log.info("[Agent] {}", reason);
        if (contextCompressor != null) {
            String knowledgeSummary = contextCompressor.buildFinalContext(userQuestion);
            messages = contextCompressor.buildFinalMessages(
                    effectiveSystemPrompt, userQuestion, knowledgeSummary);
        } else {
            messages.add(UserMessage.from("请基于以上所有检索到的信息，直接给出最终回答，不要再调用工具。"));
        }

        try {
            LLMInvoker.LLMResult finalResult = llmInvoker.invoke(messages);
            if (!finalResult.success()) {
                return AgentResult.error("回答生成失败: " + finalResult.answer());
            }
            events.accept(new AgentEvent(AgentEventType.FINAL, finalResult.answer()));
            triggerMemoryExtraction(userId, sessionId, messages);
            return AgentResult.success(finalResult.answer(), messages);
        } catch (Exception e) {
            log.error("[Agent] 最终答案生成失败: {}", e.getMessage(), e);
            return AgentResult.error("回答生成失败: " + e.getMessage());
        }
    }

    /**
     * 触发异步记忆提取（仅在成功回答且有 userId 时）。
     */
    private void triggerMemoryExtraction(Long userId, Long sessionId, List<ChatMessage> messages) {
        if (memoryManager != null && userId != null) {
            try {
                memoryManager.extractAsync(userId, sessionId, messages);
            } catch (Exception e) {
                log.warn("记忆异步提取调度失败: {}", e.getMessage());
            }
        }
    }

    public record AgentResult(String answer, List<ChatMessage> conversationHistory, boolean success) {
        public static AgentResult success(String answer, List<ChatMessage> history) {
            return new AgentResult(answer, history, true);
        }

        public static AgentResult error(String errorMessage) {
            return new AgentResult(errorMessage, List.of(), false);
        }
    }

    /**
     * Agent 推理过程中的流式事件（供 SSE 实时转发）。
     */
    public enum AgentEventType { THOUGHT, TOOL_CALL, TOOL_RESULT, FINAL }

    public record AgentEvent(AgentEventType type, String content) {}
}
