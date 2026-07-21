package com.qianguanguan.controller;

import com.qianguanguan.constant.JwtClaimKey;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.service.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户模块", description = "用户信息相关接口")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class UserController {

    private final LoginService loginService;

    @Operation(summary = "获取当前用户信息", description = "根据JWT token获取当前登录用户的详细信息")
    @GetMapping("/user/info")
    public LoginResponse.UserInfo userInfo(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
        return loginService.getUserInfo(userId);
    }
}
