package com.lhs.lawmind.controller;

import com.lhs.lawmind.agent.AgentRunner;
import com.lhs.lawmind.agent.gate.GateResult;
import com.lhs.lawmind.agent.gate.IntentGate;
import com.lhs.lawmind.agent.monitor.AgentMetricsCollector;
import com.lhs.lawmind.common.Result;
import com.lhs.lawmind.context.RequestContext;
import com.lhs.lawmind.skill.SkillConfig;
import com.lhs.lawmind.skill.SkillManager;
import com.lhs.lawmind.utils.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 合同审查控制器。
 *
 * <p>提供合同文件上传和 AI 审查功能。文件上传后"阅后即焚"——仅内存提取文本，
 * 不存储到磁盘或数据库。审查通过 Agent 通道进行多步推理，
 * Agent 在审查每条条款前会先从知识库检索相关法条原文作为判断依据。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/contract-review")
public class ContractReviewController {

    private static final int MAX_FILE_SIZE_MB = 20;
    private static final int MAX_TEXT_LENGTH = 200_000;

    /**
     * 合同审查专用 System Prompt，运行时注入 Agent 替代默认通用 Prompt。
     * 核心原则：所有法律判断必须基于 searchLawKnowledge 从知识库检索的真实法条，
     * 不得凭 LLM 记忆编造。
     */
    private final FileUtil fileUtil;
    private final AgentRunner agentRunner;
    private final IntentGate intentGate;
    private final AgentMetricsCollector metricsCollector;
    private final SkillManager skillManager;

    public ContractReviewController(FileUtil fileUtil,
                                    AgentRunner agentRunner,
                                    IntentGate intentGate,
                                    AgentMetricsCollector metricsCollector,
                                    SkillManager skillManager) {
        this.fileUtil = fileUtil;
        this.agentRunner = agentRunner;
        this.intentGate = intentGate;
        this.metricsCollector = metricsCollector;
        this.skillManager = skillManager;
    }

    @PostMapping(value = "/upload", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter uploadAndReview(@RequestParam("file") MultipartFile file) {
        Long userId = RequestContext.getUserId();
        if (userId == null) {
            return errorEmitter("用户未登录，请先登录");
        }
        if (file == null || file.isEmpty()) {
            return errorEmitter("请上传合同文件");
        }
        if (file.getSize() > MAX_FILE_SIZE_MB * 1024 * 1024L) {
            return errorEmitter("文件大小超过限制（最大 " + MAX_FILE_SIZE_MB + "MB）");
        }

        SseEmitter emitter = new SseEmitter(300_000L);

        new Thread(() -> {
            try {
                // 1. 提取文本
                sendProgress(emitter, "正在提取文件内容...");
                String extractedText = fileUtil.extractText(file);

                if (extractedText == null || extractedText.isBlank()) {
                    sendError(emitter, "无法提取文件内容，请确认文件格式正确（支持 PDF/Word/TXT）");
                    return;
                }
                if (extractedText.length() > MAX_TEXT_LENGTH) {
                    extractedText = extractedText.substring(0, MAX_TEXT_LENGTH);
                    sendProgress(emitter, "合同文本较长，已截取前 " + MAX_TEXT_LENGTH + " 字符进行分析...");
                }

                int textLength = extractedText.length();
                log.info("[合同审查] 文件提取完成: userId={}, fileName={}, textLength={}",
                        userId, file.getOriginalFilename(), textLength);
                sendProgress(emitter, "文件提取完成（共 " + textLength + " 字符），开始审查...");

                // 2. 加载 Skill 配置（自动热更新，修改 skill 文件后无需重启）
                String originalFilename = file.getOriginalFilename();
                SkillConfig skill = skillManager.getSkill("contract-review");
                String reviewQuestion = skill.fillUserPrompt(
                        detectContractType(originalFilename), extractedText);
                String systemPrompt = skill.assembleSystemPrompt();

                // 3. 通过意图门控 → Agent 通道
                GateResult gateResult = intentGate.process(reviewQuestion);
                metricsCollector.recordAgentCall(userId, "合同审查");
                metricsCollector.recordGateProcess(gateResult);

                if (!gateResult.accepted()) {
                    sendError(emitter, gateResult.rejectResponse());
                    return;
                }

                // 4. Agent 多步推理（System Prompt 由 Skill 动态加载，支持热更新）
                sendProgress(emitter, "Agent 正在从知识库检索法条，逐条对照审查...");
                AgentRunner.AgentResult result = agentRunner.execute(
                        reviewQuestion, systemPrompt, userId, null);

                if (result.success()) {
                    String answer = result.answer();
                    for (int i = 0; i < answer.length(); i++) {
                        emitter.send(SseEmitter.event()
                                .name("message")
                                .data(String.valueOf(answer.charAt(i))));
                        if (i % 3 == 0) {
                            Thread.sleep(5);
                        }
                    }
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data("{\"status\":\"completed\",\"channel\":\"agent\",\"textLength\":" + textLength + "}"));
                    emitter.complete();
                    log.info("[合同审查] 审查完成: userId={}, fileName={}", userId, file.getOriginalFilename());
                } else {
                    sendError(emitter, result.answer());
                }
            } catch (IOException e) {
                log.error("[合同审查] 文件处理失败: userId={}, error={}", userId, e.getMessage(), e);
                sendError(emitter, "文件处理失败：" + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.completeWithError(e);
            } catch (Exception e) {
                log.error("[合同审查] 审查异常: userId={}, error={}", userId, e.getMessage(), e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"message\":\"合同审查失败，请稍后重试\"}"));
                    emitter.complete();
                } catch (IOException ex) {
                    emitter.completeWithError(ex);
                }
            }
        }, "contract-review-" + userId).start();

        return emitter;
    }

    /**
     * 从文件名推测合同类型，用于填充 Skill 用户提示词模板中的 {contract_type} 占位符。
     */
    private String detectContractType(String fileName) {
        if (fileName == null) return "通用合同";
        String lower = fileName.toLowerCase();
        if (lower.contains("劳动") || lower.contains("聘用") || lower.contains("员工") || lower.contains("实习"))
            return "劳动合同";
        if (lower.contains("租赁") || lower.contains("租房") || lower.contains("出租"))
            return "租赁合同";
        if (lower.contains("借款") || lower.contains("贷款") || lower.contains("借贷"))
            return "借款合同";
        if (lower.contains("买卖") || lower.contains("采购") || lower.contains("销售") || lower.contains("供货"))
            return "买卖合同";
        if (lower.contains("服务") || lower.contains("技术") || lower.contains("开发") || lower.contains("外包"))
            return "服务合同";
        if (lower.contains("合伙") || lower.contains("股东") || lower.contains("股权"))
            return "合伙/股权合同";
        if (lower.contains("保密") || lower.contains("竞业") || lower.contains("知识产权"))
            return "保密/竞业限制合同";
        return "通用合同";
    }

    private void sendProgress(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("progress")
                    .data("{\"message\":\"" + escapeJson(message) + "\"}"));
        } catch (IOException e) {
            log.warn("[合同审查] 进度推送失败: {}", e.getMessage());
        }
    }

    private void sendError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data("{\"message\":\"" + escapeJson(message) + "\"}"));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private SseEmitter errorEmitter(String message) {
        SseEmitter e = new SseEmitter();
        try {
            e.send(SseEmitter.event()
                    .name("error")
                    .data("{\"message\":\"" + escapeJson(message) + "\"}"));
            e.complete();
        } catch (IOException ex) {
            e.completeWithError(ex);
        }
        return e;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    @GetMapping("/health")
    public Result<String> health() {
        return Result.success("Contract review service is running");
    }
}
