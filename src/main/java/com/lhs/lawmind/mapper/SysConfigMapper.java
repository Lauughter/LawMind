package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.SysConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysConfigMapper {
    List<SysConfig> selectAll();
    List<SysConfig> selectAllPage(@Param("offset") int offset, @Param("pageSize") int pageSize);
    long countAll();
    SysConfig selectByKey(String configKey);
    SysConfig selectById(Integer id);
    int insert(SysConfig sysConfig);
    int update(SysConfig sysConfig);
    int delete(Integer id);
}