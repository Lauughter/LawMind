package com.lhs.lawmind.service.impl;

import com.lhs.lawmind.common.PageResult;
import com.lhs.lawmind.entity.User;
import com.lhs.lawmind.mapper.UserMapper;
import com.lhs.lawmind.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public List<User> selectAll() {
        return userMapper.selectAll();
    }

    @Override
    public PageResult<User> selectPage(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<User> list = userMapper.selectPage(offset, pageSize);
        long total = userMapper.countAll();
        return PageResult.of(total, list, page, pageSize);
    }

    @Override
    public User selectById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    public User selectByUsername(String username) {
        return userMapper.selectByUsername(username);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(User user) {
        user.setPassword(encodePassword(user.getPassword()));
        user.setCreateTime(new Date());
        return userMapper.insert(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(User user) {
        String pwd = user.getPassword();
        if (pwd != null && !pwd.isEmpty() && !pwd.startsWith("$2a$")) {
            user.setPassword(encodePassword(pwd));
        }
        return userMapper.update(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return userMapper.delete(id);
    }

    @Override
    public String encodePassword(String password) {
        return passwordEncoder.encode(password);
    }

    @Override
    public boolean verifyPassword(String rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
