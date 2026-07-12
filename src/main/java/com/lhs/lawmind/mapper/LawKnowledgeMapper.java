package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.LawKnowledge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface LawKnowledgeMapper {
    List<LawKnowledge> selectAll();
    List<LawKnowledge> selectPage(int offset, int pageSize);
    List<LawKnowledge> selectByLawType(String lawType);
    List<LawKnowledge> selectByLawTypeWithPage(String lawType, int offset, int pageSize);
    List<LawKnowledge> search(String keyword, int offset, int pageSize);
    List<LawKnowledge> searchByKeywordAndType(String keyword, String lawType, int offset, int pageSize);
    List<LawKnowledge> searchMultiTerm(@Param("terms") List<String> terms, @Param("offset") int offset, @Param("pageSize") int pageSize);
    List<LawKnowledge> searchByKeywordAndTypeMultiTerm(@Param("terms") List<String> terms, @Param("lawType") String lawType, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long count();
    long countByLawType(String lawType);
    long countByKeyword(String keyword);
    long countByKeywordAndType(String keyword, String lawType);
    long countByMultiTerm(@Param("terms") List<String> terms);
    long countByKeywordAndTypeMultiTerm(@Param("terms") List<String> terms, @Param("lawType") String lawType);
    /** 多关键词 OR LIKE 搜索：任一关键词匹配即命中（全文检索降级兜底） */
    List<LawKnowledge> searchOrTerms(@Param("terms") List<String> terms, @Param("offset") int offset, @Param("pageSize") int pageSize);
    List<LawKnowledge> searchByKeywordAndTypeOrTerms(@Param("terms") List<String> terms, @Param("lawType") String lawType, @Param("offset") int offset, @Param("pageSize") int pageSize);
    List<LawKnowledge> searchFulltext(@Param("booleanQuery") String booleanQuery, @Param("offset") int offset, @Param("pageSize") int pageSize);
    List<LawKnowledge> searchByKeywordAndTypeFulltext(@Param("booleanQuery") String booleanQuery, @Param("lawType") String lawType, @Param("offset") int offset, @Param("pageSize") int pageSize);
    /** 全文搜索 + 元数据过滤（只搜现行有效的法律，可选限定法律类型） */
    List<LawKnowledge> searchFulltextFiltered(@Param("booleanQuery") String booleanQuery, @Param("lawType") String lawType, @Param("offset") int offset, @Param("pageSize") int pageSize);
    /** 全文搜索 NATURAL LANGUAGE MODE 降级（长自然语言查询时使用，不要求所有 ngram 匹配） */
    List<LawKnowledge> searchFulltextFilteredNatural(@Param("query") String query, @Param("lawType") String lawType, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countByKeywordFulltext(@Param("booleanQuery") String booleanQuery);
    long countByKeywordAndTypeFulltext(@Param("booleanQuery") String booleanQuery, @Param("lawType") String lawType);
    LawKnowledge selectById(Long id);
    int insert(LawKnowledge lawKnowledge);
    int update(LawKnowledge lawKnowledge);
    int delete(Long id);
    List<LawKnowledge> selectUnvectorized();
    List<LawKnowledge> selectUnvectorizedWithOffset(int offset, int limit);
    long countUnvectorized();
    int batchUpdateVectorStatus(@Param("ids") List<Long> ids, @Param("status") int status);
}