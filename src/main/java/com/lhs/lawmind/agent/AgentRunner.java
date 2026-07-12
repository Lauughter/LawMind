package com.lhs.lawmind.agent;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.lhs.lawmind.agent.compress.ContextCompressor;
import com.lhs.lawmind.agent.memory.MemoryManager;
import com.lhs.lawmind.agent.monitor.AgentMetricsCollector;
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

@Slf4j
public class AgentRunner {

    private final ChatLanguageModel chatLanguageModel;
    private final String systemPrompt;
    private final Map<String, ToolMethod> toolRegistry = new LinkedHashMap<>();
    private final List<ToolSpecification> toolSpecifications = new ArrayList<>();
    private final int maxIterations;
    private final AgentMetricsCollector metricsCollector;
    private final ContextCompressor contextCompressor;
    private final MemoryManager memoryManager;

    private record ToolMethod(Object instance, Method method, String[] paramNames) {}

    public AgentRunner(ChatLanguageModel chatLanguageModel,
                       String systemPrompt,
                       List<Object> toolObjects,
                       int maxIterations,
                       AgentMetricsCollector metricsCollector,
                       ContextCompressor contextCompressor,
                       MemoryManager memoryManager) {
        this.chatLanguageModel = chatLanguageModel;
        this.systemPrompt = systemPrompt;
        this.maxIterations = maxIterations;
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
        Method method = toolMethod.method;
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        if (argumentsJson == null || argumentsJson.isBlank() || paramTypes.length == 0) {
            return args;
        }

        try {
            JSONObject json = JSONUtil.parseObj(argumentsJson);
            for (int i = 0; i < paramTypes.length; i++) {
                String paramName = toolMethod.paramNames[i];
                if (json.containsKey(paramName)) {
                    args[i] = json.get(paramName, paramTypes[i]);
                }
                if (args[i] == null && json.size() == paramTypes.length) {
                    int idx = 0;
                    for (String key : json.keySet()) {
                        if (idx == i) {
                            args[i] = json.get(key, paramTypes[i]);
                            break;
                        }
                        idx++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[Agent] 参数解析失败: json={}, error={}", argumentsJson, e.getMessage());
        }
        return args;
    }

    public AgentResult execute(String userQuestion) {
        return execute(userQuestion, systemPrompt);
    }

    /**
     * 使用自定义 System Prompt 执行 Agent 推理。
     * 合同审查等专项场景可通过此方法注入领域专用 Prompt，
     * 使 Agent 以特定角色和审查框架执行任务。
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
        // ★ 集成点 0：推理前注入记忆
        String enrichedPrompt = effectiveSystemPrompt;
        if (memoryManager != null && userId != null) {
            String memoryContext = memoryManager.retrieveAndFormat(userId, userQuestion);
            if (!memoryContext.isEmpty()) {
                enrichedPrompt = effectiveSystemPrompt + "\n" + memoryContext;
            }
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(enrichedPrompt));
        messages.add(UserMessage.from(userQuestion));

        for (int iteration = 0; iteration < maxIterations; iteration++) {
            log.info("[Agent] 第 {} 轮推理开始 (历史消息数={})", iteration + 1, messages.size());

            // ★ 集成点：全局上下文超阈值时，用 KnowledgeState 重建上下文
            if (contextCompressor != null && contextCompressor.needsCompression(messages)) {
                log.info("[Agent] 上下文超阈值，执行全局压缩");
                String knowledgeSummary = contextCompressor.buildFinalContext(userQuestion);
                messages = contextCompressor.buildFinalMessages(
                        enrichedPrompt, userQuestion, knowledgeSummary);
                // Force final answer
                try {
                    Response<AiMessage> finalResponse = chatLanguageModel.generate(messages);
                    triggerMemoryExtraction(userId, sessionId, messages);
                    return AgentResult.success(finalResponse.content().text(), messages);
                } catch (Exception e) {
                    log.error("[Agent] 压缩后最终答案生成失败: {}", e.getMessage(), e);
                    return AgentResult.error("回答生成失败: " + e.getMessage());
                }
            }

            Response<AiMessage> response;
            try {
                if (toolSpecifications.isEmpty()) {
                    response = chatLanguageModel.generate(messages);
                } else {
                    response = chatLanguageModel.generate(messages, toolSpecifications);
                }
            } catch (Exception e) {
                log.error("[Agent] 模型调用失败: {}", e.getMessage(), e);
                return AgentResult.error("模型调用失败: " + e.getMessage());
            }

            AiMessage aiMessage = response.content();

            if (aiMessage.hasToolExecutionRequests()) {
                log.info("[Agent] 第 {} 轮: 检测到 {} 个 Tool 调用请求",
                        iteration + 1, aiMessage.toolExecutionRequests().size());

                if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
                    log.debug("[Agent] 思考: {}", aiMessage.text().substring(0,
                            Math.min(200, aiMessage.text().length())));
                }

                messages.add(aiMessage);

                for (ToolExecutionRequest toolRequest : aiMessage.toolExecutionRequests()) {
                    log.info("[Agent] 调用 Tool: {} args={}", toolRequest.name(),
                            toolRequest.arguments());
                    String toolResult = executeTool(toolRequest);

                    // ★ 集成点 1：压缩工具结果（更新 KnowledgeState + 按策略压缩）
                    if (contextCompressor != null) {
                        int originalLen = toolResult.length();
                        toolResult = contextCompressor.compressToolResult(
                                toolRequest.name(), toolResult, messages, iteration + 1);
                        if (metricsCollector != null && toolResult.length() < originalLen) {
                            metricsCollector.recordCompression(
                                    originalLen, toolResult.length());
                        }
                    }

                    messages.add(ToolExecutionResultMessage.from(toolRequest, toolResult));
                }
                continue;
            }

            if (aiMessage.text() != null && !aiMessage.text().isBlank()) {
                log.info("[Agent] 第 {} 轮: 模型返回最终答案 (长度={})",
                        iteration + 1, aiMessage.text().length());
                triggerMemoryExtraction(userId, sessionId, messages);
                return AgentResult.success(aiMessage.text(), messages);
            }

            log.warn("[Agent] 第 {} 轮: 模型返回空内容，重试", iteration + 1);
        }

        // ★ 集成点 2：达到最大迭代次数，用 KnowledgeState 构建精简上下文
        log.info("[Agent] 达最大迭代次数({})，用结构化知识摘要构建最终上下文", maxIterations);
        if (contextCompressor != null) {
            String knowledgeSummary = contextCompressor.buildFinalContext(userQuestion);
            messages = contextCompressor.buildFinalMessages(
                    effectiveSystemPrompt, userQuestion, knowledgeSummary);
        } else {
            messages.add(UserMessage.from("请基于以上所有检索到的信息，直接给出最终回答，不要再调用工具。"));
        }

        try {
            Response<AiMessage> finalResponse = chatLanguageModel.generate(messages);
            triggerMemoryExtraction(userId, sessionId, messages);
            return AgentResult.success(finalResponse.content().text(), messages);
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
}
