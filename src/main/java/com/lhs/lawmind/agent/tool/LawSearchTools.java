package com.lhs.lawmind.agent.tool;

import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import com.lhs.lawmind.service.HybridSearchService;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.utils.EmbeddingUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * LawSearchTools 是一个专门针对法律知识检索的工具类，提供了两个核心功能：
 * 1. searchLawKnowledge：根据用户的自然语言查询和可选的法律类型过滤，在法律知识库中进行混合检索，返回与查询相关的法律条文、司法解释和案例摘要。
 * 2. getArticleText：根据法律名称和条款号查询具体法条的原文完整内容，适用于需要精确定位某一条法律条文的场景。
 * 这些工具通过调用 HybridSearchService 和 LawKnowledgeService 来实现具体的检索逻辑，并且在执行过程中添加了错误处理和日志记录，确保在实际应用中能够稳定运行并提供有用的反馈信息。
 */
@Slf4j
@Component
public class LawSearchTools {

    private final HybridSearchService hybridSearchService;
    private final LawKnowledgeService lawKnowledgeService;
    private final EmbeddingUtil embeddingUtil;

    public LawSearchTools(HybridSearchService hybridSearchService,
                          LawKnowledgeService lawKnowledgeService,
                          EmbeddingUtil embeddingUtil) {
        this.hybridSearchService = hybridSearchService;
        this.lawKnowledgeService = lawKnowledgeService;
        this.embeddingUtil = embeddingUtil;
    }

    /**
     * 搜索法律知识库，返回与用户问题相关的法律条文、司法解释和案例摘要。
     * 适用于需要查找法律依据的场景。返回最多10条结果，每条包含标题、法条内容和来源。
     * @param query
     * @param lawType
     * @return
     */
    @Tool("搜索法律知识库，返回与用户问题相关的法律条文、司法解释和案例摘要。" +
          "适用于需要查找法律依据的场景。返回最多10条结果，每条包含标题、法条内容和来源。")
    public String searchLawKnowledge(
            @P("用户的搜索查询，使用自然语言描述，例如'劳动合同法关于经济补偿的规定'")
            String query,
            @P("法律类型过滤，如：刑法、民法典、劳动法、劳动合同法、公司法等。" +
               "如果用户问题没有明确指代特定法律类型，传空字符串''")
            String lawType) {
        try {
            float[] queryVector = embeddingUtil.embed(query);

            List<LawKnowledge> results;
            if (lawType != null && !lawType.isEmpty()) {
                results = hybridSearchService.searchHybridFiltered(
                        queryVector, query, 10, lawType);
            } else {
                results = hybridSearchService.searchHybrid(queryVector, query, 10);
            }

            if (results.isEmpty()) {
                return "[检索结果] 未找到相关法律知识。建议：尝试更换关键词或扩大搜索范围。";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < results.size(); i++) {
                LawKnowledge k = results.get(i);
                // Level 0: 结构化紧凑格式 —— 法条编号 | 核心规则 | 适用条件
                String content = k.getContent();
                // 截取关键信息行（前 200 字），避免全文膨胀
                String brief = content != null && content.length() > 200
                        ? content.substring(0, 200) + "..."
                        : content;
                sb.append(String.format("[%d] 《%s》| %s | %s",
                        i + 1,
                        k.getLawType() != null ? k.getLawType() : "法律",
                        k.getTitle(),
                        brief != null ? brief.replace('\n', ' ') : ""));
                if (i < results.size() - 1) {
                    sb.append("\n");
                }
            }
            return sb.toString();

        } catch (Exception e) {
            log.error("[Agent Tool] searchLawKnowledge 执行失败: query={}, lawType={}",
                    query, lawType, e);
            return "[Tool 错误] searchLawKnowledge 执行失败：" + e.getMessage()
                    + "。请尝试换一种方式检索，或直接告知用户当前无法检索。";
        }
    }

    /**
     * 根据法律名称和条款号查询具体法条的原文完整内容。
     * @param lawName
     * @param articleNumber
     * @return
     */
    @Tool("根据法律名称和条款号查询具体法条的原文完整内容。" +
          "适用于需要精确定位某一条法律条文的场景。")
    public String getArticleText(
            @P("完整的法律名称，如'劳动合同法'、'民法典'（不含书名号）")
            String lawName,
            @P("条款号，如'第三十九条'、'第一千零六十二条'。可以是条、款、项。")
            String articleNumber) {
        try {
            String keyword = lawName.replaceAll("[《》]", "").trim();
            List<LawKnowledge> knowledges = lawKnowledgeService.search(keyword, 1, 50);

            if (knowledges.isEmpty()) {
                return "[查询结果] 未找到《" + lawName + "》的相关内容。";
            }

            String result = knowledges.stream()
                    .filter(k -> k.getTitle() != null && k.getTitle().contains(articleNumber))
                    .map(k -> {
                        String content = k.getContent();
                        // Level 0: 法条原文前加关键信息摘要行
                        String keyInfo = content != null && content.length() > 80
                                ? content.substring(0, 80).replace('\n', ' ') + "..."
                                : (content != null ? content : "");
                        return String.format("%s | 关键信息: %s\n原文: %s",
                                k.getTitle(), keyInfo, content);
                    })
                    .collect(Collectors.joining("\n\n"));

            if (result.isEmpty()) {
                return "[查询结果] 在《" + lawName + "》中未找到" + articleNumber
                        + "的内容，请确认条款号是否正确。";
            }

            return result;

        } catch (Exception e) {
            log.error("[Agent Tool] getArticleText 执行失败: lawName={}, articleNumber={}",
                    lawName, articleNumber, e);
            return "[Tool 错误] getArticleText 执行失败：" + e.getMessage();
        }
    }
}
