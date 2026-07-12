package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.entity.LawFileUpload;
import com.lhs.lawmind.mapper.LawFileUploadMapper;
import com.lhs.lawmind.service.LawFileUploadService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LawFileUploadServiceImpl implements LawFileUploadService {

    private final LawFileUploadMapper lawFileUploadMapper;

    @Override
    public List<LawFileUpload> selectAll() {
        return lawFileUploadMapper.selectAll();
    }

    @Override
    public List<LawFileUpload> selectByUserId(Long userId) {
        return lawFileUploadMapper.selectByUserId(userId);
    }

    @Override
    public PageResult<LawFileUpload> selectPage(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<LawFileUpload> list = lawFileUploadMapper.selectPage(offset, pageSize);
        long total = lawFileUploadMapper.countAll();
        return PageResult.of(total, list, page, pageSize);
    }

    @Override
    public PageResult<LawFileUpload> selectByUserIdPage(Long userId, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<LawFileUpload> list = lawFileUploadMapper.selectByUserIdPage(userId, offset, pageSize);
        long total = lawFileUploadMapper.countByUserId(userId);
        return PageResult.of(total, list, page, pageSize);
    }

    @Override
    public LawFileUpload selectById(Long id) {
        return lawFileUploadMapper.selectById(id);
    }

    @Override
    public int insert(LawFileUpload lawFileUpload) {
        return lawFileUploadMapper.insert(lawFileUpload);
    }

    @Override
    public int update(LawFileUpload lawFileUpload) {
        return lawFileUploadMapper.update(lawFileUpload);
    }

    @Override
    public int delete(Long id) {
        return lawFileUploadMapper.delete(id);
    }
}
