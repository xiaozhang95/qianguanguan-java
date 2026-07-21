package com.qianguanguan.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.qianguanguan.constant.JwtClaimKey;
import com.qianguanguan.entity.User;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.model.UserListRequest;
import com.qianguanguan.model.ProfileUpdateRequest;
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

    @Operation(summary = "获取当前用户信息", description = "根据JWT token获取当前登录用户的详细信息")
    @GetMapping("/user/info")
    public LoginResponse.UserInfo userInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
        return userService.getUserInfo(userId);
    }

    @Operation(summary = "用户列表", description = "分页查询用户，支持用户名、角色、状态筛选")
    @PostMapping("/user/list")
    public IPage<User> userList(@RequestBody UserListRequest body) {
        return userService.list(body.getUsername(), body.getRole(), body.getStatus(),
                body.getPageNum(), body.getPageSize());
    }

    @Operation(summary = "切换用户状态", description = "启用/禁用用户")
    @PutMapping("/user/toggle/{id}")
    public void userToggle(@PathVariable Long id) {
        userService.toggleStatus(id);
    }

    @Operation(summary = "修改个人信息", description = "当前用户修改自己的姓名、手机号、邮箱")
    @PutMapping("/user/profile")
    public LoginResponse.UserInfo updateProfile(HttpServletRequest request, @RequestBody ProfileUpdateRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
        return userService.updateProfile(userId, body.getName(), body.getPhone(), body.getEmail(), body.getAvatar());
    }
}
