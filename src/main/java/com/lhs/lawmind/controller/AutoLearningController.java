package com.lhs.lawmind.controller;

import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.scheduler.AutoLearningScheduler;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * 自动学习测试控制器
 * 开发阶段用于手动触发自动学习入库
 */
@RestController
@RequestMapping("/auto-learning")
public class AutoLearningController {

    private final AutoLearningScheduler autoLearningScheduler;

    public AutoLearningController(Optional<AutoLearningScheduler> autoLearningScheduler) {
        this.autoLearningScheduler = autoLearningScheduler.orElse(null);
    }

    /**
     * 手动触发自动学习入库
     */
    @PostMapping("/trigger")
    public Result<String> triggerAutoLearning() {
        if (autoLearningScheduler == null) {
            return Result.error("自动学习服务未初始化");
        }
        autoLearningScheduler.autoLearning();
        return Result.success("自动学习入库已触发，请查看日志");
    }
}
