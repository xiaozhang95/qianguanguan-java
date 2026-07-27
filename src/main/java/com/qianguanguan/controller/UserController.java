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

@Tag(name = "用户模块", description = "用户信息、用户管理相关接口")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final LoginService loginService;

    @Operation(summary = "获取当前用户信息", description = "根据JWT token获取当前登录用户的详细信息")
    @GetMapping("/user/info")
    public LoginResponse.UserInfo userInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return userService.getUserInfo(userId);
    }

    @Operation(summary = "微信登录", description = "通过微信小程序code进行登录或注册，返回JWT token和用户信息")
    @GetMapping("/user/LoginByWid")
    public LoginResponse loginByWid(@RequestParam String code) {
        return loginService.loginByWid(code);
    }

    @Operation(summary = "用户列表", description = "分页查询用户，支持用户名、角色、状态筛选")
    @PostMapping("/user/list")
    public IPage<User> userList(@RequestBody UserListRequest body) {
        return userService.list(body.getUsername(), body.getRole(), body.getStatus(),
                body.getPageNum(), body.getPageSize());
    }

    @Operation(summary = "切换用户状态", description = "仅超级管理员可启用/禁用用户")
    @PutMapping("/user/toggle/{id}")
    public void userToggle(HttpServletRequest request, @PathVariable Long id) {
        String role = (String) request.getAttribute(JwtClaims.ROLE);
        userService.toggleStatus(role, id);
    }

    @Operation(summary = "修改用户角色", description = "仅超级管理员可修改用户角色类型")
    @PutMapping("/user/role/update/{id}")
    public void userUpdateRole(HttpServletRequest request, @PathVariable Long id, @RequestParam String role) {
        String requesterRole = (String) request.getAttribute(JwtClaims.ROLE);
        userService.updateRole(requesterRole, id, role);
    }

    @Operation(summary = "修改个人信息", description = "当前用户修改自己的姓名、手机号、邮箱")
    @PutMapping("/user/profile")
    public LoginResponse.UserInfo updateProfile(HttpServletRequest request, @RequestBody ProfileUpdateRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return userService.updateProfile(userId, body.getName(), body.getPhone(), body.getEmail(), body.getEmailCode(), body.getAvatar());
    }

    @Operation(summary = "修改密码", description = "当前用户修改自己的登录密码")
    @PutMapping("/user/password")
    public void changePassword(HttpServletRequest request, @RequestBody ChangePasswordRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        userService.changePassword(userId, body.getOldPassword(), body.getNewPassword());
    }
}
