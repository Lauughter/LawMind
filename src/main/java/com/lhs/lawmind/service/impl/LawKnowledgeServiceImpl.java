package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.config.RagConfig;
import com.lhs.lawmind.entity.LawKnowledge;
import com.lhs.lawmind.mapper.LawKnowledgeMapper;
import com.lhs.lawmind.service.LawKnowledgeService;
import com.lhs.lawmind.utils.EmbeddingUtil;
import com.lhs.lawmind.utils.LawKnowledgeRedisUtil;
import com.lhs.lawmind.utils.RedisIndexUtil;
import com.lhs.lawmind.utils.SearchCacheUtil;
import com.lhs.lawmind.utils.RedisVectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 法律知识服务实现类
 * 封装法律知识相关的数据库操作
 * 实现LawKnowledgeService接口，提供法律知识的查询、分页、搜索、增删改和向量化功能
 */
@Slf4j
@Service
public class LawKnowledgeServiceImpl implements LawKnowledgeService {

    private final LawKnowledgeMapper lawKnowledgeMapper;
    private final EmbeddingUtil embeddingUtil;
    private final LawKnowledgeRedisUtil lawKnowledgeRedisUtil;
    private final RedisIndexUtil redisIndexUtil;
    private final SearchCacheUtil searchCacheUtil;
    private final RagConfig ragConfig;

    // 分页查询的最大限制，防止过大页码或页大小导致性能问题
    private static final int MAX_PAGE_SIZE = 100;
    // 最大页码限制，超过后返回空结果，提示用户调整查询条件
    private static final int MAX_PAGE = 50;

    public LawKnowledgeServiceImpl(
            LawKnowledgeMapper lawKnowledgeMapper,
            Optional<EmbeddingUtil> embeddingUtil,
            LawKnowledgeRedisUtil lawKnowledgeRedisUtil,
            RedisIndexUtil redisIndexUtil,
            SearchCacheUtil searchCacheUtil,
            RagConfig ragConfig) {
        this.lawKnowledgeMapper = lawKnowledgeMapper;
        this.embeddingUtil = embeddingUtil.orElse(null);
        this.lawKnowledgeRedisUtil = lawKnowledgeRedisUtil;
        this.redisIndexUtil = redisIndexUtil;
        this.searchCacheUtil = searchCacheUtil;
        this.ragConfig = ragConfig;
    }

    /**
     * 根据ID查询法律知识
     * <p>根据法律知识的唯一标识ID查询完整的法律知识信息</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 法律知识ID
     * @return 法律知识实体，未找到或查询失败返回null
     */
    @Override
    public LawKnowledge getById(Long id) {
        LawKnowledge knowledge = lawKnowledgeMapper.selectById(id);
        if (knowledge != null) {
            log.debug("查询法律知识成功: id={}", id);
        } else {
            log.debug("未找到法律知识: id={}", id);
        }
        return knowledge;
    }

    /**
     * 批量查询法律知识
     * <p>根据多个法律知识ID批量查询法律知识信息</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param ids 法律知识ID列表
     * @return 法律知识实体列表，查询失败返回空列表
     */
    @Override
    public List<LawKnowledge> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<LawKnowledge> knowledgeList = new ArrayList<>();
        for (Long id : ids) {
            LawKnowledge knowledge = lawKnowledgeMapper.selectById(id);
            if (knowledge != null) {
                knowledgeList.add(knowledge);
            }
        }
        log.debug("批量查询法律知识成功: 数量={}", knowledgeList.size());
        return knowledgeList;
    }

    /**
     * 分页查询法律知识
     * <p>分页查询法律知识列表</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param page 页码
     * @param pageSize 每页大小
     * @return 法律知识列表
     */
    @Override
    public List<LawKnowledge> selectPage(int page, int pageSize) {
        try {
            int clampedPage = Math.min(page, MAX_PAGE);
            int clampedSize = Math.min(pageSize, MAX_PAGE_SIZE);
            int offset = (clampedPage - 1) * clampedSize;
            List<LawKnowledge> knowledgeList = lawKnowledgeMapper.selectPage(offset, clampedSize);
            log.debug("分页查询法律知识成功: 页码={}, 每页大小={}, 数量={}", clampedPage, clampedSize, knowledgeList.size());
            return knowledgeList;
        } catch (Exception e) {
            log.error("分页查询法律知识失败: error={}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 根据法律类型分页查询
     * <p>根据法律类型分页查询法律知识列表</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param lawType 法律类型
     * @param page 页码
     * @param pageSize 每页大小
     * @return 法律知识列表
     */
    @Override
    public List<LawKnowledge> selectByLawType(String lawType, int page, int pageSize) {
        int clampedPage = Math.min(page, MAX_PAGE);
        int clampedSize = Math.min(pageSize, MAX_PAGE_SIZE);
        int offset = (clampedPage - 1) * clampedSize;
        List<LawKnowledge> knowledgeList = lawKnowledgeMapper.selectByLawTypeWithPage(lawType, offset, clampedSize);
        log.debug("根据法律类型分页查询成功: 法律类型={}, 页码={}, 每页大小={}, 数量={}", lawType, clampedPage, clampedSize, knowledgeList.size());
        return knowledgeList;
    }

    /**
     * 搜索法律知识
     * <p>根据关键词搜索法律知识</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param keyword 关键词
     * @param page 页码
     * @param pageSize 每页大小
     * @return 法律知识列表
     */
    @Override
    public List<LawKnowledge> search(String keyword, int page, int pageSize) {
        int clampedPage = Math.min(page, MAX_PAGE);
        int clampedSize = Math.min(pageSize, MAX_PAGE_SIZE);

        List<LawKnowledge> cached = searchCacheUtil.getSearchResult(keyword, null, clampedPage, clampedSize);
        if (cached != null) {
            log.debug("搜索缓存命中: keyword={}, page={}, pageSize={}", keyword, clampedPage, clampedSize);
            return cached;
        }

        try {
            int offset = (clampedPage - 1) * clampedSize;
            List<String> terms = splitSearchTerms(keyword);
            List<LawKnowledge> knowledgeList;

            boolean fulltextTried = false;
            if (canUseFulltext(terms)) {
                fulltextTried = true;
                try {
                    String booleanQuery = buildBooleanQuery(terms);
                    knowledgeList = lawKnowledgeMapper.searchFulltext(booleanQuery, offset, clampedSize);
                    if (!knowledgeList.isEmpty()) {
                        log.debug("FULLTEXT搜索成功: keyword={}, booleanQuery={}, 结果数={}", keyword, booleanQuery, knowledgeList.size());
                        searchCacheUtil.setSearchResult(keyword, null, clampedPage, clampedSize, knowledgeList);
                        return knowledgeList;
                    }
                    log.debug("FULLTEXT返回空结果，回退到LIKE: keyword={}", keyword);
                } catch (Exception e) {
                    log.warn("FULLTEXT搜索失败，回退到LIKE: keyword={}, error={}", keyword, e.getMessage());
                }
            }

            if (terms.size() <= 1) {
                knowledgeList = lawKnowledgeMapper.search(keyword, offset, clampedSize);
            } else {
                knowledgeList = lawKnowledgeMapper.searchMultiTerm(terms, offset, clampedSize);
            }
            log.debug("{}搜索成功: 关键词={}, 词条数={}, 页码={}, 数量={}",
                    fulltextTried ? "LIKE(回退)" : "LIKE", keyword, terms.size(), clampedPage, knowledgeList.size());
            searchCacheUtil.setSearchResult(keyword, null, clampedPage, clampedSize, knowledgeList);
            return knowledgeList;
        } catch (Exception e) {
            log.error("搜索法律知识失败: keyword={}, error={}", keyword, e.getMessage(), e);
            return List.of();
        }
    }

    @Override
    public List<LawKnowledge> searchByKeywordAndType(String keyword, String lawType, int page, int pageSize) {
        int clampedPage = Math.min(page, MAX_PAGE);
        int clampedSize = Math.min(pageSize, MAX_PAGE_SIZE);

        List<LawKnowledge> cached = searchCacheUtil.getSearchResult(keyword, lawType, clampedPage, clampedSize);
        if (cached != null) {
            log.debug("搜索缓存命中: keyword={}, type={}, page={}, pageSize={}", keyword, lawType, clampedPage, clampedSize);
            return cached;
        }

        try {
            int offset = (clampedPage - 1) * clampedSize;
            List<String> terms = splitSearchTerms(keyword);
            List<LawKnowledge> knowledgeList;

            boolean fulltextTried = false;
            if (canUseFulltext(terms)) {
                fulltextTried = true;
                try {
                    String booleanQuery = buildBooleanQuery(terms);
                    knowledgeList = lawKnowledgeMapper.searchByKeywordAndTypeFulltext(booleanQuery, lawType, offset, clampedSize);
                    if (!knowledgeList.isEmpty()) {
                        log.debug("FULLTEXT按类型搜索成功: keyword={}, booleanQuery={}, lawType={}, 结果数={}", keyword, booleanQuery, lawType, knowledgeList.size());
                        searchCacheUtil.setSearchResult(keyword, lawType, clampedPage, clampedSize, knowledgeList);
                        return knowledgeList;
                    }
                    log.debug("FULLTEXT返回空结果，回退到LIKE: keyword={}, lawType={}", keyword, lawType);
                } catch (Exception e) {
                    log.warn("FULLTEXT按类型搜索失败，回退到LIKE: keyword={}, lawType={}, error={}", keyword, lawType, e.getMessage());
                }
            }

            if (terms.size() <= 1) {
                knowledgeList = lawKnowledgeMapper.searchByKeywordAndType(keyword, lawType, offset, clampedSize);
            } else {
                knowledgeList = lawKnowledgeMapper.searchByKeywordAndTypeMultiTerm(terms, lawType, offset, clampedSize);
            }
            log.debug("{}按类型搜索成功: 关键词={}, 词条数={}, 类型={}, 数量={}",
                    fulltextTried ? "LIKE(回退)" : "LIKE", keyword, terms.size(), lawType, knowledgeList.size());
            searchCacheUtil.setSearchResult(keyword, lawType, clampedPage, clampedSize, knowledgeList);
            return knowledgeList;
        } catch (Exception e) {
            log.error("按关键词和类型搜索失败: keyword={}, lawType={}, error={}", keyword, lawType, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * 根据ID查询法律知识
     * <p>根据法律知识的唯一标识ID查询完整的法律知识信息</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 法律知识ID
     * @return 法律知识实体
     */
    @Override
    public LawKnowledge selectById(Long id) {
        return getById(id);
    }

    /**
     * 插入法律知识
     * <p>插入新的法律知识</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param lawKnowledge 法律知识实体
     * @return 插入成功的记录数
     */
    @Override
    public int insert(LawKnowledge lawKnowledge) {
        if (ragConfig.isDedupEnabled() && isDuplicateContent(lawKnowledge.getContent())) {
            log.info("知识去重：跳过重复内容 title=\"{}\"", lawKnowledge.getTitle());
            return 0;
        }
        try {
            int result = lawKnowledgeMapper.insert(lawKnowledge);
            if (result > 0) {
                log.debug("插入法律知识成功: id={}", lawKnowledge.getId());
            } else {
                log.warn("插入法律知识失败");
            }
            return result;
        } catch (Exception e) {
            log.error("插入法律知识失败: error={}", e.getMessage(), e);
            return 0;
        }
    }

    @Override
    public boolean isDuplicateContent(String content) {
        if (!ragConfig.isDedupEnabled() || embeddingUtil == null || lawKnowledgeRedisUtil == null) {
            return false;
        }
        try {
            float[] vector = embeddingUtil.embed(content);
            List<RedisVectorUtil.SearchResult> results = lawKnowledgeRedisUtil.searchLawKnowledge(vector, 3);
            double threshold = ragConfig.getDedupThreshold();
            for (RedisVectorUtil.SearchResult r : results) {
                if (r.getScore() >= threshold) {
                    log.info("知识去重命中: score={} threshold={} matchedKey={}",
                            String.format("%.4f", r.getScore()), threshold, r.getKey());
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("知识去重检查失败，放行入库: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 更新法律知识
     * <p>更新已存在的法律知识</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param lawKnowledge 法律知识实体
     * @return 更新成功的记录数
     */
    @Override
    public int update(LawKnowledge lawKnowledge) {
        try {
            int result = lawKnowledgeMapper.update(lawKnowledge);
            if (result > 0) {
                log.debug("更新法律知识成功: id={}", lawKnowledge.getId());
            } else {
                log.debug("更新法律知识失败: id={}", lawKnowledge.getId());
            }
            return result;
        } catch (Exception e) {
            log.error("更新法律知识失败: id={}, error={}", lawKnowledge.getId(), e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 删除法律知识
     * <p>根据ID删除法律知识</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @param id 法律知识ID
     * @return 删除成功的记录数
     */
    @Override
    public int delete(Long id) {
        try {
            int result = lawKnowledgeMapper.delete(id);
            if (result > 0) {
                log.debug("删除法律知识成功: id={}", id);
            } else {
                log.debug("删除法律知识失败: id={}", id);
            }
            return result;
        } catch (Exception e) {
            log.error("删除法律知识失败: id={}, error={}", id, e.getMessage(), e);
            return 0;
        }
    }

    /**
     * 统计法律知识总数
     * <p>统计法律知识库中的总记录数</p>
     * <p>包含异常处理和日志记录</p>
     *
     * @return 法律知识总数，查询失败返回0
     */
    @Override
    public long count() {
        return lawKnowledgeMapper.count();
    }

    /**
     * 根据法律类型统计法律知识数量
     * <p>统计特定法律类型的法律知识数量</p>
     * <p>包含异常处理和日志记录</p>
     * @param lawType
     * @return
     */
    @Override
    public long countByLawType(String lawType) {
        return lawKnowledgeMapper.countByLawType(lawType);
    }

    /**
     * 根据关键词统计法律知识数量
     * <p>统计包含特定关键词的法律知识数量</p>
     * <p>包含异常处理和日志记录</p>
     * @param keyword
     * @return
     */
    @Override
    public long countByKeyword(String keyword) {
        Long cached = searchCacheUtil.getSearchCount(keyword, null);
        if (cached != null) {
            log.debug("计数缓存命中: keyword={}", keyword);
            return cached;
        }

        List<String> terms = splitSearchTerms(keyword);
        long count;
        if (canUseFulltext(terms)) {
            try {
                count = lawKnowledgeMapper.countByKeywordFulltext(buildBooleanQuery(terms));
                if (count > 0) {
                    searchCacheUtil.setSearchCount(keyword, null, count);
                    return count;
                }
                log.debug("FULLTEXT计数为0，回退到LIKE: keyword={}", keyword);
            } catch (Exception e) {
                log.warn("FULLTEXT计数失败，回退到LIKE: keyword={}, error={}", keyword, e.getMessage());
            }
        }
        if (terms.size() <= 1) {
            count = lawKnowledgeMapper.countByKeyword(keyword);
        } else {
            count = lawKnowledgeMapper.countByMultiTerm(terms);
        }
        searchCacheUtil.setSearchCount(keyword, null, count);
        return count;
    }

    /**
     * 根据关键词和法律类型统计法律知识数量
     * <p>统计包含特定关键词且属于特定法律类型的法律知识数量</p>
     * <p>包含异常处理和日志记录</p>
     * @param keyword
     * @param lawType
     * @return
     */
    @Override
    public long countByKeywordAndType(String keyword, String lawType) {
        Long cached = searchCacheUtil.getSearchCount(keyword, lawType);
        if (cached != null) {
            log.debug("计数缓存命中: keyword={}, type={}", keyword, lawType);
            return cached;
        }

        List<String> terms = splitSearchTerms(keyword);
        long count;
        if (canUseFulltext(terms)) {
            try {
                count = lawKnowledgeMapper.countByKeywordAndTypeFulltext(buildBooleanQuery(terms), lawType);
                if (count > 0) {
                    searchCacheUtil.setSearchCount(keyword, lawType, count);
                    return count;
                }
                log.debug("FULLTEXT计数为0，回退到LIKE: keyword={}, lawType={}", keyword, lawType);
            } catch (Exception e) {
                log.warn("FULLTEXT按类型计数失败，回退到LIKE: keyword={}, lawType={}, error={}", keyword, lawType, e.getMessage());
            }
        }
        if (terms.size() <= 1) {
            count = lawKnowledgeMapper.countByKeywordAndType(keyword, lawType);
        } else {
            count = lawKnowledgeMapper.countByKeywordAndTypeMultiTerm(terms, lawType);
        }
        searchCacheUtil.setSearchCount(keyword, lawType, count);
        return count;
    }

    /**
     * 将中文搜索关键词拆分为多个词条
     * 识别法律结构标记（第X条/章/节/编/款/项），拆分为独立词条做 AND 搜索
     * 也支持空格/逗号分隔的手动多词搜索
     */
    private List<String> splitSearchTerms(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }

        List<String> terms = new ArrayList<>();

        // 法律结构编号模式：第X条, 第X章, 第X节, 第X编, 第X款, 第X项
        Pattern legalPattern = Pattern.compile("第[^条章节编款项]{1,10}[条章节编款项]");

        for (String part : keyword.trim().split("[\\s,，]+")) {
            if (part.isEmpty()) continue;

            Matcher matcher = legalPattern.matcher(part);
            int lastEnd = 0;
            while (matcher.find()) {
                String before = part.substring(lastEnd, matcher.start()).trim();
                if (!before.isEmpty()) {
                    terms.add(before);
                }
                terms.add(matcher.group());
                lastEnd = matcher.end();
            }
            String after = part.substring(lastEnd).trim();
            if (!after.isEmpty()) {
                terms.add(after);
            }
            if (lastEnd == 0) {
                terms.add(part);
            }
        }

        List<String> result = new ArrayList<>();
        for (String term : terms) {
            if (!result.contains(term)) {
                result.add(term);
            }
        }
        log.debug("搜索关键词拆分: keyword={}, terms={}", keyword, result);
        return result;
    }

    /**
     * 构建 FULLTEXT 布尔模式查询字符串
     * "+term1 +term2" 表示所有词必须匹配
     */
    private String buildBooleanQuery(List<String> terms) {
        StringBuilder sb = new StringBuilder();
        for (String term : terms) {
            if (!sb.isEmpty()) sb.append(" ");
            // 转义 FULLTEXT 布尔模式特殊字符
            String escaped = term.replaceAll("[+\\-><()~*\"@]", "");
            sb.append("+").append(escaped);
        }
        return sb.toString();
    }

    /**
     * 判断是否可用 FULLTEXT 搜索
     * ngram 分词器最小 token 长度为 2，所有词条长度 >= 2 才使用 FULLTEXT
     */
    private boolean canUseFulltext(List<String> terms) {
        if (terms.isEmpty()) return false;
        for (String term : terms) {
            if (term.length() < 2) return false;
        }
        return true;
    }

    /**
     * 向量化并存储法律知识
     * <p>对法律知识进行向量化并存储到Redis</p>
     * <p>包含异常处理和日志记录</p>
     */
    @Override
    public void vectorizeAndStore() {
        log.info("开始向量化法律知识");
        
        // 检查法律知识库向量索引是否存在
        if (!redisIndexUtil.indexExists(lawKnowledgeRedisUtil.getRedisTemplate(), "idx:law_knowledge")) {
            log.error("法律知识库向量索引不存在，无法执行向量化操作");
            return;
        }
        
        // 获取所有法律知识
        List<LawKnowledge> knowledgeList = lawKnowledgeMapper.selectAll();
        log.info("获取到法律知识数量: {}", knowledgeList.size());
        
        int successCount = 0;
        for (LawKnowledge knowledge : knowledgeList) {
            try {
                float[] vector = embeddingUtil.embed(knowledge.getContent());
                if (vector == null || vector.length == 0) {
                    log.warn("向量化跳过（向量为空）: id={}", knowledge.getId());
                    continue;
                }
                lawKnowledgeRedisUtil.storeLawKnowledge(
                        knowledge.getId(),
                        knowledge.getTitle(),
                        knowledge.getLawType(),
                        knowledge.getContent(),
                        vector
                );
                knowledge.setVectorStatus(1);
                lawKnowledgeMapper.update(knowledge);
                successCount++;
            } catch (Exception e) {
                log.error("向量化法律知识失败: id={}, error={}", knowledge.getId(), e.getMessage(), e);
            }
        }
        
        log.info("向量化法律知识完成，成功: {}, 失败: {}", successCount, knowledgeList.size() - successCount);
    }
}