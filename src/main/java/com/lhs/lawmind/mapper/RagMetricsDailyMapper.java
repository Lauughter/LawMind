package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.RagMetricsDaily;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RagMetricsDailyMapper {
    int insert(RagMetricsDaily metrics);
    int upsert(RagMetricsDaily metrics);
    RagMetricsDaily selectByDate(@Param("metricDate") String metricDate);
    List<RagMetricsDaily> selectTrend(@Param("days") int days);
}
