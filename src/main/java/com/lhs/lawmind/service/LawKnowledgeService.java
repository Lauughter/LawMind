package com.lhs.lawmind.service;

import com.lhs.lawmind.entity.LawKnowledge;

import java.util.List;

/**
 * 法律知识服务接口
 * 封装法律知识相关的数据库操作
 * 提供法律知识的查询、分页、搜索、增删改和向量化功能
 */
public interface LawKnowledgeService {

    /**
     * 根据ID查询法律知识
     * <p>根据法律知识的唯一标识ID查询完整的法律知识信息</p>
     *
     * @param id 法律知识ID
     * @return 法律知识实体，未找到返回null
     */
    LawKnowledge getById(Long id);

    /**
     * 批量查询法律知识
     * <p>根据多个法律知识ID批量查询法律知识信息</p>
     *
     * @param ids 法律知识ID列表
     * @return 法律知识实体列表，查询失败返回空列表
     */
    List<LawKnowledge> getByIds(List<Long> ids);

    /**
     * 分页查询法律知识
     * <p>分页查询法律知识列表</p>
     *
     * @param page 页码
     * @param pageSize 每页大小
     * @return 法律知识列表
     */
    List<LawKnowledge> selectPage(int page, int pageSize);

    /**
     * 根据法律类型分页查询
     * <p>根据法律类型分页查询法律知识列表</p>
     *
     * @param lawType 法律类型
     * @param page 页码
     * @param pageSize 每页大小
     * @return 法律知识列表
     */
    List<LawKnowledge> selectByLawType(String lawType, int page, int pageSize);

    /**
     * 搜索法律知识
     * <p>根据关键词搜索法律知识</p>
     *
     * @param keyword 关键词
     * @param page 页码
     * @param pageSize 每页大小
     * @return 法律知识列表
     */
    List<LawKnowledge> search(String keyword, int page, int pageSize);

    List<LawKnowledge> searchByKeywordAndType(String keyword, String lawType, int page, int pageSize);

    /**
     * 根据ID查询法律知识
     * <p>根据法律知识的唯一标识ID查询完整的法律知识信息</p>
     *
     * @param id 法律知识ID
     * @return 法律知识实体
     */
    LawKnowledge selectById(Long id);

    /**
     * 插入法律知识
     * <p>插入新的法律知识</p>
     *
     * @param lawKnowledge 法律知识实体
     * @return 插入成功的记录数
     */
    int insert(LawKnowledge lawKnowledge);

    /**
     * 更新法律知识
     * <p>更新已存在的法律知识</p>
     *
     * @param lawKnowledge 法律知识实体
     * @return 更新成功的记录数
     */
    int update(LawKnowledge lawKnowledge);

    /**
     * 删除法律知识
     * <p>根据ID删除法律知识</p>
     *
     * @param id 法律知识ID
     * @return 删除成功的记录数
     */
    int delete(Long id);

    /**
     * 统计法律知识总数
     */
    long count();

    /**
     * 根据法律类型统计数量
     */
    long countByLawType(String lawType);

    /**
     * 根据关键词统计数量
     */
    long countByKeyword(String keyword);

    /**
     * 根据关键词和法律类型统计数量
     */
    long countByKeywordAndType(String keyword, String lawType);

    /**
     * 向量化并存储法律知识
     */
    void vectorizeAndStore();

    /**
     * 检查内容是否与已有知识重复
     * 对内容进行向量化后在Redis中搜索最相似的K条记录，若存在相似度 >= 配置阈值的记录则视为重复
     *
     * @param content 待检查的文本内容
     * @return true=重复/高度相似, false=不重复
     */
    boolean isDuplicateContent(String content);
}