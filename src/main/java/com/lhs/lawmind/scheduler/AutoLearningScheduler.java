package com.lhs.lawmind.scheduler;

import com.lhs.lawmind.entity.AiChat;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.mapper.AiChatMapper;
import com.lhs.lawmind.mapper.LawKnowledgeMapper;
import com.lhs.lawmind.service.AsyncVectorizeService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 自动学习入库定时任务
 * 收集高频未命中问题，自动生成法律知识点并入库
 */
@Slf4j
@Component
public class AutoLearningScheduler {

    private final AiChatMapper aiChatMapper;
    private final LawKnowledgeMapper lawKnowledgeMapper;
    private final AsyncVectorizeService asyncVectorizeService;
    private final ChatLanguageModel chatLanguageModel;
    private final boolean autoLearningEnabled;
    private final int batchSize;
    private final String cronExpression;

    public AutoLearningScheduler(AiChatMapper aiChatMapper,
                                  LawKnowledgeMapper lawKnowledgeMapper,
                                  AsyncVectorizeService asyncVectorizeService,
                                  Optional<ChatLanguageModel> chatLanguageModel,
                                  @Value("${rag.auto-learning.enabled:false}") boolean autoLearningEnabled,
                                  @Value("${rag.auto-learning.batch-size:10}") int batchSize,
                                  @Value("${rag.auto-learning.cron:0 0 2 * * ?}") String cronExpression) {
        this.aiChatMapper = aiChatMapper;
        this.lawKnowledgeMapper = lawKnowledgeMapper;
        this.asyncVectorizeService = asyncVectorizeService;
        this.chatLanguageModel = chatLanguageModel.orElse(null);
        this.autoLearningEnabled = autoLearningEnabled;
        this.batchSize = batchSize;
        this.cronExpression = cronExpression;
    }

    /**
     * 定时执行自动学习入库
     * 默认每天凌晨2点执行
     */
    @Scheduled(cron = "${rag.auto-learning.cron:0 0 2 * * ?}")
    public void autoLearning() {
        if (!autoLearningEnabled) {
            log.info("自动学习入库功能未启用");
            return;
        }

        log.info("=== 开始自动学习入库 ===");

        try {
            // 1. 收集高频未命中问题
            log.info("[1/4] 收集高频未命中问题");
            List<AiChat> unmatchedQuestions = aiChatMapper.selectHighFrequencyUnmatched(batchSize);
            
            if (unmatchedQuestions == null || unmatchedQuestions.isEmpty()) {
                log.info("没有未命中问题，自动学习结束");
                return;
            }
            
            log.info("收集到 {} 个未命中问题", unmatchedQuestions.size());

            // 2. 大模型生成法律知识点
            log.info("[2/4] 大模型生成法律知识点");
            for (AiChat chat : unmatchedQuestions) {
                try {
                    LawKnowledge knowledge = generateLawKnowledge(chat.getUserQuestion());
                    if (knowledge != null) {
                        // 3. 写入MySQL law_knowledge表
                        log.info("[3/4] 写入法律知识点: {}", knowledge.getTitle());
                        saveLawKnowledge(knowledge);
                        
                        // 4. 触发向量化任务
                        log.info("[4/4] 触发向量化任务: {}", knowledge.getTitle());
                        asyncVectorizeService.vectorizeAsync(knowledge.getId());
                    }
                } catch (Exception e) {
                    log.error("处理问题失败: {}, 错误: {}", chat.getUserQuestion(), e.getMessage());
                }
            }

            log.info("=== 自动学习入库完成 ===");

        } catch (Exception e) {
            log.error("自动学习入库异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 大模型生成法律知识点
     */
    private LawKnowledge generateLawKnowledge(String question) {
        if (chatLanguageModel == null) {
            log.warn("ChatLanguageModel 未初始化，无法生成法律知识点");
            return null;
        }
    
        try {
            String prompt = buildGenerateKnowledgePrompt(question);
                
            // ========== 打印调用大模型的 Prompt ==========
            log.info("========================================");
            log.info("【调用大模型 - 生成法律知识点］");
            log.info("========================================");
            log.info("【原始问题］");
            log.info("{}", question);
            log.info("----------------------------------------");
            log.info("【完整 Prompt］");
            log.info("{}", prompt);
            log.info("========================================");
                
            String response = chatLanguageModel.generate(prompt);
                
            log.info("大模型生成法律知识点成功");
            log.info("【大模型回答］");
            log.info("{}", response);
            log.info("========================================");
                
            LawKnowledge knowledge = parseKnowledgeResponse(response, question);
            return knowledge;
    
        } catch (Exception e) {
            log.error("生成法律知识点失败：{}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 构建生成法律知识点的Prompt
     */
    private String buildGenerateKnowledgePrompt(String question) {
        return "请根据以下用户问题，生成一个标准的法律知识点。\n\n" +
                "用户问题：" + question + "\n\n" +
                "请严格按照以下JSON格式返回（仅返回JSON，不要有其他文字）：\n" +
                "{\n" +
                "  \"law_type\": \"法律类型，如：消费者权益保护法、劳动合同法、刑法等\",\n" +
                "  \"title\": \"知识点标题，格式：[法律类型]-[具体知识点名]\",\n" +
                "  \"content\": \"详细的法律条文内容，包含完整的法律条文和解释\"\n" +
                "}\n\n" +
                "注意事项：\n" +
                "1. law_type不能为空，必须是真实的法律名称\n" +
                "2. title格式必须包含法律类型和具体知识点名\n" +
                "3. content必须详细、准确、完整\n" +
                "4. 所有字段都不能为空";
    }

    /**
     * 解析大模型返回的法律知识点
     */
    private LawKnowledge parseKnowledgeResponse(String response, String originalQuestion) {
        LawKnowledge knowledge = new LawKnowledge();
        knowledge.setVectorStatus(0);
        knowledge.setCreateTime(new Date());

        try {
            // 尝试从JSON中提取信息
            String lawType = extractField(response, "law_type");
            String title = extractField(response, "title");
            String content = extractField(response, "content");

            // 检查必要字段是否为空，使用默认值
            if (lawType == null || lawType.trim().isEmpty()) {
                lawType = "通用法律";
            }
            if (title == null || title.trim().isEmpty()) {
                title = lawType + "-相关知识点";
            }
            if (content == null || content.trim().isEmpty()) {
                content = "关于 \"" + originalQuestion + "\" 的相关法律知识。";
            }

            knowledge.setLawType(lawType);
            knowledge.setTitle(title);
            knowledge.setContent(content);

            log.info("解析法律知识点成功: law_type={}, title={}", lawType, title);
            return knowledge;

        } catch (Exception e) {
            log.warn("解析法律知识点失败，使用默认值: {}", e.getMessage());
            // 如果解析失败，使用默认值
            knowledge.setLawType("通用法律");
            knowledge.setTitle("通用法律-相关知识点");
            knowledge.setContent("关于 \"" + originalQuestion + "\" 的相关法律知识。");
            return knowledge;
        }
    }

    /**
     * 从JSON字符串中提取字段
     */
    private String extractField(String json, String fieldName) {
        Pattern pattern = Pattern.compile("\"" + fieldName + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * 保存法律知识点到MySQL
     */
    @Transactional(rollbackFor = Exception.class)
    protected void saveLawKnowledge(LawKnowledge knowledge) {
        try {
            lawKnowledgeMapper.insert(knowledge);
            log.info("法律知识点保存成功: id={}, title={}", knowledge.getId(), knowledge.getTitle());
        } catch (Exception e) {
            log.error("法律知识点保存失败: {}", e.getMessage(), e);
            throw e;
        }
    }
}
