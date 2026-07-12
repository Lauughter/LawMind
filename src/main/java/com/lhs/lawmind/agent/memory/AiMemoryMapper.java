package com.lhs.lawmind.agent.memory;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 统一记忆表 MyBatis Mapper。
 */
@Mapper
public interface AiMemoryMapper {

    int insert(AiMemory memory);

    AiMemory selectById(@Param("id") Long id);

    List<AiMemory> selectByUserId(@Param("userId") Long userId);

    List<AiMemory> selectByUserIdAndType(@Param("userId") Long userId, @Param("type") String type);

    List<AiMemory> selectTopByImportance(@Param("userId") Long userId, @Param("limit") int limit);

    int update(AiMemory memory);

    int updateAccessInfo(@Param("id") Long id);

    int deleteById(@Param("id") Long id, @Param("userId") Long userId);

    int deleteByUserId(@Param("userId") Long userId);

    List<AiMemory> selectForDecay(@Param("userId") Long userId, @Param("type") String type,
                                  @Param("beforeDate") String beforeDate);

    int updateImportance(@Param("id") Long id, @Param("importance") Double importance);

    long countByUserId(@Param("userId") Long userId);

    List<AiMemory> selectLowestImportance(@Param("userId") Long userId, @Param("limit") int limit);
}
