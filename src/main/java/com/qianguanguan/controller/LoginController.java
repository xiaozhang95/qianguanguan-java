package com.qianguanguan.controller;

import com.qianguanguan.model.CaptchaResponse;
import com.qianguanguan.model.LoginRequest;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.model.RegisterRequest;
import com.qianguanguan.model.SendEmailCodeRequest;
import com.qianguanguan.service.CaptchaService;
import com.qianguanguan.service.EmailService;
import com.qianguanguan.service.LoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "登录模块", description = "用户登录、验证码相关接口")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class LoginController {

    private final CaptchaService captchaService;
    private final LoginService loginService;
    private final EmailService emailService;

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

    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送6位验证码，60秒内不可重复发送")
    @PostMapping("/send-email-code")
    public void sendEmailCode(@RequestBody SendEmailCodeRequest request) {
        emailService.sendVerificationCode(request.getEmail());
    }

    @Operation(summary = "用户注册", description = "注册新用户，需要用户名、密码、邮箱、邮箱验证码和图形验证码")
    @PostMapping("/register")
    public LoginResponse register(@RequestBody RegisterRequest request) {
        return loginService.register(request);
    }

    @Operation(summary = "刷新token", description = "使用当前token换取新token")
    @PostMapping("/token/refresh")
    public LoginResponse refreshToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("缺少token");
        }
        return loginService.refreshToken(authorization.substring(7));
    }
}
