package com.lhs.lawmind.service;

import com.lhs.lawmind.entity.LawVectorTask;

import java.util.List;

public interface LawVectorTaskService {
    List<LawVectorTask> selectAll();
    List<LawVectorTask> selectByKnowledgeId(Long knowledgeId);
    LawVectorTask selectById(Long id);
    int insert(LawVectorTask lawVectorTask);
    int update(LawVectorTask lawVectorTask);
    int delete(Long id);
}