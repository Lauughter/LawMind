package com.lhs.lawmind.controller;

import com.alibaba.csp.sentinel.annotation.SentinelResource;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.lhs.lawmind.aop.annotation.Log;
import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.dto.AiChatVO;
import com.lhs.lawmind.dto.StreamChatRequest;
import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.service.ConversationService;
import com.lhs.lawmind.service.RagService;
import com.lhs.lawmind.entity.Conversation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import com.lhs.lawmind.common.BusinessException;
import java.util.concurrent.*;

/**
 * AI聊天控制器
 *
 * 安全加固说明：
 * - 从RequestContext获取用户ID，防止用户篡改
 * - 所有接口都需要JWT认证才能访问
 *
 * */
@Slf4j
@RestController
@RequestMapping("/ai-chat")
@RequiredArgsConstructor
public class AiChatController {

    private static final int TIMEOUT_SECONDS = 90;

    @Value("${lawmind.admin-user-id:1}")
    private Long adminUserId;

    private final AiChatService aiChatService;
    private final ConversationService conversationService;
    private final RagService ragService;
    private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor taskExecutor;

    @GetMapping("/list")
    public Result<PageResult<AiChatVO>> list(@RequestParam(defaultValue = "1") int page,
                                             @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = RequestContext.getUserId();
        if (userId == null || !userId.equals(adminUserId)) {
            return Result.error(403, "无权访问");
        }
        PageResult<AiChat> pageResult = aiChatService.selectPage(page, pageSize);
        List<AiChatVO> voList = pageResult.getList().stream().map(AiChatVO::from).toList();
        return Result.success(PageResult.of(pageResult.getTotal(), voList, page, pageSize));
    }

    @GetMapping("/list-by-user")
    public Result<PageResult<AiChatVO>> listByUser(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        PageResult<AiChat> pageResult = aiChatService.selectByUserIdPage(userId, page, pageSize);
        List<AiChatVO> voList = pageResult.getList().stream().map(AiChatVO::from).toList();
        return Result.success(PageResult.of(pageResult.getTotal(), voList, page, pageSize));
    }

    @GetMapping("/get/{id}")
    public Result<AiChatVO> get(@PathVariable Long id) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        AiChat aiChat = aiChatService.getById(id);
        if (aiChat == null) {
            return Result.error(404, "记录不存在");
        }
        if (!aiChat.getUserId().equals(userId)) {
            return Result.error(403, "无权访问该记录");
        }
        return Result.success(AiChatVO.from(aiChat));
    }

    @PostMapping("/add")
    public Result<?> add(@RequestBody AiChat aiChat) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        aiChat.setUserId(userId);
        aiChatService.insert(aiChat);
        return Result.success();
    }

    @PostMapping("/update")
    public Result<?> update(@RequestBody AiChat aiChat) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        AiChat existing = aiChatService.getById(aiChat.getId());
        if (existing == null) {
            return Result.error(404, "记录不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            return Result.error(403, "无权修改该记录");
        }
        aiChat.setUserId(userId);
        aiChatService.update(aiChat);
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        AiChat existing = aiChatService.getById(id);
        if (existing == null) {
            return Result.error(404, "记录不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            return Result.error(403, "无权删除该记录");
        }
        aiChatService.delete(id);
        return Result.success();
    }

    /**
     * AI 问答接口 - 已添加 Sentinel 限流
     *
     * 安全加固：从RequestContext获取用户ID，防止用户篡改
     */
    @PostMapping("/ask")
    @Log("AI问答请求")
    @SentinelResource(value = "askQuestion", blockHandler = "handleBlock", fallback = "handleFallback")
    public Result<AIChatResponse> ask(@RequestBody Map<String, Object> params) {
        // 从RequestContext获取用户ID（安全加固，防止篡改）
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }

        // 参数解析
        String question = params.get("question").toString();

        // 解析或创建 conversationId
        Long conversationId = null;
        if (params.containsKey("conversationId") && params.get("conversationId") != null) {
            conversationId = Long.parseLong(params.get("conversationId").toString());
        }
        if (conversationId == null) {
            String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
            Conversation conversation = conversationService.create(userId, title);
            conversationId = conversation.getId();
            log.info("自动创建新会话：conversationId={}", conversationId);
        }

        final Long finalConversationId = conversationId;

        // 执行 AI 问答
        Future<AIChatResponse> future = taskExecutor.submit(() -> {
            return aiChatService.askQuestion(userId, question, finalConversationId);
        });

        try {
            AIChatResponse result = future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            conversationService.touch(finalConversationId);
            return Result.success(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw BusinessException.serviceError("请求被中断，请稍后重试");
        } catch (ExecutionException e) {
            log.error("AI问答异步执行失败", e.getCause());
            throw BusinessException.asyncExecutionFailed("执行失败，请稍后重试");
        } catch (TimeoutException e) {
            throw BusinessException.serviceError("请求超时，请稍后重试");
        }
    }

    /**
     * SSE 流式问答接口
     *
     * 安全加固：从RequestContext获取用户ID，防止用户篡改
     */
    @PostMapping(value = "/ask-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Log(value = "SSE流式问答", logResult = false)
    public SseEmitter askStream(@RequestBody StreamChatRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        try {
            // 从RequestContext获取用户ID（安全加固，防止篡改）
            Long userId = RequestContext.getUserId();
            if (userId == null) {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"用户未登录，请先登录\"}"));
                emitter.complete();
                return emitter;
            }

            String question = request.getQuestion();

            if (userId == null || question == null || question.trim().isEmpty()) {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"参数不完整\"}"));
                emitter.complete();
                return emitter;
            }

            Long conversationId = request.getConversationId();
            if (conversationId == null) {
                String title = question.length() > 20 ? question.substring(0, 20) + "..." : question;
                Conversation conversation = conversationService.create(userId, title);
                conversationId = conversation.getId();
                log.info("SSE: 自动创建新会话：conversationId={}", conversationId);
            }

            final Long finalConversationId = conversationId;

            emitter.onTimeout(() -> {
                log.warn("SSE 连接超时：userId={}", userId);
                emitter.complete();
            });
            emitter.onError(throwable -> {
                log.warn("SSE 连接异常：{}", throwable.getMessage());
            });

            taskExecutor.execute(() -> {
                ragService.processQuestionStream(userId, question, finalConversationId, emitter);
                conversationService.touch(finalConversationId);
            });

        } catch (Exception e) {
            log.error("SSE 流式问答初始化失败：{}", e.getMessage(), e);
            try {
                emitter.send(SseEmitter.event().name("error").data("{\"message\":\"系统内部错误\"}"));
                emitter.complete();
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        }

        return emitter;
    }

    /**
     * 消息反馈接口
     * 点踩（feedback=-1）时自动入审核队列（feedback_status=PENDING_REVIEW）
     */
    @PostMapping("/feedback")
    public Result<?> feedback(@RequestBody Map<String, Object> params) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录");
        }
        Long chatId = Long.parseLong(params.get("chatId").toString());
        AiChat existing = aiChatService.getById(chatId);
        if (existing == null) {
            return Result.error(404, "聊天记录不存在");
        }
        if (!existing.getUserId().equals(userId)) {
            return Result.error(403, "无权操作该记录");
        }
        Integer feedback = params.get("feedback") != null
                ? Integer.parseInt(params.get("feedback").toString())
                : null;

        if (feedback != null && feedback != 1 && feedback != -1) {
            return Result.error(400, "反馈值非法，只允许 1（赞）或 -1（踩）");
        }

        String feedbackContent = params.get("feedbackContent") != null
                ? params.get("feedbackContent").toString()
                : null;

        int result = aiChatService.updateFeedback(chatId, feedback, feedbackContent);
        if (result > 0) {
            log.info("消息反馈更新成功：chatId={}, feedback={}", chatId, feedback);
            return Result.success();
        } else {
            return Result.error(404, "聊天记录不存在");
        }
    }

    /**
     * 管理员查询待审核反馈队列
     */
    @GetMapping("/admin/review-queue")
    public Result<PageResult<AiChatVO>> reviewQueue(@RequestParam(defaultValue = "1") int page,
                                                      @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = RequestContext.getUserId();
        if (userId == null || !userId.equals(adminUserId)) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        PageResult<AiChat> pageResult = aiChatService.selectPendingReviewPage(page, pageSize);
        List<AiChatVO> voList = pageResult.getList().stream().map(AiChatVO::from).toList();
        return Result.success(PageResult.of(pageResult.getTotal(), voList, page, pageSize));
    }

    /**
     * 管理员提交审核结果
     */
    @PostMapping("/admin/review/{id}")
    public Result<?> submitReview(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        Long userId = RequestContext.getUserId();
        if (userId == null || !userId.equals(adminUserId)) {
            return Result.error(403, "无权操作，仅限管理员");
        }
        String feedbackStatus = params.get("feedbackStatus") != null
                ? params.get("feedbackStatus").toString()
                : null;
        if (feedbackStatus == null || (!feedbackStatus.equals("REVIEWED") && !feedbackStatus.equals("DISMISSED"))) {
            return Result.error(400, "审核状态非法，只允许 REVIEWED 或 DISMISSED");
        }
        String reviewNotes = params.get("reviewNotes") != null
                ? params.get("reviewNotes").toString()
                : null;
        int result = aiChatService.submitReview(id, feedbackStatus, userId, reviewNotes);
        if (result > 0) {
            log.info("审核提交成功：chatId={}, status={}, reviewedBy={}", id, feedbackStatus, userId);
            return Result.success();
        } else {
            return Result.error(404, "聊天记录不存在");
        }
    }

    /**
     * Sentinel 限流处理方法
     */
    public Result<AIChatResponse> handleBlock(Map<String, Object> params, BlockException ex) {
        // 设置 HTTP 429 Too Many Requests 状态码
        jakarta.servlet.http.HttpServletResponse response =
            ((org.springframework.web.context.request.ServletRequestAttributes)
                org.springframework.web.context.request.RequestContextHolder.currentRequestAttributes()).getResponse();
        if (response != null) {
            response.setStatus(429);
        }

        log.error("=== SENTINEL 限流触发 ===");
        log.error("资源名：{}", ex.getRule().getResource());
        log.error("限流类型：{}", ex.getRule().getClass().getSimpleName());
        return Result.error(429, "请求过于频繁，请稍后再试");
    }

    /**
     * Sentinel 降级处理方法
     */
    public Result<AIChatResponse> handleFallback(Map<String, Object> params, Throwable ex) {
        log.error("=== SENTINEL 降级触发 ===");
        log.error("异常信息：{}", ex.getMessage(), ex);
        return Result.error(503, "服务暂时不可用，请稍后再试");
    }
}
