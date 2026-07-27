package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qianguanguan.constant.Role;
import com.qianguanguan.entity.User;
import com.qianguanguan.mapper.UserMapper;
import com.qianguanguan.model.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    public LoginResponse.UserInfo getUserInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        return LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .wid(user.getWid())
                .createtime(user.getCreatetime())
                .build();
    }

    public IPage<User> list(String username, String role, Integer status, Integer pageNum, Integer pageSize) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (username != null && !username.isEmpty()) {
            wrapper.like(User::getUsername, username);
        }
        if (role != null && !role.isEmpty()) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        wrapper.orderByDesc(User::getCreatetime);

        Page<User> page = new Page<>(pageNum != null ? pageNum : 1, pageSize != null ? pageSize : 10);
        return userMapper.selectPage(page, wrapper);
    }

    public void toggleStatus(String requesterRole, Long id) {
        if (!Role.SUPER_ADMIN.equals(requesterRole)) {
            throw new RuntimeException("无权限");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setStatus(user.getStatus() != null && user.getStatus() == 1 ? 0 : 1);
        userMapper.updateById(user);
        stringRedisTemplate.delete("user_" + id);
    }

    public void updateRole(String requesterRole, Long id, String role) {
        if (!Role.SUPER_ADMIN.equals(requesterRole)) {
            throw new RuntimeException("无权限");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setRole(role);
        userMapper.updateById(user);
        stringRedisTemplate.delete("user_" + id);
    }

    public LoginResponse.UserInfo updateProfile(Long userId, String name, String phone, String email, String emailCode, String avatar) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        boolean emailChanged = email != null && !email.equals(user.getEmail())
                && !(email.isEmpty() && user.getEmail() == null);
        if (emailChanged) {
            if (emailCode == null || emailCode.isEmpty()) {
                throw new RuntimeException("修改邮箱需要验证码");
            }
            if (!emailService.validate(email, emailCode)) {
                throw new RuntimeException("邮箱验证码错误或已过期");
            }
            User existEmailUser = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, email)
            );
            if (existEmailUser != null && !existEmailUser.getId().equals(userId)) {
                throw new RuntimeException("该邮箱已被其他用户使用");
            }
        }

        user.setName(name);
        user.setPhone(phone);
        user.setEmail(email);
        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatar(avatar);
        }
        userMapper.updateById(user);
        return LoginResponse.UserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .phone(user.getPhone())
                .email(user.getEmail())
                .avatar(user.getAvatar())
                .wid(user.getWid())
                .createtime(user.getCreatetime())
                .build();
    }

    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        stringRedisTemplate.delete("user_" + userId);
    }
}
