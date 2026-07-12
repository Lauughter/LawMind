package com.lhs.lawmind.common;

/**
 * 业务异常基类 — 消息会安全返回给前端
 * 用于业务逻辑中的可预期错误（参数非法、资源不存在等）
 */
public class BusinessException extends RuntimeException {

    private final int code;
    private final String publicMessage;

    public BusinessException(int code, String publicMessage) {
        super(publicMessage);
        this.code = code;
        this.publicMessage = publicMessage;
    }

    public BusinessException(String publicMessage) {
        this(400, publicMessage);
    }

    public int getCode() { return code; }
    public String getPublicMessage() { return publicMessage; }

    /** 资源不存在 (404) */
    public static BusinessException notFound(String resource, Object id) {
        return new BusinessException(404, resource + "不存在: " + id);
    }

    /** 未登录 (401) */
    public static BusinessException unauthorized(String message) {
        return new BusinessException(401, message);
    }

    /** 无权限 (403) */
    public static BusinessException forbidden(String message) {
        return new BusinessException(403, message);
    }

    /** 参数非法 (400) */
    public static BusinessException badRequest(String message) {
        return new BusinessException(400, message);
    }

    /** 请求过多 (429) */
    public static BusinessException tooManyRequests(String message) {
        return new BusinessException(429, message);
    }

    /** 服务内部错误 (500)，只返回通用消息，不泄露内部细节 */
    public static BusinessException serviceError(String publicMessage) {
        return new BusinessException(500, publicMessage);
    }

    /** 异步执行失败 (500) */
    public static BusinessException asyncExecutionFailed(String publicMessage) {
        return new BusinessException(500, publicMessage);
    }
}
