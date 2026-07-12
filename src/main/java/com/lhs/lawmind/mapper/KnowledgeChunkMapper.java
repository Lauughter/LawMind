package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.KnowledgeChunk;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface KnowledgeChunkMapper {
    int insert(KnowledgeChunk chunk);
    int update(KnowledgeChunk chunk);
    KnowledgeChunk selectById(Long id);
    List<KnowledgeChunk> selectByKnowledgeId(Long knowledgeId);
    List<KnowledgeChunk> selectUnvectorizedWithOffset(int offset, int limit);
    long countUnvectorized();
    int batchUpdateVectorStatus(@Param("ids") List<Long> ids, @Param("status") int status);
}
