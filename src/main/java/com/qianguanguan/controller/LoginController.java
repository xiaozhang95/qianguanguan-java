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

/**
 * 登录模块控制器
 * <p>
 * 提供用户登录、注册、图形验证码获取、邮箱验证码发送、Token 刷新等接口。
 * 所有登录/注册相关接口均在白名单中（无需 JWT 认证即可访问）。
 */
// Swagger 分组标签
@Tag(name = "登录模块", description = "用户登录、验证码相关接口")
// 标识这是一个 REST 控制器
@RestController
// 统一的基础请求路径
@RequestMapping("/api/business")
// Lombok：自动生成构造器，注入所有 final 字段
@RequiredArgsConstructor
public class LoginController {

    // --- 通过构造器注入 Service（@RequiredArgsConstructor 自动处理） ---
    // 图形验证码服务
    private final CaptchaService captchaService;
    // 登录/注册业务服务
    private final LoginService loginService;
    // 邮箱验证码服务
    private final EmailService emailService;

    // ======================== 验证码 ========================

    @Operation(summary = "获取验证码", description = "生成图形验证码，返回验证码key和base64图片")
    // GET /api/business/captcha
    @GetMapping("/captcha")
    public CaptchaResponse captcha() {
        // 委托Service生成验证码，返回 key 与 base64 图片
        return captchaService.generate();
    }

    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送6位验证码，60秒内不可重复发送")
    // POST /api/business/send-email-code
    @PostMapping("/send-email-code")
    // 请求体：目标邮箱地址
    public void sendEmailCode(@RequestBody SendEmailCodeRequest request) {
        // 取出邮箱地址，委托Service发送6位验证码
        emailService.sendVerificationCode(request.getEmail());
    }

    // ======================== 登录 / 注册 ========================

    @Operation(summary = "用户登录", description = "使用用户名、密码和验证码进行登录，返回JWT token和用户信息")
    // POST /api/business/login
    @PostMapping("/login")
    // 请求体：账号密码与图形验证码
    public LoginResponse login(@RequestBody LoginRequest request) {
        // 委托Service校验（验证码+邮箱+密码），返回 token 与用户信息
        return loginService.login(request);
    }

    @Operation(summary = "用户注册", description = "注册新用户，需要用户名、密码、邮箱、邮箱验证码和图形验证码")
    // POST /api/business/register
    @PostMapping("/register")
    // 请求体：注册信息与两类验证码
    public LoginResponse register(@RequestBody RegisterRequest request) {
        // 委托Service注册新用户（含邮箱与图形验证码双重校验）
        return loginService.register(request);
    }

    // ======================== Token 管理 ========================

    @Operation(summary = "刷新token", description = "使用当前token换取新token")
    // POST /api/business/token/refresh
    @PostMapping("/token/refresh")
    // 从请求头 Authorization 中读取旧 token
    public LoginResponse refreshToken(HttpServletRequest request) {
        // 获取 Authorization 请求头值
        String authorization = request.getHeader("Authorization");
        // 校验必须以 "Bearer " 开头，否则视为无效请求
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            // 抛出异常，中断刷新流程
            throw new RuntimeException("缺少token");
        }
        // 去掉 "Bearer " 前缀（7个字符）后委托Service刷新，允许已过期token
        return loginService.refreshToken(authorization.substring(7));
    }
}
