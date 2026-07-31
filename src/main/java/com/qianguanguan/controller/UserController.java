package com.qianguanguan.controller;

// --- MyBatis-Plus 分页对象 ---
import com.baomidou.mybatisplus.core.metadata.IPage;
// --- JWT Claims 常量（id、role 字段名） ---
import com.qianguanguan.constant.JwtClaims;
// --- 用户实体 ---
import com.qianguanguan.entity.User;
// --- 登录响应模型（含 UserInfo 内部类） ---
import com.qianguanguan.model.LoginResponse;
// --- 请求模型 ---
import com.qianguanguan.model.UserListRequest;
import com.qianguanguan.model.ProfileUpdateRequest;
import com.qianguanguan.model.ChangePasswordRequest;
// --- Service 层 ---
import com.qianguanguan.service.LoginService;
import com.qianguanguan.service.UserService;
// --- Swagger 注解 ---
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
// --- Jakarta Servlet ---
import jakarta.servlet.http.HttpServletRequest;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
// --- Spring MVC ---
import org.springframework.web.bind.annotation.*;

/**
 * 用户模块控制器
 * <p>
 * 提供用户信息查询、微信登录、用户管理（管理员功能）、个人资料修改、密码修改等接口。
 */
@Tag(name = "用户模块", description = "用户信息、用户管理相关接口") // Swagger 分组标签
@RestController                 // REST 控制器
@RequestMapping("/api/business") // 统一基础路径
@RequiredArgsConstructor        // 自动注入 final 字段
public class UserController {

    // --- 通过构造器注入 Service ---
    private final UserService userService;   // 用户业务服务
    private final LoginService loginService; // 登录业务服务（含微信登录）

    // ======================== 当前用户信息 ========================

    @Operation(summary = "获取当前用户信息", description = "根据JWT token获取当前登录用户的详细信息")
    @GetMapping("/user/info")  // GET /api/business/user/info
    public LoginResponse.UserInfo userInfo(HttpServletRequest request) {
        // 从 Request 属性中取出 JWT 中存储的用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 UserService 查询用户详情并返回
        return userService.getUserInfo(userId);
    }

    // ======================== 微信登录 ========================

    @Operation(summary = "微信登录", description = "通过微信小程序code进行登录或注册，返回JWT token和用户信息")
    @GetMapping("/user/LoginByWid")  // GET /api/business/user/LoginByWid?code=xxx
    public LoginResponse loginByWid(@RequestParam String code) {
        // 将小程序 wx.login() 获取的临时 code 传给 LoginService，
        // 内部会调微信接口换取 OpenID，新用户自动注册
        return loginService.loginByWid(code);
    }

    // ======================== 用户管理（管理员功能） ========================

    @Operation(summary = "用户列表", description = "分页查询用户，支持用户名、角色、状态筛选")
    @PostMapping("/user/list")  // POST /api/business/user/list
    public IPage<User> userList(@RequestBody UserListRequest body) {
        // 委托 UserService 分页查询，传入筛选条件
        return userService.list(
                body.getUsername(), // 用户名（模糊匹配）
                body.getRole(),     // 角色筛选
                body.getStatus(),   // 状态筛选（1-启用 / 0-禁用）
                body.getPageNum(),  // 页码
                body.getPageSize()  // 每页条数
        );
    }

    @Operation(summary = "切换用户状态", description = "仅超级管理员可启用/禁用用户")
    @PutMapping("/user/toggle/{id}")  // PUT /api/business/user/toggle/{id}
    public void userToggle(
            HttpServletRequest request, // 获取当前操作者信息
            @PathVariable Long id       // 要切换状态的目标用户 ID
    ) {
        // 取出当前操作者的角色，用于权限校验
        String role = (String) request.getAttribute(JwtClaims.ROLE);
        // 委托 UserService 切换状态（内部会校验是否为 SUPER_ADMIN）
        userService.toggleStatus(role, id);
    }

    @Operation(summary = "修改用户角色", description = "仅超级管理员可修改用户角色类型")
    @PutMapping("/user/role/update/{id}")  // PUT /api/business/user/role/update/{id}
    public void userUpdateRole(
            HttpServletRequest request,  // 获取当前操作者信息
            @PathVariable Long id,       // 目标用户 ID
            @RequestParam String role    // 新角色值（URL 参数）
    ) {
        // 取出当前操作者的角色
        String requesterRole = (String) request.getAttribute(JwtClaims.ROLE);
        // 委托 UserService 修改角色
        userService.updateRole(requesterRole, id, role);
    }

    // ======================== 个人资料修改 ========================

    @Operation(summary = "修改个人信息", description = "当前用户修改自己的姓名、手机号、邮箱")
    @PutMapping("/user/profile")  // PUT /api/business/user/profile
    public LoginResponse.UserInfo updateProfile(
            HttpServletRequest request,         // 获取当前用户 ID
            @RequestBody ProfileUpdateRequest body // 请求体：姓名、手机、邮箱、验证码、头像
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 UserService 更新个人资料（修改邮箱需验证码）
        return userService.updateProfile(
                userId,
                body.getName(),     // 姓名/昵称
                body.getPhone(),    // 手机号
                body.getEmail(),    // 邮箱（变更需验证码）
                body.getEmailCode(),// 邮箱验证码
                body.getAvatar()    // 头像路径
        );
    }

    @Operation(summary = "修改密码", description = "当前用户修改自己的登录密码")
    @PutMapping("/user/password")  // PUT /api/business/user/password
    public void changePassword(
            HttpServletRequest request,         // 获取当前用户 ID
            @RequestBody ChangePasswordRequest body // 请求体：旧密码、新密码
    ) {
        // 取出当前登录用户 ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托 UserService 修改密码（需验证原密码正确）
        userService.changePassword(userId, body.getOldPassword(), body.getNewPassword());
    }
}
