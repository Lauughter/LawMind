package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.entity.LawVectorTask;
import com.lhs.lawmind.mapper.LawVectorTaskMapper;
import com.lhs.lawmind.service.LawVectorTaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LawVectorTaskServiceImpl implements LawVectorTaskService {

    private final LawVectorTaskMapper lawVectorTaskMapper;

    @Override
    public List<LawVectorTask> selectAll() {
        return lawVectorTaskMapper.selectAll();
    }

    @Override
    public List<LawVectorTask> selectByKnowledgeId(Long knowledgeId) {
        return lawVectorTaskMapper.selectByKnowledgeId(knowledgeId);
    }

    @Override
    public LawVectorTask selectById(Long id) {
        return lawVectorTaskMapper.selectById(id);
    }

    @Override
    public int insert(LawVectorTask lawVectorTask) {
        return lawVectorTaskMapper.insert(lawVectorTask);
    }

    @Override
    public int update(LawVectorTask lawVectorTask) {
        return lawVectorTaskMapper.update(lawVectorTask);
    }

    @Override
    public int delete(Long id) {
        return lawVectorTaskMapper.delete(id);
    }
}
