package com.lhs.lawmind.rag;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 流式安全发送工具 —— 防止 complete 后再 send、统一 flush。
 * 从 RagServiceImpl 拆出。
 */
public final class SseStreamHelper {

    private SseStreamHelper() {}

    /**
     * 安全地向 SseEmitter 发送事件，防止 complete 后再 send。
     */
    public static void safeSend(SseEmitter emitter, AtomicBoolean completed, SseEmitter.SseEventBuilder event) {
        if (completed.get()) return;
        try {
            emitter.send(event);
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletResponse response = attrs.getResponse();
                if (response != null) {
                    response.flushBuffer();
                }
            }
        } catch (IOException e) {
            safeComplete(emitter, completed);
        }
    }

    /**
     * 安全地完成 SseEmitter。
     */
    public static void safeComplete(SseEmitter emitter, AtomicBoolean completed) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.complete();
            } catch (Exception e) {
                // 可忽略
            }
        }
    }

    /**
     * 安全地以错误完成 SseEmitter。
     */
    public static void safeCompleteWithError(SseEmitter emitter, AtomicBoolean completed, Throwable ex) {
        if (completed.compareAndSet(false, true)) {
            try {
                emitter.completeWithError(ex);
            } catch (Exception e) {
                // 可忽略
            }
        }
    }

    /**
     * 转义 JSON 字符串中的特殊字符。
     */
    public static String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                case '\b': sb.append("\\b"); break;
                case '\f': sb.append("\\f"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }
}
