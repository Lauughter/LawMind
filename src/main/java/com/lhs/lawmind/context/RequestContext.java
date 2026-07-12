package com.lhs.lawmind.context;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 统一的请求上下文
 * 
 * 功能说明：
 * 1. 用户上下文（userId）- 从 UserContext 迁移
 * 2. 请求追踪（requestId）- 用于日志追踪
 * 3. 会话上下文（conversationId）- AI 对话会话
 * 4. 扩展属性（extra）- 自定义扩展
 * 
 * ThreadLocal 说明：
 * - 使用 ThreadLocal 存储每个线程的上下文
 * - 请求结束后必须调用 clear()，防止内存泄漏
 * - 配合拦截器自动管理
 * 
 * @author LawMind
 */
public class RequestContext {

    // 用户 ID
    private static final ThreadLocal<Long> USER_ID_HOLDER = new ThreadLocal<>();

    // 用户角色
    private static final ThreadLocal<String> ROLE_HOLDER = new ThreadLocal<>();

    // 请求 ID
    private static final ThreadLocal<String> REQUEST_ID_HOLDER = new ThreadLocal<>();

    // 会话 ID
    private static final ThreadLocal<String> CONVERSATION_ID_HOLDER = new ThreadLocal<>();

    // 扩展属性
    private static final ThreadLocal<Map<String, Object>> EXTRA_HOLDER = new ThreadLocal<>();

    // 构造方法（私有，防止实例化）
    private RequestContext() {
    }

    /**
     * 设置用户 ID
     */
    public static void setUserId(Long userId) {
        USER_ID_HOLDER.set(userId);
    }

    /**
     * 获取用户 ID
     */
    public static Long getUserId() {
        return USER_ID_HOLDER.get();
    }

    /**
     * 设置用户角色
     */
    public static void setRole(String role) {
        ROLE_HOLDER.set(role);
    }

    /**
     * 获取用户角色
     */
    public static String getRole() {
        return ROLE_HOLDER.get();
    }

    /**
     * 检查用户是否为管理员
     */
    public static boolean isAdmin() {
        return "admin".equals(ROLE_HOLDER.get());
    }

    /**
     * 检查用户是否已登录
     */
    public static boolean isLoggedIn() {
        return getUserId() != null;
    }

    /**
     * 生成并设置请求 ID
     * 格式：req-{UUID 前8位}
     */
    public static String generateRequestId() {
        String requestId = "req-" + UUID.randomUUID().toString().substring(0, 8);
        REQUEST_ID_HOLDER.set(requestId);
        return requestId;
    }

    /**
     * 设置请求 ID
     */
    public static void setRequestId(String requestId) {
        REQUEST_ID_HOLDER.set(requestId);
    }

    /**
     * 获取请求 ID
     */
    public static String getRequestId() {
        return REQUEST_ID_HOLDER.get();
    }

    /**
     * 设置会话 ID
     */
    public static void setConversationId(String conversationId) {
        CONVERSATION_ID_HOLDER.set(conversationId);
    }

    /**
     * 获取会话 ID
     */
    public static String getConversationId() {
        return CONVERSATION_ID_HOLDER.get();
    }

    /**
     * 设置扩展属性
     */
    public static void setExtra(String key, Object value) {
        Map<String, Object> extra = EXTRA_HOLDER.get();
        if (extra == null) {
            extra = new HashMap<>();
            EXTRA_HOLDER.set(extra);
        }
        extra.put(key, value);
    }

    /**
     * 获取扩展属性
     */
    public static Object getExtra(String key) {
        Map<String, Object> extra = EXTRA_HOLDER.get();
        return extra != null ? extra.get(key) : null;
    }

    /**
     * 清空所有上下文
     * 请求结束时必须调用此方法
     */
    public static void clear() {
        USER_ID_HOLDER.remove();
        ROLE_HOLDER.remove();
        REQUEST_ID_HOLDER.remove();
        CONVERSATION_ID_HOLDER.remove();
        EXTRA_HOLDER.remove();
    }

    /**
     * 获取当前上下文快照（用于日志）
     */
    public static String getSnapshot() {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        if (getRequestId() != null) {
            sb.append("reqId=").append(getRequestId()).append(", ");
        }
        if (getUserId() != null) {
            sb.append("userId=").append(getUserId()).append(", ");
        }
        if (getConversationId() != null) {
            sb.append("convId=").append(getConversationId());
        }
        // 移除末尾的逗号和空格
        if (sb.length() > 1) {
            int length = sb.length();
            if (sb.charAt(length - 2) == ',') {
                sb.delete(length - 2, length);
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
