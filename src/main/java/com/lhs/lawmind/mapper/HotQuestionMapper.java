package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.HotQuestion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 热点问题Mapper接口
 */
@Mapper
public interface HotQuestionMapper {
    
    /**
     * 根据MD5查询热点问题
     */
    HotQuestion selectByQuestionMd5(@Param("questionMd5") String questionMd5);
    
    /**
     * 插入热点问题
     */
    int insert(HotQuestion hotQuestion);
    
    /**
     * 更新热点问题
     */
    int update(HotQuestion hotQuestion);
    
    /**
     * 根据ID删除热点问题
     */
    int deleteById(@Param("id") Long id);
    
    /**
     * 查询过期的热点问题
     */
    List<HotQuestion> selectExpiredHotQuestions(@Param("currentTime") Date currentTime);
    
    /**
     * 查询访问次数超过阈值的问题
     */
    List<HotQuestion> selectHighFrequencyQuestions(@Param("threshold") Integer threshold);
    
    /**
     * 批量更新状态
     */
    int batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") Integer status);
    
    /**
     * 统计总热点问题数量
     */
    int countTotal();
    
    /**
     * 查询最近的热点问题
     */
    List<HotQuestion> selectRecentHotQuestions(@Param("limit") Integer limit);
}