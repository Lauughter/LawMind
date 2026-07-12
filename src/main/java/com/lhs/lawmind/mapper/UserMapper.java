package com.lhs.lawmind.mapper;

import com.lhs.lawmind.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {
    List<User> selectAll();
    User selectById(Long id);
    User selectByUsername(String username);
    int insert(User user);
    int update(User user);
    int delete(Long id);
    List<User> selectPage(@Param("offset") int offset, @Param("pageSize") int pageSize);
    long countAll();
}