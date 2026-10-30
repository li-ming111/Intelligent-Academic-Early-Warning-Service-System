package com.academic.service.impl;

import com.academic.common.entity.User;
import com.academic.mapper.UserMapper;
import com.academic.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    @Cacheable(value = "users", key = "'username:' + #username")
    public User getByUsername(String username) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        return baseMapper.selectOne(wrapper);
    }

    @Override
    @Cacheable(value = "users", key = "'role:' + #role")
    public List<User> getUsersByRole(Integer role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getRole, role);
        wrapper.eq(User::getStatus, 1);
        return baseMapper.selectList(wrapper);
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public boolean createUser(User user) {
        User existingUser = getByUsername(user.getUsername());
        if (existingUser != null) {
            return false;
        }
        // 口令留空 → 演示口令 123456。建号表单（小程序 users.vue）写着"留空则与演示口令一致"，
        // 但从前这里直接 encode(null) 抛 IllegalArgumentException（回 500"Create user failed"、
        // 事务回滚），也就是说**留空根本建不出号** —— 表单的承诺一直是空的。
        // 与 resetPassword 的口径一致：本库全部账号本就用统一演示口令 123456。
        String rawPassword = user.getPassword();
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            rawPassword = "123456";
        }
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        user.setStatus(1);
        return save(user);
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public boolean updateUser(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        return updateById(user);
    }

    @Override
    @CacheEvict(value = "users", allEntries = true)
    public boolean deleteUser(Long id) {
        return removeById(id);
    }

    @Override
    @Cacheable(value = "users", key = "'search:' + #keyword")
    public List<User> searchUsers(String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(User::getUsername, keyword)
               .or()
               .like(User::getName, keyword)
               .or()
               .like(User::getEmail, keyword)
               .or()
               .like(User::getPhone, keyword);
        wrapper.eq(User::getStatus, 1);
        return baseMapper.selectList(wrapper);
    }

    @Override
    public boolean validateLogin(String username, String password) {
        User user = getByUsername(username);
        if (user != null && passwordEncoder.matches(password, user.getPassword()) && user.getStatus() == 1) {
            return true;
        }
        return false;
    }

    @Override
    @Cacheable(value = "users", key = "'recent:' + #limit")
    public List<User> getRecentUsers(int limit) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(User::getCreatedAt);
        wrapper.last("LIMIT " + limit);
        return baseMapper.selectList(wrapper);
    }
}
