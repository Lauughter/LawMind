package com.lhs.lawmind.service;

import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.entity.LawKnowledge;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG知识库检索服务接口
 */
public interface RagService {

    /**
     * 处理用户问题并返回答案（完整RAG流程，支持多轮对话）
     *
     * @param userId         用户ID
     * @param question       用户问题
     * @param conversationId 会话ID（可为null）
     * @return AI回答响应
     */
    AIChatResponse processQuestion(Long userId, String question, Long conversationId);

    /**
     * 搜索法律知识
     *
     * @param questionVector 问题向量
     * @param expandedQuery 扩展后的查询文本（用于混合搜索的全文检索）
     * @return 匹配的法律知识列表
     */
    java.util.List<LawKnowledge> searchLawKnowledge(float[] questionVector, String expandedQuery);

    /**
     * 生成AI回答
     *
     * @param question       用户问题
     * @param relatedKnowledge 相关法律知识
     * @return AI生成的回答
     */
    String generateAnswer(String question, java.util.List<LawKnowledge> relatedKnowledge, String historyContext);

    /**
     * 异步记录访问日志和埋点
     *
     * @param userId          用户ID
     * @param question        用户问题
     * @param answer          AI回答
     * @param knowledgeMatch  匹配的知识JSON
     * @param source          回答来源（law_knowledge/llm_direct 等）
     * @return 生成的聊天记录ID
     */
    Long asyncLogVisit(Long userId, String question, String answer, String knowledgeMatch, String source, Long conversationId);

    /**
     * 异步更新法律知识到聊天记录
     *
     * @param chatId      聊天记录ID
     * @param knowledgeIds 知识点ID列表，逗号分隔
     */
    void asyncUpdateKnowledgeToChatRecord(Long chatId, String knowledgeIds);

    /**
     * 流式处理用户问题（SSE）
     * Steps 1-5 同步执行，Step 6 使用 StreamingChatLanguageModel 逐 token 推送
     *
     * @param userId         用户ID
     * @param question       用户问题
     * @param conversationId 会话ID（可为null）
     * @param emitter        SseEmitter 用于推送流式事件
     */
    void processQuestionStream(Long userId, String question, Long conversationId, SseEmitter emitter);
}
