package com.lhs.lawmind.scheduler;

import com.lhs.lawmind.service.AutoLearnService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 自动学习定时任务
 * 定期扫描未匹配知识的聊天记录并处理
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class AutoLearnScheduler {

    private final AutoLearnService autoLearnService;

    /**
     * 定时扫描未匹配知识的聊天记录
     * 每6小时执行一次
     */
    @Scheduled(cron = "0 0 */6 * * ?")
    public void scanUnmatchedChats() {
        try {
            log.info("开始执行自动学习定时任务：扫描未匹配知识的聊天记录");
            autoLearnService.scanAndProcessUnmatchedChats();
            log.info("自动学习定时任务执行完成");
        } catch (Exception e) {
            log.error("自动学习定时任务执行失败: {}", e.getMessage(), e);
        }
    }
}
