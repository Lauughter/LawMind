package com.lhs.lawmind.controller;

import com.lhs.lawmind.agent.memory.AiMemory;
import com.lhs.lawmind.agent.memory.MemoryManager;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 记忆管理接口 —— 用户查看、删除、清空记忆。
 */
@Slf4j
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryManager memoryManager;

    @GetMapping("/list")
    public Result<List<AiMemory>> list() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        List<AiMemory> memories = memoryManager.getMemoryList(userId);
        return Result.success(memories);
    }

    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> delete(@PathVariable Long id) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        boolean deleted = memoryManager.deleteMemory(id, userId);
        if (deleted) {
            return Result.success(Map.of("deleted", true, "id", id));
        }
        return Result.error(404, "记忆不存在或无权删除");
    }

    @DeleteMapping("/clear")
    public Result<Map<String, Object>> clear() {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        int count = memoryManager.clearAllMemories(userId);
        log.info("用户清空所有记忆: userId={}, count={}", userId, count);
        return Result.success(Map.of("deleted", true, "count", count));
    }
}
