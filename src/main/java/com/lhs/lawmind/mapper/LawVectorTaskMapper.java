package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.LawVectorTask;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface LawVectorTaskMapper {
    List<LawVectorTask> selectAll();
    List<LawVectorTask> selectByKnowledgeId(Long knowledgeId);
    LawVectorTask selectById(Long id);
    int insert(LawVectorTask lawVectorTask);
    int update(LawVectorTask lawVectorTask);
    int delete(Long id);
}