package com.lhs.lawmind.config;

import com.lhs.lawmind.agent.AgentRunner;
import com.lhs.lawmind.agent.compress.*;
import com.lhs.lawmind.agent.memory.MemoryManager;
import com.lhs.lawmind.agent.monitor.AgentMetricsCollector;
import com.lhs.lawmind.agent.tool.LawSearchTools;
import com.lhs.lawmind.agent.tool.LawIntentTools;
import com.lhs.lawmind.agent.tool.LawVerificationTools;
import com.lhs.lawmind.agent.tool.RetrieveMemoryTool;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Configuration
@EnableConfigurationProperties({CompressionConfig.class,
        com.lhs.lawmind.agent.gate.IntentGateConfig.class})
public class AgentConfig {

    private static final String SYSTEM_PROMPT = """
            你是一位专业的中国法律智能助手，名为 LawMind。

            ## 思考模式（ReAct）
            对于每个用户问题，按照以下框架逐步思考：
            1. **理解**：用户的核心法律诉求是什么？涉及哪些法律领域？
            2. **规划**：回答需要哪些信息？列出要调用的工具和顺序。
            3. **执行**：按规划调用工具，每次调用后评估结果是否充分。
            4. **调整**：结果不理想时换关键词或策略重新检索，不要重复相同调用。
            5. **回答**：综合所有信息，按标准结构给出完整的法律建议。

            ## 可用工具
            1. searchLawKnowledge(query, lawType) —— 混合检索法律知识库，返回相关法条和解释
            2. getArticleText(lawName, articleNumber) —— 查询具体法条原文内容
            3. classifyLegalIntent(question) —— 分析用户问题的法律意图类型
            4. expandLegalQuery(originalQuery) —— 对查询进行法律术语扩展
            5. searchSimilarQuestions(question) —— 查找历史上已解答的相似问题
            6. verifyCitation(citation, sourceText) —— 核实法条引用的准确性
            7. retrieveMemory(memoryId) —— 获取用户历史记忆详情，用于了解背景、偏好或之前讨论

            ## 工具使用策略（按问题类型选择）

            **法条查询类**（如"XX法第几条是什么"）：
            → 直接调用 getArticleText 或 searchLawKnowledge → 给出答案
            → 1-2次工具调用即可完成

            **法律咨询类**（劳动纠纷、婚姻家庭、合同争议等）：
            → classifyLegalIntent 分析意图
            → expandLegalQuery 扩展查询关键词
            → searchLawKnowledge 检索相关法条（不理想时换关键词重试1次）
            → searchSimilarQuestions 查看是否有类似解答可参考
            → verifyCitation 核实关键法条引用
            → 综合所有信息给出答案

            **金额计算类**（赔偿金、补偿金、抚养费等）：
            → 先检索法律依据明确计算标准
            → 获取计算公式和参数
            → 代入用户提供的具体数值，分步演算
            → 核实计算结果

            ## 行为约束（必须遵守）
            - 回答法律问题前必须先调用工具获取信息，禁止凭记忆或猜测回答
            - 禁止对同一工具使用相同参数调用超过2次
            - 工具返回"未找到"时，换关键词重试；仍失败则明确告知用户当前知识库暂无相关信息
            - 最多调用5次工具，超过后基于已有信息给出最佳回答
            - 绝对禁止编造法条内容或条文号
            - 不确定的内容必须标注"仅供参考，建议核实"

            ## 输出结构（所有回答统一使用）
            1. **问题分析** —— 简要概述用户核心诉求（1-2句）
            2. **法律依据** —— 列出相关法条，格式为《法律名称》第X条第Y款
            3. **具体解答** —— 结合法条给出建议；涉及金额时逐步列出公式和计算过程
            4. **注意事项** —— 提醒时效、证据保全、程序等实务要点
            5. **免责声明** —— 以上内容仅供参考，不构成法律意见。具体案件请咨询专业律师。

            ## 特别提醒
            - 涉及刑事问题，必须额外提醒"建议立即寻求专业律师帮助"
            - 劳动争议提醒仲裁时效（1年）和先行调解程序
            - 金额计算示例格式：赔偿金 = 月工资 × 工作年限 × 2 = 8000 × 3 × 2 = 48000元
            """;

    private static final int MAX_ITERATIONS = 5;

    @Bean
    @ConditionalOnProperty(prefix = "lawmind.agent.compression", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public ContextCompressor contextCompressor(ChatLanguageModel chatLanguageModel,
                                                CompressionConfig compressionConfig) {
        log.info("[Agent] 初始化上下文压缩器: enabled={}, keepFullRecent={}, maxArticles={}",
                compressionConfig.enabled(),
                compressionConfig.recency().keepFullRecent(),
                compressionConfig.knowledgeState().maxArticles());

        TokenEstimator tokenEstimator = new TokenEstimator();
        RuleExtractor ruleExtractor = new RuleExtractor();
        SummarizingCompressor summarizingCompressor = new SummarizingCompressor(
                chatLanguageModel, tokenEstimator, compressionConfig.minSavingsRatio());
        KnowledgeState knowledgeState = new KnowledgeState(
                compressionConfig.knowledgeState().maxArticles());

        return new ContextCompressor(
                ruleExtractor, summarizingCompressor, tokenEstimator,
                knowledgeState, compressionConfig);
    }

    @Bean
    public AgentRunner agentRunner(ChatLanguageModel chatLanguageModel,
                                   LawSearchTools lawSearchTools,
                                   LawIntentTools lawIntentTools,
                                   LawVerificationTools lawVerificationTools,
                                   RetrieveMemoryTool retrieveMemoryTool,
                                   AgentMetricsCollector metricsCollector,
                                   ContextCompressor contextCompressor,
                                   MemoryManager memoryManager) {
        List<Object> toolObjects = new ArrayList<>();
        toolObjects.add(lawSearchTools);
        toolObjects.add(lawIntentTools);
        toolObjects.add(lawVerificationTools);
        if (retrieveMemoryTool != null) {
            toolObjects.add(retrieveMemoryTool);
        }

        log.info("[Agent] 初始化 AgentRunner: chatModel={}, maxIterations={}, toolCount={}, "
                + "compressor={}, memory={}",
                chatLanguageModel != null ? "available" : "unavailable",
                MAX_ITERATIONS,
                toolObjects.size(),
                contextCompressor != null ? "enabled" : "disabled",
                memoryManager != null ? "enabled" : "disabled");

        return new AgentRunner(chatLanguageModel, SYSTEM_PROMPT, toolObjects,
                MAX_ITERATIONS, metricsCollector, contextCompressor, memoryManager);
    }
}
