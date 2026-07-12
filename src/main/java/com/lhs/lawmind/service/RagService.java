package com.lhs.lawmind.service;

import com.lhs.lawmind.dto.AIChatResponse;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.entity.SimilarQuestion;
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
     * 查询热点缓存
     *
     * @param md5 问题MD5
     * @return 缓存的答案，未命中返回null
     */
    String queryHotCache(String md5);

    /**
     * 搜索相似问题
     *
     * @param originalQuestion 原始问题
     * @param questionVector 问题向量
     * @return 匹配的相似问题，未命中返回null
     */
    SimilarQuestion searchSimilarQuestion(String originalQuestion, float[] questionVector);

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
     * @param source          回答来源（hot_cache/similar_question/law_knowledge/llm_direct）
     * @return 生成的聊天记录ID
     */
    Long asyncLogVisit(Long userId, String question, String answer, String knowledgeMatch, String source, Long conversationId);

    /**
     * 异步更新相似问题库
     *
     * @param question       用户问题
     * @param answer         AI回答
     * @param knowledgeIds   关联的知识点ID
     * @param questionVector 问题向量
     */
    void asyncUpdateSimilarQuestion(String question, String answer, String knowledgeIds, float[] questionVector);

    /**
     * 检查并升级热点缓存
     *
     * @param md5    问题MD5
     * @param answer 答案
     */
    void checkAndUpgradeHotCache(String md5, String answer);

    /**
     * 异步更新法律知识到聊天记录
     *
     * @param chatId      聊天记录ID
     * @param knowledgeIds 知识点ID列表，逗号分隔
     */
    void asyncUpdateKnowledgeToChatRecord(Long chatId, String knowledgeIds);

    /**
     * 异步插入问题和法律知识到聊天记录
     *
     * @param userId      用户ID
     * @param question    用户问题
     * @param answer      AI回答
     * @param knowledgeIds 知识点ID列表，逗号分隔
     */
    void asyncInsertQuestionAndKnowledge(Long userId, String question, String answer, String knowledgeIds, Long conversationId);

    /**
     * 流式处理用户问题（SSE）
     * Steps 1-5 同步执行，Step 6 使用 StreamingChatLanguageModel 逐 token 推送
     * 缓存/相似问题命中时直接一次性发送完整答案
     *
     * @param userId         用户ID
     * @param question       用户问题
     * @param conversationId 会话ID（可为null）
     * @param emitter        SseEmitter 用于推送流式事件
     */
    void processQuestionStream(Long userId, String question, Long conversationId, SseEmitter emitter);
}
