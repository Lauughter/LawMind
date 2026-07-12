package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.entity.SimilarQuestion;
import com.lhs.lawmind.service.SimilarQuestionMaintenanceService;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.SimilarQuestionRedisUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 相似问题库维护服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SimilarQuestionMaintenanceServiceImpl implements SimilarQuestionMaintenanceService {
    
    private final EmbeddingUtil embeddingUtil;
    private final SimilarQuestionRedisUtil similarQuestionRedisUtil;
    
    @Override
    public boolean storeToSimilarQuestionLibrary(String question, String answer, String knowledgeIds) {
        if (!StringUtils.hasText(question) || !StringUtils.hasText(answer)) {
            log.warn("问题或答案为空，无法存储到相似问题库");
            return false;
        }
        
        try {
            // 生成问题向量
            float[] questionVector = embeddingUtil.embed(question);
            if (questionVector == null || questionVector.length == 0) {
                log.error("问题向量化失败: question={}", question);
                return false;
            }
            
            // 构造相似问题实体
            SimilarQuestion similarQuestion = new SimilarQuestion();
            similarQuestion.setId((long) Math.abs(question.hashCode()));
            similarQuestion.setQuestion(question);
            similarQuestion.setAnswer(answer);
            similarQuestion.setKnowledgeIds(knowledgeIds != null ? knowledgeIds : "");
            
            // 存储到Redis
            similarQuestionRedisUtil.storeSimilarQuestion(similarQuestion, questionVector);
            
            log.info("存储相似问题成功: question={}, knowledgeIds={}", 
                    question.substring(0, Math.min(50, question.length())), knowledgeIds);
            
            return true;
            
        } catch (Exception e) {
            log.error("存储相似问题失败: question={}", question, e);
            return false;
        }
    }
    
    @Override
    @Async("taskExecutor")
    public void asyncStoreToSimilarQuestionLibrary(String question, String answer, String knowledgeIds) {
        try {
            storeToSimilarQuestionLibrary(question, answer, knowledgeIds);
        } catch (Exception e) {
            log.error("异步存储相似问题失败: question={}", question, e);
        }
    }
    
    @Override
    public boolean updateSimilarQuestionKnowledgeIds(String question, String knowledgeIds) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        
        try {
            // 重新存储以更新知识点关联
            SimilarQuestion sq = similarQuestionRedisUtil.getSimilarQuestion((long) Math.abs(question.hashCode()));
            if (sq != null) {
                sq.setKnowledgeIds(knowledgeIds);
                float[] questionVector = embeddingUtil.embed(question);
                if (questionVector != null && questionVector.length > 0) {
                    similarQuestionRedisUtil.storeSimilarQuestion(sq, questionVector);
                }
            }
            
            log.debug("更新相似问题知识点关联成功: question={}, knowledgeIds={}", 
                     question.substring(0, Math.min(50, question.length())), knowledgeIds);
            
            return true;
            
        } catch (Exception e) {
            log.error("更新相似问题知识点关联失败: question={}", question, e);
            return false;
        }
    }
    
    @Override
    @Async("taskExecutor")
    public void asyncUpdateSimilarQuestionKnowledgeIds(String question, String knowledgeIds) {
        try {
            updateSimilarQuestionKnowledgeIds(question, knowledgeIds);
        } catch (Exception e) {
            log.error("异步更新相似问题知识点关联失败: question={}", question, e);
        }
    }
    
    @Override
    public int cleanupLowQualitySimilarQuestions(int minVisitCount) {
        try {
            // 当前 SimilarQuestionRedisUtil 未提供批量清理接口，返回0
            log.info("清理低质量相似问题记录: minVisitCount={}", minVisitCount);
            return 0;
            
        } catch (Exception e) {
            log.error("清理低质量相似问题记录失败", e);
            return 0;
        }
    }
    
    @Override
    public int getSimilarQuestionCount() {
        try {
            // 通过搜索一个零向量来估算总数，当前返回0作为默认值
            return 0;
        } catch (Exception e) {
            log.error("获取相似问题库统计失败", e);
            return 0;
        }
    }
}
