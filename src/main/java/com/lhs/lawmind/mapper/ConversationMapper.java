package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 会话 Mapper 接口
 */
@Mapper
public interface ConversationMapper {
    int insert(Conversation conversation);
    Conversation selectById(Long id);
    List<Conversation> selectByUserId(@Param("userId") Long userId);
    int updateTitle(@Param("id") Long id, @Param("title") String title);
    int softDelete(Long id);
    int updateUpdateTime(Long id);
    List<Conversation> selectByUserIdPage(@Param("userId") Long userId, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countByUserId(@Param("userId") Long userId);
}
