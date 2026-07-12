package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.entity.SysConfig;
import com.lhs.lawmind.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/sys-config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    @Value("${lawmind.admin-user-id:1}")
    private Long adminUserId;

    private boolean isAdmin() {
        Long userId = RequestContext.getUserId();
        return userId != null && userId.equals(adminUserId);
    }

    @GetMapping("/list")
    public Result<PageResult<SysConfig>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        return Result.success(sysConfigService.selectAllPage(page, pageSize));
    }

    @GetMapping("/get-by-key/{configKey}")
    public Result<SysConfig> getByKey(@PathVariable String configKey) {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        return Result.success(sysConfigService.selectByKey(configKey));
    }

    @GetMapping("/get/{id}")
    public Result<SysConfig> get(@PathVariable Integer id) {
        if (!isAdmin()) {
            return Result.error(403, "无权访问，仅限管理员");
        }
        return Result.success(sysConfigService.selectById(id));
    }

    @PostMapping("/add")
    public Result<?> add(@RequestBody SysConfig sysConfig) {
        if (!isAdmin()) {
            return Result.error(403, "无权操作，仅限管理员");
        }
        sysConfigService.insert(sysConfig);
        log.warn("系统配置被修改: key={}", sysConfig.getConfigKey());
        return Result.success();
    }

    @PostMapping("/update")
    public Result<?> update(@RequestBody SysConfig sysConfig) {
        if (!isAdmin()) {
            return Result.error(403, "无权操作，仅限管理员");
        }
        sysConfigService.update(sysConfig);
        log.warn("系统配置被修改: key={}", sysConfig.getConfigKey());
        return Result.success();
    }

    @DeleteMapping("/delete/{id}")
    public Result<?> delete(@PathVariable Integer id) {
        if (!isAdmin()) {
            return Result.error(403, "无权操作，仅限管理员");
        }
        sysConfigService.delete(id);
        log.warn("系统配置被删除: id={}", id);
        return Result.success();
    }
}
