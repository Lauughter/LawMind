package com.lhs.lawmind.config;

import com.alibaba.csp.sentinel.slots.block.RuleConstant;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRule;
import com.alibaba.csp.sentinel.slots.block.flow.FlowRuleManager;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Sentinel 限流配置类
 * 
 * 功能说明:
 * 1. 在 Spring Boot 启动时自动初始化限流规则
 * 2. 为 AI 问答接口设置 QPS 限制，保护后端服务
 * 3. 使用预热模式（Warm Up）保护 AI 服务冷启动
 * 4. 支持动态调整限流阈值 (未来可通过 Nacos 配置中心实现)
 * 
 * 限流算法：滑动窗口（Sentinel 默认）
 * 限流模式：预热模式（Warm Up）
 * 
 * @author LawMind
 */
@Slf4j
@Configuration
public class SentinelConfig {

    /**
     * Spring Boot 启动后自动加载限流规则
     */
    @PostConstruct
    public void initSentinelRules() {
        List<FlowRule> rules = new ArrayList<>();
        
        // === 配置 1: AI 问答接口限流（使用预热模式） ===
        FlowRule askRule = new FlowRule();
        askRule.setResource("askQuestion");              // 资源名，与@SentinelResource 的 value 一致
        askRule.setGrade(RuleConstant.FLOW_GRADE_QPS);   // QPS 模式 (1=QPS, 0=线程数)
        askRule.setCount(5);                             // QPS 阈值为 5 (根据服务器性能调整)
        
        // 使用预热模式（保护 AI 服务冷启动）
        // 说明：QPS 从 1 开始，在 10 秒内逐渐增加到 5
        askRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_WARM_UP);
        askRule.setWarmUpPeriodSec(10);                  // 预热时间：10 秒
        
        askRule.setLimitApp("default");                  // 针对所有调用者
        
        // === 配置 2: 测试接口限流（直接拒绝模式） ===
        FlowRule testRule = new FlowRule();
        testRule.setResource("testHello");
        testRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        testRule.setCount(2);  // 更严格的限制，便于测试
        testRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_DEFAULT);  // 直接拒绝
        testRule.setLimitApp("default");
        
        // === 配置 3: 注解测试接口限流（匀速排队模式） ===
        FlowRule annotationTestRule = new FlowRule();
        annotationTestRule.setResource("annotationTest");
        annotationTestRule.setGrade(RuleConstant.FLOW_GRADE_QPS);
        annotationTestRule.setCount(3);  // QPS=3
        // 使用匀速排队模式（漏桶算法）
        annotationTestRule.setControlBehavior(RuleConstant.CONTROL_BEHAVIOR_RATE_LIMITER);
        annotationTestRule.setMaxQueueingTimeMs(500);  // 最大排队时间：500ms
        annotationTestRule.setLimitApp("default");
        
        rules.add(askRule);
        rules.add(testRule);
        rules.add(annotationTestRule);
        
        // 加载规则到 Sentinel
        FlowRuleManager.loadRules(rules);
        
        log.info("===========================================");
        log.info("Sentinel 限流规则加载完成!");
        log.info("  - 资源：askQuestion");
        log.info("    QPS=5 | 模式=预热(Warm Up) | 预热时间=10s");
        log.info("  - 资源：testHello");
        log.info("    QPS=2 | 模式=直接拒绝");
        log.info("  - 资源：annotationTest");
        log.info("    QPS=3 | 模式=匀速排队 | 最大排队时间=500ms");
        log.info("  - 限流算法：滑动窗口");
        log.info("===========================================");
    }
}
