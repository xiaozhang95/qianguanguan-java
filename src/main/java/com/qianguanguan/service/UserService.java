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

/**
 * 用户业务服务
 * <p>
 * 处理用户信息查询、分页列表、状态切换、角色修改、个人资料更新、密码修改等逻辑。
 * 管理员操作（切换状态、修改角色）需校验操作者是否为 SUPER_ADMIN。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    // --- 依赖注入 ---
    // 用户表 Mapper
    private final UserMapper userMapper;
    // Redis 操作（清除缓存）
    private final StringRedisTemplate stringRedisTemplate;
    // BCrypt 密码编码器
    private final PasswordEncoder passwordEncoder;
    // 邮箱验证码服务
    private final EmailService emailService;

    /**
     * 获取当前用户详细信息
     *
     * @param userId 用户ID
     * @return 封装为 UserInfo 的用户信息
     */
    public LoginResponse.UserInfo getUserInfo(Long userId) {
        // 按主键查询用户
        User user = userMapper.selectById(userId);
        if (user == null) {
            // 查不到用户直接报错
            throw new RuntimeException("用户不存在");
        }
        // 构建 UserInfo 对象并返回
        return LoginResponse.UserInfo.builder()
                // 用户ID
                .id(user.getId())
                // 用户名
                .username(user.getUsername())
                // 姓名
                .name(user.getName())
                // 角色
                .role(user.getRole())
                // 手机号
                .phone(user.getPhone())
                // 邮箱
                .email(user.getEmail())
                // 头像
                .avatar(user.getAvatar())
                // 微信OpenID
                .wid(user.getWid())
                // 创建时间
                .createtime(user.getCreatetime())
                .build();
    }

    /**
     * 分页查询用户列表（管理员功能）
     * <p>
     * 支持用户名模糊搜索、角色精确筛选、状态筛选。
     * 结果按创建时间降序排列。
     *
     * @param username 用户名（模糊匹配，可选）
     * @param role     角色（精确匹配，可选）
     * @param status   状态 1-启用/0-禁用（可选）
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @return 分页用户列表
     */
    public IPage<User> list(String username, String role, Integer status,
                            Integer pageNum, Integer pageSize) {
        // 构建动态查询条件
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        // 传入用户名则模糊搜索
        if (username != null && !username.isEmpty()) {
            // LIKE '%username%' 模糊匹配
            wrapper.like(User::getUsername, username);
        }
        // 传入角色则精确筛选
        if (role != null && !role.isEmpty()) {
            // = role 精确匹配
            wrapper.eq(User::getRole, role);
        }
        // 传入状态则筛选启用/禁用
        if (status != null) {
            // = status
            wrapper.eq(User::getStatus, status);
        }
        // 按创建时间倒序
        wrapper.orderByDesc(User::getCreatetime);

        // 分页参数，null 时使用默认值
        Page<User> page = new Page<>(
                // 页码，默认第1页
                pageNum != null ? pageNum : 1,
                // 每页条数，默认10条
                pageSize != null ? pageSize : 10
        );
        // 执行分页查询
        return userMapper.selectPage(page, wrapper);
    }

    /**
     * 切换用户启用/禁用状态（仅超级管理员）
     * <p>
     * 切换后删除 Redis 中的 Token 缓存，强制该用户重新登录。
     *
     * @param requesterRole 操作者的角色
     * @param id            目标用户ID
     */
    public void toggleStatus(String requesterRole, Long id) {
        // --- 1. 权限校验：只有 SUPER_ADMIN 可以操作 ---
        // 非超级管理员直接拒绝
        if (!Role.SUPER_ADMIN.equals(requesterRole)) {
            throw new RuntimeException("无权限");
        }
        // --- 2. 查询目标用户 ---
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // --- 3. 切换状态：1→0 或 0→1 ---
        // 状态 1↔0 取反
        user.setStatus(user.getStatus() != null && user.getStatus() == 1 ? 0 : 1);
        // 更新数据库
        userMapper.updateById(user);
        // --- 4. 清除 Redis Token 缓存，强制下线 ---
        // 删除缓存，强制该用户重新登录
        stringRedisTemplate.delete("user_" + id);
    }

    /**
     * 修改用户角色（仅超级管理员）
     * <p>
     * 修改后清除 Token 缓存使变更立即生效。
     *
     * @param requesterRole 操作者的角色
     * @param id            目标用户ID
     * @param role          新角色值
     */
    public void updateRole(String requesterRole, Long id, String role) {
        // 权限校验
        // 非超级管理员拒绝操作
        if (!Role.SUPER_ADMIN.equals(requesterRole)) {
            throw new RuntimeException("无权限");
        }
        // 查询并更新
        // 按主键查询目标用户
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // 设置新角色
        user.setRole(role);
        // 更新数据库
        userMapper.updateById(user);
        // 清除 Redis 缓存，让新角色立即生效
        stringRedisTemplate.delete("user_" + id);
    }

    /**
     * 更新个人信息
     * <p>
     * 修改邮箱时需要邮箱验证码，并校验新邮箱未被其他用户使用。
     *
     * @param userId    当前用户ID
     * @param name      新姓名/昵称
     * @param phone     新手机号
     * @param email     新邮箱
     * @param emailCode 邮箱验证码（修改邮箱时必需）
     * @param avatar    新头像路径
     * @return 更新后的 UserInfo
     */
    public LoginResponse.UserInfo updateProfile(Long userId, String name, String phone,
                                                 String email, String emailCode, String avatar) {
        // --- 1. 查询当前用户 ---
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // --- 2. 判断邮箱是否发生变化 ---
        // emailChanged：新邮箱非空
        boolean emailChanged = email != null
                // 不等于旧邮箱
                && !email.equals(user.getEmail())
                // 排除两者都为空
                && !(email.isEmpty() && user.getEmail() == null);
        // 邮箱有变化才进入验证流程
        if (emailChanged) {
            // --- 2a. 校验邮箱验证码 ---
            // 修改邮箱必须携带验证码
            if (emailCode == null || emailCode.isEmpty()) {
                throw new RuntimeException("修改邮箱需要验证码");
            }
            // 验证码校验失败则报错
            if (!emailService.validate(email, emailCode)) {
                throw new RuntimeException("邮箱验证码错误或已过期");
            }
            // --- 2b. 检查新邮箱是否已被其他用户使用 ---
            // 按新邮箱查询已有用户
            User existEmailUser = userMapper.selectOne(
                    // WHERE email = ?
                    new LambdaQueryWrapper<User>().eq(User::getEmail, email)
            );
            // 邮箱已被他人占用
            if (existEmailUser != null && !existEmailUser.getId().equals(userId)) {
                throw new RuntimeException("该邮箱已被其他用户使用");
            }
        }

        // --- 3. 更新字段 ---
        // 更新姓名
        user.setName(name);
        // 更新手机号
        user.setPhone(phone);
        // 更新邮箱
        user.setEmail(email);
        // 只有传了头像才更新
        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatar(avatar);
        }
        // 写入数据库
        userMapper.updateById(user);

        // --- 4. 返回更新后的用户信息 ---
        return LoginResponse.UserInfo.builder()
                // 用户ID
                .id(user.getId())
                // 用户名
                .username(user.getUsername())
                // 姓名
                .name(user.getName())
                // 角色
                .role(user.getRole())
                // 手机号
                .phone(user.getPhone())
                // 邮箱
                .email(user.getEmail())
                // 头像
                .avatar(user.getAvatar())
                // 微信OpenID
                .wid(user.getWid())
                // 创建时间
                .createtime(user.getCreatetime())
                .build();
    }

    /**
     * 修改密码（需验证原密码）
     * <p>
     * 修改后清除 Redis Token 缓存，强制用户用新密码重新登录。
     *
     * @param userId      当前用户ID
     * @param oldPassword 原密码（明文）
     * @param newPassword 新密码（明文）
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        // --- 1. 查询用户 ---
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // --- 2. 验证原密码 ---
        // BCrypt 比对明文/密文
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        // --- 3. 加密并更新新密码 ---
        // BCrypt 加密
        user.setPassword(passwordEncoder.encode(newPassword));
        // 写入数据库
        userMapper.updateById(user);
        // --- 4. 清除 Token 缓存，强制重新登录 ---
        // 删除缓存，强制用新密码重新登录
        stringRedisTemplate.delete("user_" + userId);
    }
}
