package com.qianguanguan.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qianguanguan.constant.JwtClaims;
import com.qianguanguan.entity.User;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.model.UserListRequest;
import com.qianguanguan.model.ProfileUpdateRequest;
import com.qianguanguan.model.ChangePasswordRequest;
import com.qianguanguan.service.LoginService;
import com.qianguanguan.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户模块控制器
 * <p>
 * 提供用户信息查询、微信登录、用户管理（管理员功能）、个人资料修改、密码修改等接口。
 */
// Swagger 分组标签
@Tag(name = "用户模块", description = "用户信息、用户管理相关接口")
// REST 控制器
@RestController
// 统一基础路径
@RequestMapping("/api/business")
// 自动注入 final 字段
@RequiredArgsConstructor
public class UserController {

    // --- 通过构造器注入 Service ---
    // 用户业务服务
    private final UserService userService;
    // 登录业务服务（含微信登录）
    private final LoginService loginService;

    // ======================== 当前用户信息 ========================

    @Operation(summary = "获取当前用户信息", description = "根据JWT token获取当前登录用户的详细信息")
    // GET /api/business/user/info
    @GetMapping("/user/info")
    public LoginResponse.UserInfo userInfo(HttpServletRequest request) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service查询用户详情并返回
        return userService.getUserInfo(userId);
    }

    // ======================== 微信登录 ========================

    @Operation(summary = "微信登录", description = "通过微信小程序code进行登录或注册，返回JWT token和用户信息")
    // GET /api/business/user/LoginByWid?code=xxx
    @GetMapping("/user/LoginByWid")
    public LoginResponse loginByWid(@RequestParam String code) {
        // 用临时code换OpenID，新用户自动注册，返回JWT与用户信息
        return loginService.loginByWid(code);
    }

    // ======================== 用户管理（管理员功能） ========================

    @Operation(summary = "用户列表", description = "分页查询用户，支持用户名、角色、状态筛选")
    // POST /api/business/user/list
    @PostMapping("/user/list")
    public IPage<User> userList(@RequestBody UserListRequest body) {
        // 委托Service分页查询用户，传入筛选条件
        return userService.list(
                // 用户名（模糊匹配）
                body.getUsername(),
                // 角色筛选
                body.getRole(),
                // 状态筛选（1-启用 / 0-禁用）
                body.getStatus(),
                // 页码
                body.getPageNum(),
                // 每页条数
                body.getPageSize()
        );
    }

    @Operation(summary = "切换用户状态", description = "仅超级管理员可启用/禁用用户")
    // PUT /api/business/user/toggle/{id}
    @PutMapping("/user/toggle/{id}")
    public void userToggle(
            // 获取当前操作者信息
            HttpServletRequest request,
            // 要切换状态的目标用户 ID
            @PathVariable Long id
    ) {
        // 取出JWT属性中的当前操作者角色，用于权限校验
        String role = (String) request.getAttribute(JwtClaims.ROLE);
        // 委托Service切换状态（内部校验仅SUPER_ADMIN可操作）
        userService.toggleStatus(role, id);
    }

    @Operation(summary = "修改用户角色", description = "仅超级管理员可修改用户角色类型")
    // PUT /api/business/user/role/update/{id}
    @PutMapping("/user/role/update/{id}")
    public void userUpdateRole(
            // 获取当前操作者信息
            HttpServletRequest request,
            // 目标用户 ID
            @PathVariable Long id,
            // 新角色值（URL 参数）
            @RequestParam String role
    ) {
        // 取出JWT属性中的操作者角色
        String requesterRole = (String) request.getAttribute(JwtClaims.ROLE);
        // 委托Service校验权限后修改目标用户角色
        userService.updateRole(requesterRole, id, role);
    }

    // ======================== 个人资料修改 ========================

    @Operation(summary = "修改个人信息", description = "当前用户修改自己的姓名、手机号、邮箱")
    // PUT /api/business/user/profile
    @PutMapping("/user/profile")
    public LoginResponse.UserInfo updateProfile(
            // 获取当前用户 ID
            HttpServletRequest request,
            // 请求体：姓名、手机、邮箱、验证码、头像
            @RequestBody ProfileUpdateRequest body
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 委托Service更新个人资料（修改邮箱需验证码）
        return userService.updateProfile(
                // 当前用户ID
                userId,
                // 姓名/昵称
                body.getName(),
                // 手机号
                body.getPhone(),
                // 邮箱（变更需验证码）
                body.getEmail(),
                // 邮箱验证码
                body.getEmailCode(),
                // 头像路径
                body.getAvatar()
        );
    }

    @Operation(summary = "修改密码", description = "当前用户修改自己的登录密码")
    // PUT /api/business/user/password
    @PutMapping("/user/password")
    public void changePassword(
            // 获取当前用户 ID
            HttpServletRequest request,
            // 请求体：旧密码、新密码
            @RequestBody ChangePasswordRequest body
    ) {
        // 从JWT过滤器写入的Request属性中取出用户ID
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        // 校验原密码正确后修改为新密码
        userService.changePassword(userId, body.getOldPassword(), body.getNewPassword());
    }
}
