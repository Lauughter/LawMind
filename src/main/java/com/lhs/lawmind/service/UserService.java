package com.lhs.lawmind.service;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.entity.User;
import java.util.List;

public interface UserService {
    List<User> selectAll();
    PageResult<User> selectPage(int page, int pageSize);
    User selectById(Long id);
    User selectByUsername(String username);
    int insert(User user);
    int update(User user);
    int delete(Long id);
    String encodePassword(String password);
    boolean verifyPassword(String rawPassword, String encodedPassword);
}