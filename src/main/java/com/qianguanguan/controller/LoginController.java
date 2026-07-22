package com.qianguanguan.controller;

import com.qianguanguan.model.CaptchaResponse;
import com.qianguanguan.model.LoginRequest;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.model.RegisterRequest;
import com.qianguanguan.service.CaptchaService;
import com.qianguanguan.service.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "登录模块", description = "用户登录、验证码相关接口")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class LoginController {

    private final CaptchaService captchaService;
    private final LoginService loginService;

    @Operation(summary = "获取验证码", description = "生成图形验证码，返回验证码key和base64图片")
    @GetMapping("/captcha")
    public CaptchaResponse captcha() {
        return captchaService.generate();
    }

    @Operation(summary = "用户登录", description = "使用用户名、密码和验证码进行登录，返回JWT token和用户信息")
    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return loginService.login(request);
    }

    @Operation(summary = "用户注册", description = "注册新用户，需要用户名、密码和验证码")
    @PostMapping("/register")
    public void register(@RequestBody RegisterRequest request) {
        loginService.register(request);
    }
}
