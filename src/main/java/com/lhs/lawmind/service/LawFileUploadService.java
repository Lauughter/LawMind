package com.lhs.lawmind.service;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.entity.LawFileUpload;

import java.util.List;

public interface LawFileUploadService {
    List<LawFileUpload> selectAll();
    List<LawFileUpload> selectByUserId(Long userId);
    PageResult<LawFileUpload> selectPage(int page, int pageSize);
    PageResult<LawFileUpload> selectByUserIdPage(Long userId, int page, int pageSize);
    LawFileUpload selectById(Long id);
    int insert(LawFileUpload lawFileUpload);
    int update(LawFileUpload lawFileUpload);
    int delete(Long id);
}