package com.lhs.lawmind.controller;

import com.lhs.lawmind.aop.annotation.Log;
import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.entity.User;
import com.lhs.lawmind.service.UserService;
import com.lhs.lawmind.service.AiChatService;
import com.lhs.lawmind.service.LawFileUploadService;
import com.lhs.lawmind.utils.JwtUtil;
import com.lhs.lawmind.utils.TokenRedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户控制器
 *
 * 安全加固说明：
 * - Token 存储在 Redis 中，支持主动撤销
 * - 支持登出功能
 * - 修改密码时清除所有 token
 *
 * */
@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    private final JwtUtil jwtUtil;

    private final TokenRedisUtil tokenRedisUtil;

    private final AiChatService aiChatService;

    private final LawFileUploadService lawFileUploadService;

    @GetMapping("/list")
    public Result<PageResult<User>> list(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "10") int pageSize) {
        return Result.success(userService.selectPage(page, pageSize));
    }

    @GetMapping("/get/{id}")
    public Result<User> get(@PathVariable Long id) {
        return Result.success(userService.selectById(id));
    }

    @PostMapping("/add")
    public Result<?> add(@RequestBody User user) {
        userService.insert(user);
        return Result.success();
    }

    @PostMapping("/update")
    public Result<?> update(@RequestBody User user) {
        userService.update(user);
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Long id) {
        userService.delete(id);
        return Result.success();
    }

    @PostMapping("/login")
    @Log(value = "用户登录", logResult = false)
    public Result<?> login(@RequestBody User user) {
        User u = userService.selectByUsername(user.getUsername());
        if (u == null || !userService.verifyPassword(user.getPassword(), u.getPassword())) {
            return Result.error("用户名或密码错误");
        }
        Long userId = u.getId();
        String role = u.getRole() != null ? u.getRole() : "user";
        String accessToken = jwtUtil.generateToken(userId.toString(), role);
        String refreshToken = jwtUtil.generateRefreshToken(userId.toString(), role);

        // 将 token 存储到 Redis
        tokenRedisUtil.storeTokens(userId, accessToken, refreshToken);

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("accessToken", accessToken);
        tokenData.put("refreshToken", refreshToken);
        tokenData.put("expiresIn", jwtUtil.getExpire());

        log.info("用户登录成功: userId={}", userId);
        // 更新最后登录时间
        u.setLastLoginTime(new java.util.Date());
        userService.update(u);

        return Result.success(tokenData);
    }

    /**
     * 刷新 accessToken
     * 使用有效的 refreshToken 换取新的 accessToken
     */
    @PostMapping("/refresh-token")
    public Result<?> refreshToken(@RequestBody Map<String, String> params) {
        String refreshToken = params.get("refreshToken");
        if (refreshToken == null || refreshToken.isEmpty()) {
            return Result.error(400, "refreshToken 不能为空");
        }

        // 验证 refreshToken 是否在 Redis 中存在
        if (!tokenRedisUtil.isTokenExists(refreshToken)) {
            log.warn("refreshToken 不存在或已失效");
            return Result.error(401, "refreshToken 已过期，请重新登录");
        }

        // 验证 refreshToken 是否有效
        if (!jwtUtil.validateToken(refreshToken)) {
            log.warn("refreshToken 已过期或无效");
            // 从 Redis 中删除无效的 refreshToken
            tokenRedisUtil.deleteToken(refreshToken);
            return Result.error(401, "refreshToken 已过期，请重新登录");
        }

        // 验证是否为 refreshToken 类型
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            log.warn("非法的 token 类型，不是 refreshToken");
            return Result.error(401, "非法的 token 类型");
        }

        // 从 refreshToken 中获取用户ID和角色
        String userIdStr = jwtUtil.getUserIdFromToken(refreshToken);
        Long userId = Long.parseLong(userIdStr);
        String role = jwtUtil.getRoleFromToken(refreshToken);

        // 先删除旧的 refreshToken
        tokenRedisUtil.deleteToken(refreshToken);

        // 签发新的双 Token（保留角色）
        String newAccessToken = jwtUtil.generateToken(userIdStr, role);
        String newRefreshToken = jwtUtil.generateRefreshToken(userIdStr, role);

        // 存储新的 token 到 Redis
        tokenRedisUtil.storeTokens(userId, newAccessToken, newRefreshToken);

        Map<String, Object> tokenData = new HashMap<>();
        tokenData.put("accessToken", newAccessToken);
        tokenData.put("refreshToken", newRefreshToken);
        tokenData.put("expiresIn", jwtUtil.getExpire());

        log.info("Token 刷新成功: userId={}", userId);
        return Result.success(tokenData);
    }

    @PostMapping("/register")
    public Result<?> register(@RequestBody User user) {
        if (userService.selectByUsername(user.getUsername()) != null) {
            return Result.error("用户名已存在");
        }
        userService.insert(user);
        return Result.success();
    }

    /**
     * 用户登出
     * 安全加固：清除该用户的所有 token（包括 accessToken 和 refreshToken）
     * 防止旧 token 被恶意利用
     */
    @PostMapping("/logout")
    @Log("用户登出")
    public Result<?> logout(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);

        // 从 token 中获取用户ID
        Long userId = tokenRedisUtil.getUserIdFromToken(token);

        if (userId != null) {
            // 安全加固：清除该用户的所有 token（accessToken + refreshToken）
            // 防止多端登录、token 刷新后遗留的旧 token 被恶意利用
            tokenRedisUtil.clearUserTokens(userId);
            log.info("用户登出成功，已清除所有 token: userId={}", userId);
        } else {
            // token 可能已过期，只删除当前 token
            tokenRedisUtil.deleteToken(token);
            log.warn("用户登出，token 已过期，仅删除当前 token");
        }

        return Result.success();
    }

    @GetMapping("/info")
    public Result<User> getUserInfo() {
        // 从 RequestContext 获取用户ID（安全加固）
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }

        User user = userService.selectById(userId);
        // 清除密码信息
        if (user != null) {
            user.setPassword(null);
        }
        return Result.success(user);
    }

    @PostMapping("/update-info")
    public Result<?> updateUserInfo(@RequestBody User user) {
        // 从 RequestContext 获取用户ID（安全加固）
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }

        // 只允许更新 nickname 和 phone，不允许直接更新 username 和 password
        User existingUser = userService.selectById(userId);
        if (existingUser == null) {
            return Result.error("用户不存在");
        }

        // 保留原有用户名和密码
        existingUser.setNickname(user.getNickname());
        existingUser.setPhone(user.getPhone());

        userService.update(existingUser);
        log.info("用户信息更新成功：userId={}", userId);
        return Result.success();
    }

    /**
     * 管理员重置用户密码（无需旧密码）
     * 用于修复因历史版本密码双重加密导致无法登录的账号
     */
    @PostMapping("/reset-password/{id}")
    @Log("重置密码")
    public Result<?> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> params) {
        String newPassword = params.get("password");
        if (newPassword == null || newPassword.isEmpty()) {
            return Result.error("密码不能为空");
        }
        User user = userService.selectById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPassword(newPassword);
        userService.update(user);
        log.info("管理员重置密码: userId={}", id);
        return Result.success();
    }

    /**
     * 修改密码
     * 修改成功后清除用户所有 token，需要重新登录
     */
    @PostMapping("/change-password")
    @Log("修改密码")
    public Result<?> changePassword(@RequestBody Map<String, String> params) {
        // 从 RequestContext 获取用户ID（安全加固）
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }

        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");

        if (oldPassword == null || newPassword == null) {
            return Result.error("参数错误");
        }

        User user = userService.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 验证原密码
        if (!userService.verifyPassword(oldPassword, user.getPassword())) {
            log.warn("原密码错误：userId={}", userId);
            return Result.error("原密码错误");
        }

        // 更新新密码
        user.setPassword(newPassword);
        userService.update(user);

        // 清除用户所有 token（安全加固）
        tokenRedisUtil.clearUserTokens(userId);

        log.info("用户密码修改成功，已清除所有 token：userId={}", userId);
        return Result.success();
    }

    @GetMapping("/chat-history")
    public Result<?> getChatHistory(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        return Result.success(aiChatService.selectByUserIdPage(userId, page, pageSize));
    }

    @GetMapping("/upload-history")
    public Result<?> getUploadHistory(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }
        return Result.success(lawFileUploadService.selectByUserIdPage(userId, page, pageSize));
    }

    /**
     * 获取用户统计数据（咨询次数、上传文件数）
     */
    @GetMapping("/stats")
    public Result<?> getUserStats() {
        // 从 RequestContext 获取用户ID（安全加固）
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return Result.error(401, "用户未登录，请先登录");
        }

        Map<String, Object> stats = new HashMap<>();
        // 咨询次数：查询该用户的聊天记录数量
        stats.put("chatCount", aiChatService.selectByUserId(userId).size());
        // 上传文件数：查询该用户上传的文件数量
        stats.put("fileCount", lawFileUploadService.selectByUserId(userId).size());
        return Result.success(stats);
    }
}
