package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.LawFileUpload;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LawFileUploadMapper {
    List<LawFileUpload> selectAll();
    List<LawFileUpload> selectByUserId(Long userId);
    LawFileUpload selectById(Long id);
    int insert(LawFileUpload lawFileUpload);
    int update(LawFileUpload lawFileUpload);
    int delete(Long id);
    List<LawFileUpload> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);
    long countAll();
    List<LawFileUpload> selectByUserIdPage(@Param("userId") Long userId, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countByUserId(@Param("userId") Long userId);
}