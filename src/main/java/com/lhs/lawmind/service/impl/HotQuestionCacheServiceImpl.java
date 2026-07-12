package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.entity.HotQuestion;
import com.lhs.lawmind.mapper.HotQuestionMapper;
import com.lhs.lawmind.service.HotQuestionCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Calendar;
import java.util.Date;

/**
 * 热点问题缓存服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HotQuestionCacheServiceImpl implements HotQuestionCacheService {
    
    private final HotQuestionMapper hotQuestionMapper;
    
    @Override
    public HotQuestion getHotQuestionByMd5(String questionMd5) {
        if (!StringUtils.hasText(questionMd5)) {
            return null;
        }
        
        try {
            HotQuestion hotQuestion = hotQuestionMapper.selectByQuestionMd5(questionMd5);
            if (hotQuestion != null) {
                log.debug("命中热点缓存: questionMd5={}", questionMd5);
                return hotQuestion;
            }
        } catch (Exception e) {
            log.error("查询热点问题缓存失败: questionMd5={}", questionMd5, e);
        }
        
        return null;
    }
    
    @Override
    public boolean isHotQuestion(String questionMd5) {
        return getHotQuestionByMd5(questionMd5) != null;
    }
    
    @Override
    public String getHotAnswer(String questionMd5) {
        HotQuestion hotQuestion = getHotQuestionByMd5(questionMd5);
        return hotQuestion != null ? hotQuestion.getCachedAnswer() : null;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean upgradeToHotCache(String questionMd5, String originalQuestion, 
                                   String answer, String knowledgeIds, int initialTtlDays) {
        if (!StringUtils.hasText(questionMd5) || !StringUtils.hasText(answer)) {
            log.warn("参数不完整，无法升级热点缓存: questionMd5={}", questionMd5);
            return false;
        }
        
        try {
            // 检查是否已存在
            HotQuestion existing = hotQuestionMapper.selectByQuestionMd5(questionMd5);
            if (existing != null) {
                log.debug("热点缓存已存在，更新访问统计: questionMd5={}", questionMd5);
                updateVisitCount(questionMd5);
                return true;
            }
            
            // 创建新的热点问题记录
            HotQuestion hotQuestion = new HotQuestion();
            hotQuestion.setQuestionMd5(questionMd5);
            hotQuestion.setOriginalQuestion(originalQuestion);
            hotQuestion.setCachedAnswer(answer);
            hotQuestion.setKnowledgeIds(knowledgeIds);
            hotQuestion.setVisitCount(1);
            hotQuestion.setFirstVisitTime(new Date());
            hotQuestion.setLastVisitTime(new Date());
            hotQuestion.setCreateTime(new Date());
            
            // 设置过期时间
            Calendar calendar = Calendar.getInstance();
            calendar.add(Calendar.DAY_OF_YEAR, initialTtlDays);
            hotQuestion.setExpireTime(calendar.getTime());
            
            hotQuestion.setStatus(0); // 有效状态
            
            int result = hotQuestionMapper.insert(hotQuestion);
            if (result > 0) {
                log.info("热点问题缓存升级成功: questionMd5={}, initialTtlDays={}", 
                        questionMd5, initialTtlDays);
                return true;
            }
            
        } catch (Exception e) {
            log.error("升级热点缓存失败: questionMd5={}", questionMd5, e);
        }
        
        return false;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateVisitCount(String questionMd5) {
        if (!StringUtils.hasText(questionMd5)) {
            return 0;
        }
        
        try {
            HotQuestion hotQuestion = hotQuestionMapper.selectByQuestionMd5(questionMd5);
            if (hotQuestion != null) {
                hotQuestion.setVisitCount(hotQuestion.getVisitCount() + 1);
                hotQuestion.setLastVisitTime(new Date());
                hotQuestionMapper.update(hotQuestion);
                
                log.debug("更新热点问题访问统计: questionMd5={}, visitCount={}", 
                        questionMd5, hotQuestion.getVisitCount());
                
                return hotQuestion.getVisitCount();
            }
        } catch (Exception e) {
            log.error("更新热点问题访问统计失败: questionMd5={}", questionMd5, e);
        }
        
        return 0;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int cleanupExpiredCache() {
        try {
            Date currentTime = new Date();
            var expiredQuestions = hotQuestionMapper.selectExpiredHotQuestions(currentTime);
            
            if (expiredQuestions != null && !expiredQuestions.isEmpty()) {
                // 批量更新状态为已过期
                var ids = expiredQuestions.stream()
                    .map(HotQuestion::getId)
                    .toList();
                
                int updated = hotQuestionMapper.batchUpdateStatus(ids, 1);
                log.info("清理过期热点缓存完成: 清理数量={}", updated);
                return updated;
            }
            
        } catch (Exception e) {
            log.error("清理过期热点缓存失败", e);
        }
        
        return 0;
    }
    
    @Override
    public int getCacheCount() {
        try {
            return hotQuestionMapper.countTotal();
        } catch (Exception e) {
            log.error("获取热点缓存统计失败", e);
            return 0;
        }
    }
}