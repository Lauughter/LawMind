package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.entity.Conversation;
import com.lhs.lawmind.service.ConversationService;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.entity.AiChat;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 会话管理控制器
 * 提供会话的创建、列表、重命名、删除、消息查询等 API
 * 
 * 安全加固说明：
 * - 从RequestContext获取用户ID，防止用户篡改
 * - 所有接口都需要JWT认证才能访问
 * 
 * */
@Slf4j
@RestController
@RequestMapping("/conversation")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final AiChatService aiChatService;

    /**
     * 创建新会话
     * 
     * 安全加固：从UserContext获取用户ID
     */
    @PostMapping("/create")
    public Result<Conversation> create(@RequestBody(required = false) Map<String, String> body) {
        // 从RequestContext获取用户ID（安全加固，防止篡改）
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        
        String title = (body != null && body.containsKey("title")) ? body.get("title") : "新对话";
        Conversation conversation = conversationService.create(userId, title);
        return Result.success(conversation);
    }

    /**
     * 获取用户的会话列表
     * 
     * 安全加固：从UserContext获取用户ID
     */
    @GetMapping("/list")
    public Result<PageResult<Conversation>> list(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        return Result.success(conversationService.listByUserIdPage(userId, page, pageSize));
    }

    /**
     * 分页获取会话的消息列表
     */
    @GetMapping("/{id}/messages")
    public Result<PageResult<AiChat>> getMessages(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize) {
        return Result.success(aiChatService.selectByConversationIdPage(id, page, pageSize));
    }

    /**
     * 重命名会话
     */
    @PutMapping("/{id}/rename")
    public Result<?> rename(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            return Result.error("标题不能为空");
        }
        conversationService.rename(id, title);
        return Result.success();
    }

    /**
     * 删除会话（软删除）
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        conversationService.delete(id);
        return Result.success();
    }
}
