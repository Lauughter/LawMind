package com.lhs.lawmind.config;

import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Sentinel 配置验证类
 * 用于确认限流规则是否正确加载
 */
@Slf4j
@Configuration
public class SentinelVerifyConfig {

    @PostConstruct
    public void verifySentinelConfig() {
        // 延迟 3 秒执行，确保 SentinelConfig 已经初始化
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("===========================================");
        log.info("Sentinel 配置验证开始...");
        log.info("===========================================");

        // 检查限流规则
        int ruleCount = FlowRuleManager.getRules().size();
        log.info("✅ 当前限流规则数量：{}", ruleCount);

        if (ruleCount > 0) {
            log.info("✅ 限流规则已加载！");
            log.info("规则详情:");
            FlowRuleManager.getRules().forEach(rule -> {
                log.info("  - 资源：{}, QPS 阈值：{}", 
                    ((com.alibaba.csp.sentinel.slots.block.flow.FlowRule) rule).getResource(),
                    ((com.alibaba.csp.sentinel.slots.block.flow.FlowRule) rule).getCount());
            });
        } else {
            log.error("❌ 未找到任何限流规则！");
            log.error("请检查:");
            log.error("1. SentinelConfig.java 是否正确配置");
            log.error("2. @PostConstruct 方法是否被调用");
            log.error("3. FlowRuleManager.loadRules() 是否被执行");
        }

        log.info("===========================================");
        log.info("Sentinel 配置验证完成");
        log.info("===========================================");
    }
}
