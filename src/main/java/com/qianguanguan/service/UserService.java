package com.qianguanguan.service;

// --- MyBatis-Plus ---
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
// --- 角色常量 ---
import com.qianguanguan.constant.Role;
// --- 用户实体 ---
import com.qianguanguan.entity.User;
// --- 用户 Mapper ---
import com.qianguanguan.mapper.UserMapper;
// --- 登录响应模型（含 UserInfo） ---
import com.qianguanguan.model.LoginResponse;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
// --- Redis ---
import org.springframework.data.redis.core.StringRedisTemplate;
// --- 密码编码器 ---
import org.springframework.security.crypto.password.PasswordEncoder;
// --- Spring ---
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
    private final UserMapper userMapper;                  // 用户表 Mapper
    private final StringRedisTemplate stringRedisTemplate; // Redis 操作（清除缓存）
    private final PasswordEncoder passwordEncoder;         // BCrypt 密码编码器
    private final EmailService emailService;               // 邮箱验证码服务

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
            throw new RuntimeException("用户不存在");
        }
        // 构建 UserInfo 对象并返回
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
        if (username != null && !username.isEmpty()) {
            wrapper.like(User::getUsername, username); // LIKE '%username%' 模糊匹配
        }
        if (role != null && !role.isEmpty()) {
            wrapper.eq(User::getRole, role);           // = role 精确匹配
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);       // = status
        }
        wrapper.orderByDesc(User::getCreatetime);       // 按创建时间倒序

        // 分页参数，null 时使用默认值
        Page<User> page = new Page<>(
                pageNum != null ? pageNum : 1,
                pageSize != null ? pageSize : 10
        );
        return userMapper.selectPage(page, wrapper);    // 执行分页查询
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
        if (!Role.SUPER_ADMIN.equals(requesterRole)) {
            throw new RuntimeException("无权限");
        }
        // --- 2. 查询目标用户 ---
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        // --- 3. 切换状态：1→0 或 0→1 ---
        user.setStatus(user.getStatus() != null && user.getStatus() == 1 ? 0 : 1);
        userMapper.updateById(user);                // 更新数据库
        // --- 4. 清除 Redis Token 缓存，强制下线 ---
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
        if (!Role.SUPER_ADMIN.equals(requesterRole)) {
            throw new RuntimeException("无权限");
        }
        // 查询并更新
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        user.setRole(role);                      // 设置新角色
        userMapper.updateById(user);             // 更新数据库
        stringRedisTemplate.delete("user_" + id); // 清除 Redis 缓存
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
        // emailChanged = 新邮箱非空 且 不等于旧邮箱 且 不是"都为空"的情况
        boolean emailChanged = email != null
                && !email.equals(user.getEmail())            // 不等于旧邮箱
                && !(email.isEmpty() && user.getEmail() == null); // 排除两者都为空
        if (emailChanged) {
            // --- 2a. 校验邮箱验证码 ---
            if (emailCode == null || emailCode.isEmpty()) {
                throw new RuntimeException("修改邮箱需要验证码");
            }
            if (!emailService.validate(email, emailCode)) {
                throw new RuntimeException("邮箱验证码错误或已过期");
            }
            // --- 2b. 检查新邮箱是否已被其他用户使用 ---
            User existEmailUser = userMapper.selectOne(
                    new LambdaQueryWrapper<User>().eq(User::getEmail, email)
            );
            if (existEmailUser != null && !existEmailUser.getId().equals(userId)) {
                throw new RuntimeException("该邮箱已被其他用户使用");
            }
        }

        // --- 3. 更新字段 ---
        user.setName(name);
        user.setPhone(phone);
        user.setEmail(email);
        if (avatar != null && !avatar.isEmpty()) { // 只有传了头像才更新
            user.setAvatar(avatar);
        }
        userMapper.updateById(user); // 写入数据库

        // --- 4. 返回更新后的用户信息 ---
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
        // BCrypt.matches(明文, 密文)：比较密码是否匹配
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        // --- 3. 加密并更新新密码 ---
        user.setPassword(passwordEncoder.encode(newPassword)); // BCrypt 加密
        userMapper.updateById(user);
        // --- 4. 清除 Token 缓存，强制重新登录 ---
        stringRedisTemplate.delete("user_" + userId);
    }
}
