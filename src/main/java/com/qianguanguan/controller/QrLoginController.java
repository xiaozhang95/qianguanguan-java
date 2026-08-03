package com.qianguanguan.controller;

import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.service.QrLoginService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 微信扫码登录接口
 * <p>
 * 实现"Web 页面展示场景码 → 小程序扫码确认 → Web 自动登录"的跨端登录流程：
 * <ol>
 *   <li>Web 页面调用 generateScene 获取场景ID和小程序码</li>
 *   <li>用户用微信扫描小程序码，小程序自动打开确认页</li>
 *   <li>小程序调用 confirmLogin 确认登录</li>
 *   <li>Web 页面每 2 秒轮询 checkStatus，直到获取 token 完成登录</li>
 * </ol>
 */
@Tag(name = "二维码登录模块", description = "微信扫码登录相关接口")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class QrLoginController {

    private final QrLoginService qrLoginService;

    /**
     * 生成登录场景码
     * <p>
     * Web 页面调用此接口，获取一个 16 位场景ID 和对应的小程序码（base64 图片）。
     * 场景数据存入 Redis，有效期 5 分钟。
     */
    @Operation(summary = "生成登录场景码", description = "生成扫码登录的场景ID")
    @GetMapping("/qr-login/scene")
    public Map<String, String> generateScene() {
        // 委托Service生成场景码，返回 {sceneId, qrImage}
        return qrLoginService.generateScene();
    }

    /**
     * 查询场景码状态
     * <p>
     * Web 页面每 2 秒轮询一次此接口，检测小程序是否已扫描确认。
     * 状态值：pending（等待扫码）、confirmed（已确认，返回token）、expired（已过期）
     */
    @Operation(summary = "查询场景码状态", description = "轮询场景码是否被确认")
    @GetMapping("/qr-login/status")
    public Map<String, Object> checkStatus(@RequestParam String sceneId) {
        // 委托Service查询场景状态，供Web端轮询
        return qrLoginService.checkStatus(sceneId);
    }

    /**
     * 确认登录
     * <p>
     * 小程序端调用此接口。传入场景ID + wx.login() 获取的临时 code，
     * 服务端通过微信接口换取 OpenID，完成用户绑定（新用户自动注册），
     * 签发 JWT Token 并写入 Redis 供 Web 端轮询获取。
     */
    @Operation(summary = "确认登录", description = "小程序确认登录")
    @PostMapping("/qr-login/confirm")
    public LoginResponse confirmLogin(@RequestBody Map<String, String> body) {
        // 从请求体中取出场景码 ID
        String sceneId = body.get("sceneId");
        // 从请求体中取出微信临时登录凭证 code
        String code = body.get("code");
        // 委托Service换OpenID签发JWT，写入Redis供Web轮询获取
        return qrLoginService.confirmLogin(sceneId, code);
    }
}
