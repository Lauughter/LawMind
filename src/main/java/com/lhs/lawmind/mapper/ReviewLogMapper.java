package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.ReviewLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ReviewLogMapper {
    int insert(ReviewLog log);
    List<ReviewLog> selectUnprocessed();
    int markProcessed(Long id);
}
