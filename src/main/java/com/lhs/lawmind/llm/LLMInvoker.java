package com.lhs.lawmind.llm;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.output.Response;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * LLM 统一调用封装层。
 *
 * <p>集中处理模型调用、异常降级、token 统计与日志，消除各业务类中
 * 重复的 {@code chatLanguageModel.generate()} 样板代码。新增超时/重试/
 * 监控时只需在此处修改，全系统生效。</p>
 */
@Slf4j
@Component
public class LLMInvoker {

    private final ChatLanguageModel chatModel;
    private final StreamingChatLanguageModel streamingModel;

    public LLMInvoker(Optional<ChatLanguageModel> chatModel,
                      Optional<StreamingChatLanguageModel> streamingModel) {
        this.chatModel = chatModel.orElse(null);
        this.streamingModel = streamingModel.orElse(null);
    }

    /**
     * 无工具调用。
     */
    public LLMResult invoke(List<ChatMessage> messages) {
        return invoke(messages, null);
    }

    /**
     * 带工具调用（tools 为空时退化为普通调用）。
     */
    public LLMResult invoke(List<ChatMessage> messages, List<ToolSpecification> tools) {
        if (chatModel == null) {
            log.warn("[LLM] ChatLanguageModel 未初始化");
            return LLMResult.fallback("AI 服务暂时不可用，请稍后再试。");
        }
        long t0 = System.currentTimeMillis();
        try {
            Response<AiMessage> response = (tools == null || tools.isEmpty())
                    ? chatModel.generate(messages)
                    : chatModel.generate(messages, tools);
            AiMessage content = response.content();
            String text = content != null && content.text() != null ? content.text() : "";
            int input = response.tokenUsage() != null ? response.tokenUsage().inputTokenCount() : 0;
            int output = response.tokenUsage() != null ? response.tokenUsage().outputTokenCount() : 0;
            log.info("[LLM] generate ok: msgs={} in={} out={} elapsed={}ms",
                    messages.size(), input, output, System.currentTimeMillis() - t0);
            return new LLMResult(text, input, output, content, true);
        } catch (Exception e) {
            log.error("[LLM] generate failed: {}", e.getMessage(), e);
            return LLMResult.fallback(buildFallbackMessage(e));
        }
    }

    /**
     * 流式调用（SSE）。
     */
    public void invokeStreaming(List<ChatMessage> messages,
                                StreamingResponseHandler<AiMessage> handler) {
        if (streamingModel == null) {
            log.warn("[LLM] StreamingChatLanguageModel 未初始化");
            handler.onError(new IllegalStateException("StreamingChatLanguageModel 未初始化"));
            return;
        }
        streamingModel.generate(messages, handler);
    }

    public boolean isAvailable() {
        return chatModel != null;
    }

    public boolean isStreamingAvailable() {
        return streamingModel != null;
    }

    private String buildFallbackMessage(Exception e) {
        String msg = e.getMessage();
        if (msg != null && (msg.contains("InvalidApiKey") || msg.contains("Invalid API-key") || msg.contains("401"))) {
            return "AI 服务未配置有效的API密钥，请联系管理员设置DASHSCOPE_API_KEY环境变量。";
        }
        return "抱歉，AI 回答生成失败，请稍后再试。";
    }

    /**
     * 模型调用结果。
     *
     * @param aiMessage 完整消息（含 toolExecutionRequests，Agent 推理使用）；失败时为 null
     */
    public record LLMResult(String answer, int inputTokens, int outputTokens,
                            AiMessage aiMessage, boolean success) {
        public static LLMResult fallback(String message) {
            return new LLMResult(message, 0, 0, null, false);
        }
    }
}
