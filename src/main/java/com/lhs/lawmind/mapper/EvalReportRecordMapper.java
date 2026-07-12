package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.EvalReportRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface EvalReportRecordMapper {
    int insert(EvalReportRecord record);
    List<EvalReportRecord> selectRecent(int limit);
    EvalReportRecord selectByRunId(String runId);
}
