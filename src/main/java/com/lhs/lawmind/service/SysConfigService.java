package com.lhs.lawmind.service;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.entity.SysConfig;

import java.util.List;

public interface SysConfigService {
    List<SysConfig> selectAll();
    PageResult<SysConfig> selectAllPage(int page, int pageSize);
    SysConfig selectByKey(String configKey);
    SysConfig selectById(Integer id);
    int insert(SysConfig sysConfig);
    int update(SysConfig sysConfig);
    int delete(Integer id);
}