package com.qianguanguan.controller;

// --- 导入响应/请求模型 ---
import com.qianguanguan.model.CaptchaResponse;
import com.qianguanguan.model.LoginRequest;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.model.RegisterRequest;
import com.qianguanguan.model.SendEmailCodeRequest;
// --- 导入 Service 层 ---
import com.qianguanguan.service.CaptchaService;
import com.qianguanguan.service.EmailService;
import com.qianguanguan.service.LoginService;
// --- Swagger 注解 ---
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
// --- Jakarta Servlet：获取请求头 ---
import jakarta.servlet.http.HttpServletRequest;
// --- Lombok：自动生成含 final 字段的构造器 ---
import lombok.RequiredArgsConstructor;
// --- Spring MVC 注解 ---
import org.springframework.web.bind.annotation.*;

/**
 * 登录模块控制器
 * <p>
 * 提供用户登录、注册、图形验证码获取、邮箱验证码发送、Token 刷新等接口。
 * 所有登录/注册相关接口均在白名单中（无需 JWT 认证即可访问）。
 */
@Tag(name = "登录模块", description = "用户登录、验证码相关接口") // Swagger 分组标签
@RestController                 // 标识这是一个 REST 控制器
@RequestMapping("/api/business") // 统一的基础请求路径
@RequiredArgsConstructor        // Lombok：自动生成构造器，注入所有 final 字段
public class LoginController {

    // --- 通过构造器注入 Service（@RequiredArgsConstructor 自动处理） ---
    private final CaptchaService captchaService; // 图形验证码服务
    private final LoginService loginService;     // 登录/注册业务服务
    private final EmailService emailService;     // 邮箱验证码服务

    // ======================== 验证码 ========================

    @Operation(summary = "获取验证码", description = "生成图形验证码，返回验证码key和base64图片")
    @GetMapping("/captcha")  // GET /api/business/captcha
    public CaptchaResponse captcha() {
        // 委托 CaptchaService 生成验证码，返回 key + base64 图片
        return captchaService.generate();
    }

    @Operation(summary = "发送邮箱验证码", description = "向指定邮箱发送6位验证码，60秒内不可重复发送")
    @PostMapping("/send-email-code")  // POST /api/business/send-email-code
    public void sendEmailCode(@RequestBody SendEmailCodeRequest request) {
        // 取出请求中的邮箱地址，委托 EmailService 发送验证码
        emailService.sendVerificationCode(request.getEmail());
    }

    // ======================== 登录 / 注册 ========================

    @Operation(summary = "用户登录", description = "使用用户名、密码和验证码进行登录，返回JWT token和用户信息")
    @PostMapping("/login")  // POST /api/business/login
    public LoginResponse login(@RequestBody LoginRequest request) {
        // 委托 LoginService 进行登录校验（验证码 + 邮箱 + 密码）
        return loginService.login(request);
    }

    @Operation(summary = "用户注册", description = "注册新用户，需要用户名、密码、邮箱、邮箱验证码和图形验证码")
    @PostMapping("/register")  // POST /api/business/register
    public LoginResponse register(@RequestBody RegisterRequest request) {
        // 委托 LoginService 进行注册（含双重验证码校验）
        return loginService.register(request);
    }

    // ======================== Token 管理 ========================

    @Operation(summary = "刷新token", description = "使用当前token换取新token")
    @PostMapping("/token/refresh")  // POST /api/business/token/refresh
    public LoginResponse refreshToken(HttpServletRequest request) {
        // 从请求头中获取 Authorization 字段
        String authorization = request.getHeader("Authorization");
        // 校验请求头格式：必须以 "Bearer " 开头
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new RuntimeException("缺少token");
        }
        // 去掉 "Bearer " 前缀（7个字符），得到纯 Token 字符串
        // 委托 LoginService 刷新 Token（允许已过期的 Token）
        return loginService.refreshToken(authorization.substring(7));
    }
}
