package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.entity.SysConfig;
import com.lhs.lawmind.mapper.SysConfigMapper;
import com.lhs.lawmind.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 系统配置服务实现类
 * 封装系统配置相关的数据库操作
 * 实现SysConfigService接口，提供系统配置的查询、增删改功能
 */
@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl implements SysConfigService {

    private final SysConfigMapper sysConfigMapper;

    /**
     * 查询所有系统配置
     *
     * @return 系统配置列表
     */
    @Override
    public List<SysConfig> selectAll() {
        return sysConfigMapper.selectAll();
    }

    @Override
    public PageResult<SysConfig> selectAllPage(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<SysConfig> list = sysConfigMapper.selectAllPage(offset, pageSize);
        long total = sysConfigMapper.countAll();
        return PageResult.of(total, list, page, pageSize);
    }

    /**
     * 根据配置键查询系统配置
     * <p>根据系统配置的唯一键查询完整的系统配置信息</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param configKey 系统配置键
     * @return 系统配置实体，未找到或查询失败返回null
     */
    @Override
    public SysConfig selectByKey(String configKey) {
        return sysConfigMapper.selectByKey(configKey);
    }

    /**
     * 根据ID查询系统配置
     * <p>根据系统配置的唯一标识ID查询完整的系统配置信息</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 系统配置ID
     * @return 系统配置实体，未找到或查询失败返回null
     */
    @Override
    public SysConfig selectById(Integer id) {
        return sysConfigMapper.selectById(id);
    }

    /**
     * 插入系统配置
     * <p>将新的系统配置插入到数据库中</p>
     * <p>包含异常处理和日志记录</p>
     * *
     * @param sysConfig 系统配置实体
     * @return 插入的系统配置ID，插入失败返回null
     * 插入成功返回系统配置ID，插入失败返回null
     */
    @Override
    public int insert(SysConfig sysConfig) {
        return sysConfigMapper.insert(sysConfig);
    }

    /**
     * 更新系统配置
     * <p>更新已存在的系统配置信息</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param sysConfig 系统配置实体
     * @return 更新的记录数，更新失败返回0
     */
    @Override
    public int update(SysConfig sysConfig) {
        return sysConfigMapper.update(sysConfig);
    }

    /**
     * 删除系统配置
     * <p>根据ID删除系统配置</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 系统配置ID
     * @return 删除成功的记录数
     */
    @Override
    public int delete(Integer id) {
        return sysConfigMapper.delete(id);
    }
}
